package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.ErroTurmaApi
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

/** Dados da tela Turma (escopo da Activity: girar a tela não recarrega). */
class TurmaViewModel(
    private val buscarGrafo: suspend (String) -> GrafoTurma = TurmaRepositorio::grafo,
    private val simularRespostas: suspend (String) -> Unit = TurmaRepositorio::simular,
) : ViewModel() {
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
                EstadoGrafo.Erro((e as? ErroTurmaApi)?.message ?: "Não foi possível carregar o grafo da turma.")
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
}
