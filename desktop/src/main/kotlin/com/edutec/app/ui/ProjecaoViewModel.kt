package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.ErroTurmaApi
import com.edutec.app.data.PainelSessao
import com.edutec.app.data.Participante
import com.edutec.app.data.SessaoExpirada
import com.edutec.app.data.SessaoSala
import com.edutec.app.data.Supabase
import com.edutec.app.data.TokenQr
import com.edutec.app.data.TurmaRepositorio
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Sessão em sala projetada: abre (ou retoma) a sessão da atividade, troca o QR a cada
 * [INTERVALO_QR] s (o token do servidor vale ~40 s) e acompanha quem entrou e quem enviou pelo
 * Realtime (tentativas da sessão), com uma consulta a cada [INTERVALO_CONSULTA] s de reserva.
 */
class ProjecaoViewModel(
    private val abrirSessao: suspend (String) -> SessaoSala = TurmaRepositorio::abrirSessao,
    private val buscarToken: suspend (String) -> TokenQr = TurmaRepositorio::tokenQr,
    private val buscarPainel: suspend (String) -> PainelSessao = TurmaRepositorio::participantes,
    private val fecharEntrada: suspend (String) -> SessaoSala = TurmaRepositorio::encerrarEntrada,
    private val fecharSessao: suspend (String) -> SessaoSala = TurmaRepositorio::encerrarSessao,
) : ViewModel() {
    var atividade by mutableStateOf<AtividadePublicada?>(null)
        private set
    var sessao by mutableStateOf<SessaoSala?>(null)
        private set
    var qr by mutableStateOf<TokenQr?>(null)
        private set

    /** Segundos até o próximo QR (para o anel de contagem). */
    var segundosParaTrocar by mutableIntStateOf(INTERVALO_QR)
        private set
    var participantes by mutableStateOf<List<Participante>>(emptyList())
        private set

    /** Realtime conectado (o painel atualiza na hora; senão, a cada [INTERVALO_CONSULTA] s). */
    var aoVivo by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set

    private var trabalho: Job? = null
    private var canal: RealtimeChannel? = null

    fun abrir(atividade: AtividadePublicada) {
        fechar()
        this.atividade = atividade
        trabalho = viewModelScope.launch {
            val aberta = tentar { abrirSessao(atividade.id) } ?: return@launch
            sessao = aberta
            launch { trocarQr(aberta.id) }
            launch { acompanharAoVivo(aberta.id) }
            while (true) {
                atualizarPainel(aberta.id)
                delay(INTERVALO_CONSULTA * 1000L)
            }
        }
    }

    fun encerrarEntrada() {
        val id = sessao?.id ?: return
        viewModelScope.launch { tentar { fecharEntrada(id) }?.let { sessao = it; qr = null } }
    }

    fun encerrarSessao() {
        val id = sessao?.id ?: return
        viewModelScope.launch {
            tentar { fecharSessao(id) }?.let {
                sessao = it
                qr = null
                atualizarPainel(id)
            }
        }
    }

    /** Sai da projeção (a sessão continua aberta no servidor até "Encerrar sessão"). */
    fun fechar() {
        trabalho?.cancel()
        trabalho = null
        canal?.let { c -> viewModelScope.launch { runCatching { Supabase.cliente.realtime.removeChannel(c) } } }
        canal = null
        atividade = null
        sessao = null
        qr = null
        participantes = emptyList()
        aoVivo = false
        erro = null
    }

    private suspend fun trocarQr(sessaoId: String) {
        while (sessao?.entradaAberta == true) {
            tentar { buscarToken(sessaoId) }?.let { qr = it }
            for (s in INTERVALO_QR downTo 1) {
                segundosParaTrocar = s
                delay(1000)
            }
        }
    }

    /** Fluxos filhos desta corrotina: param junto com a projeção. Sem Realtime, fica a consulta periódica. */
    private suspend fun acompanharAoVivo(sessaoId: String) = coroutineScope {
        try {
            val c = Supabase.cliente.channel("sessao-$sessaoId")
            canal = c
            c.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "tentativas"
                filter("sessao_id", FilterOperator.EQ, sessaoId)
            }.onEach { atualizarPainel(sessaoId) }.launchIn(this)
            c.status.onEach { aoVivo = it == RealtimeChannel.Status.SUBSCRIBED }.launchIn(this)
            c.subscribe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aoVivo = false
        }
    }

    private suspend fun atualizarPainel(sessaoId: String) {
        tentar(mostrarErro = false) { buscarPainel(sessaoId) }?.let { p ->
            participantes = p.participantes
            if (!p.sessao.aberta || !p.sessao.entradaAberta) sessao = p.sessao
        }
    }

    private suspend fun <T> tentar(mostrarErro: Boolean = true, bloco: suspend () -> T): T? = try {
        bloco().also { if (mostrarErro) erro = null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (mostrarErro || e is SessaoExpirada) {
            erro = (e as? ErroTurmaApi ?: e as? SessaoExpirada)?.message ?: "Falha de conexão com o servidor."
        }
        null
    }

    override fun onCleared() = fechar()

    companion object {
        /** O token vale a janela atual e a anterior (20 s cada): trocar a cada 15 s nunca mostra QR vencido. */
        const val INTERVALO_QR = 15
        const val INTERVALO_CONSULTA = 10
    }
}
