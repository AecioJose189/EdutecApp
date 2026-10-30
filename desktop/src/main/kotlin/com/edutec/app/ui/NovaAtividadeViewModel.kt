package com.edutec.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.AtividadeGerada
import com.edutec.app.data.ErroGeracao
import com.edutec.app.data.ErroTurmaApi
import com.edutec.app.data.TurmaRepositorio
import com.edutec.app.data.GeracaoRepositorio
import com.edutec.app.data.PedidoGeracao
import com.edutec.app.data.SessaoExpirada
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Etapas 3 (Gerando), 4 (Revisão) e 5 (Publicar) da Nova atividade. */
sealed interface EstadoGeracao {
    data object Ocioso : EstadoGeracao
    data class Gerando(val pedido: PedidoGeracao, val segundos: Int = 0) : EstadoGeracao
    data class Concluido(
        val pedido: PedidoGeracao,
        val resultado: AtividadeGerada,
        val publicando: Boolean = false,
        /** Mensagem da última tentativa de publicar que falhou. */
        val erroPublicacao: String? = null,
    ) : EstadoGeracao

    /** Publicada na turma: a tela volta para a Turma. */
    data class Publicado(val turmaId: String) : EstadoGeracao
    data class Erro(val pedido: PedidoGeracao, val tipo: TipoErro, val detalhe: String? = null) : EstadoGeracao
}

enum class TipoErro { Cota, Indisponivel, TempoEsgotado, SemConexao, Outro }

/** Quantidade oferecida em "Gerar menos questões" quando a IA está sobrecarregada. */
const val QUESTOES_SE_SOBRECARREGADA = 3

class NovaAtividadeViewModel(
    private val gerarAtividade: suspend (PedidoGeracao) -> AtividadeGerada = GeracaoRepositorio::gerar,
    private val publicarAtividade: suspend (String, Long, Boolean, Int?, String?) -> String = TurmaRepositorio::publicar,
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
            } catch (e: SessaoExpirada) {
                // Volta para a tela de entrada; depois do login, "Tentar de novo" refaz o mesmo pedido.
                EstadoGeracao.Erro(pedido, TipoErro.Outro, e.message)
            } catch (e: Exception) {
                EstadoGeracao.Erro(pedido, TipoErro.Outro)
            } finally {
                cronometro.cancel()
            }
        }
    }

    /** Etapa 5: publica o resultado revisado na turma (RN04: só com a ação do professor). */
    fun publicar(turmaId: String) {
        val atual = _estado.value as? EstadoGeracao.Concluido ?: return
        if (atual.publicando) return
        val geracaoId = atual.resultado.geracaoId
        if (geracaoId == null) {
            _estado.value = atual.copy(erroPublicacao = "Esta geração não foi salva no servidor. Gere novas questões para publicar.")
            return
        }
        _estado.value = atual.copy(publicando = true, erroPublicacao = null)
        trabalho = viewModelScope.launch {
            _estado.value = try {
                publicarAtividade(turmaId, geracaoId, atual.pedido.permitirRefazer, atual.pedido.tempoMinutos, atual.pedido.titulo)
                EstadoGeracao.Publicado(turmaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                atual.copy(erroPublicacao = (e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Não foi possível publicar a atividade.")
            }
        }
    }

    fun tentarDeNovo() {
        (_estado.value as? EstadoGeracao.Erro)?.let { gerar(it.pedido) }
    }

    /** IA sobrecarregada: mesmo pedido com menos questões (resposta mais curta e mais rápida). */
    fun gerarMenos() {
        (_estado.value as? EstadoGeracao.Erro)?.let {
            gerar(it.pedido.copy(quantidade = minOf(it.pedido.quantidade, QUESTOES_SE_SOBRECARREGADA)))
        }
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
