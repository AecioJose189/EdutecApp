package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.Convite
import com.edutec.app.data.ErroTurmaApi
import com.edutec.app.data.Membro
import com.edutec.app.data.SessaoExpirada
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.TurmaRepositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
) : ViewModel() {
    var aba by mutableStateOf(ABA_GRAFO)

    var atividades by mutableStateOf<Carga<List<AtividadePublicada>>>(Carga.Carregando)
        private set

    var membros by mutableStateOf<Carga<List<Membro>>>(Carga.Carregando)
        private set

    /** null = diálogo de convite fechado. */
    var convite by mutableStateOf<Carga<Convite>?>(null)
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
        }
        this.turmaId = turmaId
        carregamento?.cancel()
        // Recarregar a mesma turma mantém o grafo atual na tela até chegar o novo.
        if (outraTurma || _grafo.value !is EstadoGrafo.Pronto) _grafo.value = EstadoGrafo.Carregando
        carregamento = viewModelScope.launch {
            _grafo.value = try {
                EstadoGrafo.Pronto(buscarGrafo(turmaId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoGrafo.Erro((e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Não foi possível carregar o grafo da turma.")
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
            ABA_TURMA -> viewModelScope.launch {
                if (membros !is Carga.Pronto) membros = Carga.Carregando
                membros = carregar("carregar os alunos") { buscarMembros(turma) }
            }
        }
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
        const val ABA_GRAFO = "Grafo"
        const val ABA_ATIVIDADES = "Atividades"
        const val ABA_TURMA = "Turma"
    }
}
