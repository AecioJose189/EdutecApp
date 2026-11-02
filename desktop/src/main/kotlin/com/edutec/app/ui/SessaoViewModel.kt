package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edutec.app.data.ConfiguracaoAusente
import com.edutec.app.data.ErroLogin
import com.edutec.app.data.Login
import com.edutec.app.data.Supabase
import com.edutec.app.data.Usuario
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface EstadoSessao {
    /** Lendo a sessão salva (ou renovando o token) ao abrir o app. */
    data object Iniciando : EstadoSessao

    /** Tela de entrada; [aviso] explica por que (sessão expirada, configuração ausente...). */
    data class Deslogado(val aviso: String? = null) : EstadoSessao
    data class Logado(val usuario: Usuario) : EstadoSessao
}

/**
 * Login do professor. Segue o sessionStatus do Supabase Auth: sessão salva → já entra; token
 * renovado sozinho; 401 numa chamada (Login.sessaoExpirou) ou refresh recusado → volta para a entrada.
 */
class SessaoViewModel : ViewModel() {
    var estado by mutableStateOf<EstadoSessao>(EstadoSessao.Iniciando)
        private set

    /** Esperando o retorno do navegador. */
    var entrando by mutableStateOf(false)
        private set

    /** URL de login, para copiar se o navegador não abrir sozinho. */
    var urlLogin by mutableStateOf<String?>(null)
        private set

    var erro by mutableStateOf<String?>(null)
        private set

    private var login: Job? = null
    private var saindo = false

    init {
        try {
            val auth = Supabase.cliente.auth
            viewModelScope.launch {
                auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val info = status.session.user ?: auth.currentUserOrNull()
                            if (info != null) estado = EstadoSessao.Logado(Login.usuarioDe(info))
                        }
                        is SessionStatus.NotAuthenticated -> {
                            val estavaLogado = estado is EstadoSessao.Logado
                            estado = EstadoSessao.Deslogado(
                                if (estavaLogado && !saindo) "Sua sessão expirou. Entre de novo para continuar de onde parou." else null,
                            )
                            saindo = false
                        }
                        is SessionStatus.RefreshFailure ->
                            // Sem rede: continua logado (o Auth tenta de novo); sem sessão na tela, avisa.
                            if (estado !is EstadoSessao.Logado) {
                                estado = EstadoSessao.Deslogado("Sem conexão com o servidor. Verifique a internet.")
                            }
                        SessionStatus.Initializing -> Unit
                    }
                }
            }
        } catch (e: ConfiguracaoAusente) {
            estado = EstadoSessao.Deslogado(e.message)
        }
    }

    fun entrar() {
        if (entrando) return
        entrando = true
        erro = null
        login = viewModelScope.launch {
            try {
                Login.entrarComGoogle { urlLogin = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConfiguracaoAusente) {
                erro = e.message
            } catch (e: ErroLogin) {
                erro = e.message
            } catch (e: Exception) {
                erro = "Não foi possível entrar. Verifique a internet e tente de novo."
            } finally {
                entrando = false
                urlLogin = null
            }
        }
    }

    fun cancelarLogin() {
        login?.cancel()
    }

    fun sair() {
        saindo = true
        viewModelScope.launch { Login.sair() }
    }
}
