package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.ConfiguracaoAusente
import com.edutec.app.data.ErroTurmaApi
import com.edutec.app.data.Turma
import com.edutec.app.data.TurmaRepositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Lista de turmas vinda do servidor (turma-api). */
sealed interface EstadoTurmas {
    data object Carregando : EstadoTurmas
    data class Erro(val mensagem: String) : EstadoTurmas
    data class Pronto(val turmas: List<Turma>) : EstadoTurmas
}

/**
 * Onde o professor está no app (Minhas turmas → Turma → Nova atividade / Projeção) e as turmas do
 * servidor. Fica num ViewModel da janela: se a sessão expirar, o app mostra a entrada e, depois do
 * login com a mesma conta, volta para a mesma tela.
 */
class NavegacaoViewModel(
    private val listar: suspend () -> List<Turma> = TurmaRepositorio::listarTurmas,
    private val criar: suspend (String, String) -> Turma = TurmaRepositorio::criarTurma,
) : ViewModel() {
    var turmas by mutableStateOf<EstadoTurmas>(EstadoTurmas.Carregando)
        private set

    /** Guarda só o id: os dados da turma aberta vêm sempre da lista mais recente. */
    private var idTurmaAberta by mutableStateOf<String?>(null)

    /** Cópia da turma aberta para quando a lista estiver recarregando (ou falhar ao recarregar). */
    private var ultimaTurmaAberta: Turma? = null

    val turmaAberta: Turma?
        get() {
            val id = idTurmaAberta ?: return null
            val daLista = (turmas as? EstadoTurmas.Pronto)?.turmas?.find { it.id == id }
            return (daLista ?: ultimaTurmaAberta?.takeIf { it.id == id })?.also { ultimaTurmaAberta = it }
        }

    /** null = Nova atividade fechada; caso contrário, os conceitos já marcados ao abrir. */
    var novaAtividade by mutableStateOf<Set<String>?>(null)
        private set

    /** Atividade cuja sessão em sala está projetada; null = projeção fechada. */
    var projecao by mutableStateOf<AtividadePublicada?>(null)
        private set

    private var carregamento: Job? = null
    private var usuarioId: String? = null

    /** Login concluído (ou sessão restaurada). Outra conta → começa do zero, em Minhas turmas. */
    fun entrou(usuario: String) {
        val mesmo = usuario == usuarioId
        if (!mesmo) {
            usuarioId = usuario
            idTurmaAberta = null
            ultimaTurmaAberta = null
            novaAtividade = null
            projecao = null
            turmas = EstadoTurmas.Carregando
        }
        recarregarTurmas(silencioso = mesmo)
    }

    fun abrirProjecao(atividade: AtividadePublicada) {
        projecao = atividade
    }

    fun fecharProjecao() {
        projecao = null
    }

    /** Busca as turmas de novo. Com [silencioso], mantém a lista atual na tela enquanto carrega. */
    fun recarregarTurmas(silencioso: Boolean = false) {
        carregamento?.cancel()
        if (!silencioso || turmas !is EstadoTurmas.Pronto) turmas = EstadoTurmas.Carregando
        carregamento = viewModelScope.launch {
            turmas = try {
                EstadoTurmas.Pronto(listar())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErroTurmaApi) {
                if (silencioso && turmas is EstadoTurmas.Pronto) return@launch
                EstadoTurmas.Erro(e.message.orEmpty())
            } catch (e: ConfiguracaoAusente) {
                EstadoTurmas.Erro(e.message.orEmpty())
            } catch (e: Exception) {
                if (silencioso && turmas is EstadoTurmas.Pronto) return@launch
                EstadoTurmas.Erro("Não foi possível carregar as turmas.")
            }
        }
    }

    /** Cria a turma no servidor; [aoTerminar] recebe null em caso de sucesso ou a mensagem de erro. */
    fun criarTurma(nome: String, codigo: String, aoTerminar: (erro: String?) -> Unit) {
        viewModelScope.launch {
            try {
                val nova = criar(nome, codigo)
                val atuais = (turmas as? EstadoTurmas.Pronto)?.turmas.orEmpty()
                turmas = EstadoTurmas.Pronto(atuais + nova)
                aoTerminar(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aoTerminar((e as? ErroTurmaApi)?.message ?: "Não foi possível criar a turma.")
            }
        }
    }

    fun abrirTurma(turma: Turma) {
        ultimaTurmaAberta = turma
        idTurmaAberta = turma.id
    }

    fun fecharTurma() {
        idTurmaAberta = null
    }

    fun abrirNovaAtividade(conceitosIniciais: Set<String>) {
        novaAtividade = conceitosIniciais
    }

    fun fecharNovaAtividade() {
        novaAtividade = null
    }
}
