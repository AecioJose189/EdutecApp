package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.Aluno
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.EstatisticasTurma
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.AlunosDoConceito
import com.edutec.app.data.AlunoNoConceito
import com.edutec.app.data.HistoricoConceito
import com.edutec.app.data.MapaAluno
import com.edutec.app.data.Convite
import com.edutec.app.data.ErroTurmaApi
import com.edutec.app.data.Membro
import com.edutec.app.data.SessaoExpirada
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.ResultadoFusao
import com.edutec.app.data.TurmaRepositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Aviso no topo da turma (depois de publicar ou de encerrar a sessão), com uma ação opcional. */
data class AvisoTurma(val texto: String, val acao: String? = null, val atividade: AtividadePublicada? = null)

/** Grafo da turma aberta, vindo da turma-api. */
sealed interface EstadoGrafo {
    data object Carregando : EstadoGrafo
    data class Erro(val mensagem: String) : EstadoGrafo
    data class Pronto(val grafo: GrafoTurma) : EstadoGrafo
}

/**
 * Dados da tela Turma (escopo da janela): grafo, aba aberta, atividades publicadas, membros e
 * convite. A aba fica aqui para a volta da projeção cair na mesma aba.
 */
class TurmaViewModel(
    private val buscarGrafo: suspend (String) -> GrafoTurma = TurmaRepositorio::grafo,
    private val simularRespostas: suspend (String) -> Unit = TurmaRepositorio::simular,
    private val buscarAtividades: suspend (String) -> List<AtividadePublicada> = TurmaRepositorio::atividades,
    private val buscarMembros: suspend (String) -> List<Membro> = TurmaRepositorio::membros,
    private val buscarConvite: suspend (String, Boolean) -> Convite = TurmaRepositorio::convite,
    private val fundirConceitos: suspend (String, String, String) -> ResultadoFusao = TurmaRepositorio::fundirConceitos,
    private val marcarDistintos: suspend (String, String, String) -> Unit = TurmaRepositorio::marcarDistintos,
    private val buscarEstatisticas: suspend (String, Boolean) -> EstatisticasTurma = TurmaRepositorio::estatisticas,
    private val buscarMapaAluno: suspend (String, String) -> MapaAluno = TurmaRepositorio::mapaAluno,
    private val buscarHistorico: suspend (String, String, String) -> HistoricoConceito = TurmaRepositorio::historico,
    private val buscarAlunosConceito: suspend (String, String) -> AlunosDoConceito = TurmaRepositorio::alunosConceito,
) : ViewModel() {
    /** Fase 5: "Ver alunos com dificuldade" de um conceito; null = lista fechada. */
    var alunosDoConceito by mutableStateOf<Carga<AlunosDoConceito>?>(null)
        private set
    var alunosDe by mutableStateOf<String?>(null)
        private set

    /** Aba Estatísticas (UC13). */
    var estatisticas by mutableStateOf<Carga<EstatisticasTurma>>(Carga.Carregando)
        private set

    /** Padrão: só alunos reais. */
    var incluirSimulacao by mutableStateOf(false)
        private set

    /** Aluno cujo mapa está pintado (UC11); null = turma toda. */
    var alunoSelecionado by mutableStateOf<Aluno?>(null)
        private set

    var mapaDoAluno by mutableStateOf<Carga<MapaAluno>>(Carga.Carregando)
        private set

    /** Histórico do aluno selecionado no conceito [historicoDe] (UC12). */
    var historico by mutableStateOf<Carga<HistoricoConceito>?>(null)
        private set
    var historicoDe by mutableStateOf<String?>(null)
        private set

    /** Conceito a destacar no mapa (vindo da tabela das Estatísticas); a tela consome e zera. */
    var conceitoEmFoco by mutableStateOf<String?>(null)

    var aba by mutableStateOf(ABA_GRAFO)

    var atividades by mutableStateOf<Carga<List<AtividadePublicada>>>(Carga.Carregando)
        private set

    var membros by mutableStateOf<Carga<List<Membro>>>(Carga.Carregando)
        private set

    /** null = diálogo de convite fechado. */
    var convite by mutableStateOf<Carga<Convite>?>(null)
        private set

    /** Juntar conceitos (ou marcar como distintos) em andamento. */
    var juntando by mutableStateOf(false)
        private set

    /** Aviso no topo da turma; some ao fechar, ao trocar de turma ou com um novo aviso. */
    var aviso by mutableStateOf<AvisoTurma?>(null)

    /**
     * Abrir sessão numa turma sem alunos: a atividade espera no aviso (convidar, abrir mesmo assim ou
     * cancelar). Não bloqueia: com entrar_na_turma, o aluno entra na turma pelo próprio QR da sessão.
     */
    var sessaoSemAlunos by mutableStateOf<AtividadePublicada?>(null)

    /** Conferindo os membros antes de abrir a sessão. */
    var conferindoAlunos by mutableStateOf(false)
        private set

    /** Simulação de demonstração em andamento. */
    var simulando by mutableStateOf(false)
        private set

    private val _grafo = MutableStateFlow<EstadoGrafo>(EstadoGrafo.Carregando)
    val grafo: StateFlow<EstadoGrafo> = _grafo.asStateFlow()

    /** Turma cujo grafo está em [grafo] (estado observável pelo Compose). */
    var turmaId by mutableStateOf<String?>(null)
        private set

    private var carregamento: Job? = null

    /** Carrega o grafo da turma, se ainda não for o dela (ou se [forcar]). */
    fun abrir(turmaId: String, forcar: Boolean = false) {
        if (!forcar && turmaId == this.turmaId && _grafo.value !is EstadoGrafo.Erro) return
        val outraTurma = turmaId != this.turmaId
        if (outraTurma) {
            aba = ABA_GRAFO
            atividades = Carga.Carregando
            membros = Carga.Carregando
            convite = null
            aviso = null
            sessaoSemAlunos = null
            estatisticas = Carga.Carregando
            incluirSimulacao = false
            alunoSelecionado = null
            historico = null
            historicoDe = null
            conceitoEmFoco = null
            alunosDoConceito = null
            alunosDe = null
        }
        this.turmaId = turmaId
        // O painel "Próximos passos" precisa dos alunos e das atividades em qualquer aba.
        carregarAba(ABA_ATIVIDADES)
        carregarAba(ABA_TURMA)
        // Recarregar (simulação, fusão, volta da projeção) atualiza também o que estiver na tela.
        if (aba == ABA_ESTATISTICAS) carregarEstatisticas()
        alunoSelecionado?.let { selecionarAluno(it) }
        carregamento?.cancel()
        // Recarregar a mesma turma mantém o grafo atual na tela até chegar o novo.
        if (outraTurma || _grafo.value !is EstadoGrafo.Pronto) _grafo.value = EstadoGrafo.Carregando
        carregamento = viewModelScope.launch {
            _grafo.value = try {
                EstadoGrafo.Pronto(buscarGrafo(turmaId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoGrafo.Erro((e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Não foi possível carregar o mapa do conhecimento da turma.")
            }
        }
    }

    /**
     * Demonstração: simula 25 alunos respondendo e recarrega o grafo. [aoTerminar] recebe null em caso
     * de sucesso ou a mensagem de erro.
     */
    fun simular(turmaId: String, aoTerminar: (erro: String?) -> Unit) {
        if (simulando) return
        simulando = true
        viewModelScope.launch {
            try {
                simularRespostas(turmaId)
                if (this@TurmaViewModel.turmaId == turmaId) {
                    _grafo.value = EstadoGrafo.Pronto(buscarGrafo(turmaId))
                }
                aoTerminar(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aoTerminar((e as? ErroTurmaApi)?.message ?: "Não foi possível simular as respostas.")
            } finally {
                simulando = false
            }
        }
    }

    /**
     * Junta [origem] em [destino] na turma aberta e recarrega o grafo. [aoTerminar] recebe o resultado
     * (sucesso) ou a mensagem de erro (ex.: formaria ciclo).
     */
    fun juntar(origem: String, destino: String, aoTerminar: (Result<ResultadoFusao>) -> Unit) {
        val turma = turmaId ?: return
        executarEdicao(turma, "juntar os conceitos", { fundirConceitos(turma, origem, destino) }, aoTerminar)
    }

    /** Marca dois conceitos parecidos como distintos (somem das possíveis duplicatas). */
    fun marcarComoDistintos(conceitoA: String, conceitoB: String, aoTerminar: (Result<Unit>) -> Unit) {
        val turma = turmaId ?: return
        executarEdicao(turma, "marcar os conceitos", { marcarDistintos(turma, conceitoA, conceitoB) }, aoTerminar)
    }

    private fun <T> executarEdicao(turma: String, oQue: String, acao: suspend () -> T, aoTerminar: (Result<T>) -> Unit) {
        if (juntando) return
        juntando = true
        viewModelScope.launch {
            try {
                val resultado = acao()
                if (this@TurmaViewModel.turmaId == turma) {
                    _grafo.value = EstadoGrafo.Pronto(buscarGrafo(turma))
                }
                aoTerminar(Result.success(resultado))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aoTerminar(Result.failure(Exception((e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Não foi possível $oQue.")))
            } finally {
                juntando = false
            }
        }
    }

    fun recarregar() {
        turmaId?.let { abrir(it, forcar = true) }
    }

    /** Recarrega a lista da aba (Atividades ou Turma) da turma aberta. */
    fun carregarAba(aba: String = this.aba) {
        val turma = turmaId ?: return
        when (aba) {
            ABA_ATIVIDADES -> viewModelScope.launch {
                if (atividades !is Carga.Pronto) atividades = Carga.Carregando
                atividades = carregar("carregar as atividades") { buscarAtividades(turma) }
            }
            ABA_ESTATISTICAS -> carregarEstatisticas()
            ABA_TURMA -> viewModelScope.launch {
                if (membros !is Carga.Pronto) membros = Carga.Carregando
                membros = carregar("carregar os alunos") { buscarMembros(turma) }
            }
        }
    }

    /**
     * "Abrir sessão em sala": confere (de novo, no servidor) se alguém já entrou na turma. Sem alunos,
     * mostra o aviso antes (que deixa abrir mesmo assim); sessão já aberta ("Voltar à sessão") abre direto.
     */
    fun abrirSessao(atividade: AtividadePublicada, abrir: (AtividadePublicada) -> Unit) {
        val turma = turmaId ?: return
        if (atividade.sessaoAberta != null) { abrir(atividade); return }
        if (conferindoAlunos) return
        conferindoAlunos = true
        viewModelScope.launch {
            val atual = carregar("carregar os alunos") { buscarMembros(turma) }
            if (this@TurmaViewModel.turmaId == turma) membros = atual
            conferindoAlunos = false
            when {
                atual is Carga.Pronto && atual.valor.isEmpty() -> sessaoSemAlunos = atividade
                // Sem conseguir conferir, não impede: o professor pode estar com a sala esperando.
                else -> { aviso = null; abrir(atividade) }
            }
        }
    }

    /**
     * Depois de publicar: aviso "falta abrir a sessão" com o botão que abre a sessão desta atividade
     * (a lista é recarregada para ter a atividade nova).
     */
    fun avisarPublicada(atividadeId: String) {
        val turma = turmaId ?: return
        aviso = AvisoTurma(TEXTO_PUBLICADA)
        viewModelScope.launch {
            val atual = carregar("carregar as atividades") { buscarAtividades(turma) }
            if (this@TurmaViewModel.turmaId != turma) return@launch
            atividades = atual
            val nova = (atual as? Carga.Pronto)?.valor?.find { it.id == atividadeId }
            if (aviso?.texto == TEXTO_PUBLICADA) aviso = AvisoTurma(TEXTO_PUBLICADA, nova?.let { "Abrir sessão em sala" }, nova)
        }
    }

    fun carregarEstatisticas() {
        val turma = turmaId ?: return
        val incluir = incluirSimulacao
        viewModelScope.launch {
            if (estatisticas !is Carga.Pronto) estatisticas = Carga.Carregando
            val r = carregar("carregar as estatísticas") { buscarEstatisticas(turma, incluir) }
            if (this@TurmaViewModel.turmaId == turma && incluirSimulacao == incluir) estatisticas = r
        }
    }

    fun mudarIncluirSimulacao(incluir: Boolean) {
        if (incluir == incluirSimulacao) return
        incluirSimulacao = incluir
        carregarEstatisticas()
    }

    /** Estatísticas → mapa, com o conceito destacado (da turma toda). */
    fun abrirConceitoNoMapa(conceito: String) {
        selecionarAluno(null)
        conceitoEmFoco = conceito
        aba = ABA_GRAFO
    }

    /** UC11: pinta o mapa com o domínio estimado do aluno; null volta à turma toda. */
    fun selecionarAluno(aluno: Aluno?) {
        alunoSelecionado = aluno
        historico = null
        historicoDe = null
        val turma = turmaId ?: return
        val chave = aluno?.chave ?: return
        mapaDoAluno = Carga.Carregando
        viewModelScope.launch {
            val r = carregar("carregar o mapa do aluno") { buscarMapaAluno(turma, chave) }
            if (alunoSelecionado?.chave == chave) mapaDoAluno = r
        }
    }

    /** UC12: histórico do aluno selecionado no conceito. */
    fun carregarHistorico(conceito: String) {
        val turma = turmaId ?: return
        val chave = alunoSelecionado?.chave ?: return
        if (historicoDe == conceito && historico is Carga.Pronto) return
        historicoDe = conceito
        historico = Carga.Carregando
        viewModelScope.launch {
            val r = carregar("carregar o histórico") { buscarHistorico(turma, chave, conceito) }
            if (alunoSelecionado?.chave == chave && historicoDe == conceito) historico = r
        }
    }

    /** Fase 5: lista dos alunos reais no conceito, os piores primeiro. */
    fun verAlunosComDificuldade(conceito: String) {
        val turma = turmaId ?: return
        alunosDe = conceito
        alunosDoConceito = Carga.Carregando
        viewModelScope.launch {
            val r = carregar("carregar os alunos deste conceito") { buscarAlunosConceito(turma, conceito) }
            if (this@TurmaViewModel.turmaId == turma && alunosDe == conceito) alunosDoConceito = r
        }
    }

    fun fecharAlunosComDificuldade() {
        alunosDoConceito = null
        alunosDe = null
    }

    /** Da lista de alunos do conceito para o mapa do aluno, já com o conceito selecionado e o histórico. */
    fun abrirAlunoNoConceito(aluno: AlunoNoConceito) {
        val conceito = alunosDe ?: return
        fecharAlunosComDificuldade()
        val doGrafo = (grafo.value as? EstadoGrafo.Pronto)?.grafo?.alunos?.find { it.chave == aluno.alunoId }
        selecionarAluno(doGrafo ?: Aluno(aluno.nome, aluno.estado ?: EstadoConceito.Desenvolvimento, aluno.alunoId))
        conceitoEmFoco = conceito
        aba = ABA_GRAFO
    }

    /** Abre o diálogo "Convidar alunos" ([novo] = trocar o código). */
    fun abrirConvite(novo: Boolean = false) {
        val turma = turmaId ?: return
        convite = Carga.Carregando
        viewModelScope.launch { convite = carregar("gerar o convite") { buscarConvite(turma, novo) } }
    }

    fun fecharConvite() {
        convite = null
    }

    private suspend fun <T> carregar(oQue: String, bloco: suspend () -> T): Carga<T> = try {
        Carga.Pronto(bloco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Carga.Erro((e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Não foi possível $oQue.")
    }

    companion object {
        const val ABA_GRAFO = "Mapa do conhecimento"
        const val TEXTO_PUBLICADA = "Atividade publicada. Para a turma responder, abra a sessão em sala."
        const val ABA_ATIVIDADES = "Atividades"
        const val ABA_TURMA = "Turma"
        const val ABA_ESTATISTICAS = "Estatísticas"
    }
}
