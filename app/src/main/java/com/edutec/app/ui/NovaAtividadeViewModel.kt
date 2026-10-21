package com.edutec.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.AtividadeGerada
import com.edutec.app.data.ErroGeracao
import com.edutec.app.data.GeracaoRepositorio
import com.edutec.app.data.PedidoGeracao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Etapas 3 (Gerando) e 4 (Revisão) da Nova atividade. */
sealed interface EstadoGeracao {
    data object Ocioso : EstadoGeracao
    data class Gerando(val pedido: PedidoGeracao, val segundos: Int = 0) : EstadoGeracao
    data class Concluido(val pedido: PedidoGeracao, val resultado: AtividadeGerada) : EstadoGeracao
    data class Erro(val pedido: PedidoGeracao, val tipo: TipoErro, val detalhe: String? = null) : EstadoGeracao
}

enum class TipoErro { Cota, Indisponivel, TempoEsgotado, SemConexao, Outro }

class NovaAtividadeViewModel(
    private val gerarAtividade: suspend (PedidoGeracao) -> AtividadeGerada = GeracaoRepositorio::gerar,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoGeracao>(EstadoGeracao.Ocioso)
    val estado: StateFlow<EstadoGeracao> = _estado.asStateFlow()

    private var trabalho: Job? = null

    fun gerar(pedido: PedidoGeracao) {
        trabalho?.cancel()
        _estado.value = EstadoGeracao.Gerando(pedido)
        trabalho = viewModelScope.launch {
            val cronometro = launch {
                while (true) {
                    delay(1_000)
                    _estado.update { if (it is EstadoGeracao.Gerando) it.copy(segundos = it.segundos + 1) else it }
                }
            }
            _estado.value = try {
                EstadoGeracao.Concluido(pedido, gerarAtividade(pedido))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErroGeracao) {
                when (e) {
                    is ErroGeracao.Cota -> EstadoGeracao.Erro(pedido, TipoErro.Cota)
                    is ErroGeracao.Indisponivel -> EstadoGeracao.Erro(pedido, TipoErro.Indisponivel)
                    is ErroGeracao.TempoEsgotado -> EstadoGeracao.Erro(pedido, TipoErro.TempoEsgotado)
                    is ErroGeracao.SemConexao -> EstadoGeracao.Erro(pedido, TipoErro.SemConexao)
                    is ErroGeracao.Servidor -> EstadoGeracao.Erro(pedido, TipoErro.Outro, e.detalhe)
                }
            } catch (e: Exception) {
                EstadoGeracao.Erro(pedido, TipoErro.Outro)
            } finally {
                cronometro.cancel()
            }
        }
    }

    fun tentarDeNovo() {
        (_estado.value as? EstadoGeracao.Erro)?.let { gerar(it.pedido) }
    }

    /**
     * Volta ao formulário (etapas 1–2), cancelando a geração se estiver em andamento.
     * Serve para "Cancelar", "Voltar" e o voltar do Android. O pedido já enviado não é desfeito no servidor.
     */
    fun voltar() {
        trabalho?.cancel()
        trabalho = null
        _estado.value = EstadoGeracao.Ocioso
    }
}
