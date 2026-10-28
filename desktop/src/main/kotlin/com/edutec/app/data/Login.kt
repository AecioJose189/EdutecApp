package com.edutec.app.data

import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Desktop
import java.net.BindException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import kotlin.time.Duration.Companion.minutes

/** Falha no login; [message] já é um texto para o professor. */
class ErroLogin(mensagem: String) : Exception(mensagem)

/** A sessão expirou ou foi recusada pelo servidor (HTTP 401): o app volta para a tela de entrada. */
class SessaoExpirada : Exception("Sua sessão expirou. Entre de novo para continuar.")

/** Professor logado, como aparece no topo do app. */
data class Usuario(val id: String, val nome: String, val email: String?, val fotoUrl: String?)

/**
 * Login Google com o navegador do sistema (fluxo PKCE do Supabase Auth).
 *
 * O Lumini abre um servidor HTTP local na porta FIXA [PORTA] só durante o login; o Supabase devolve o
 * navegador para [URL_RETORNO] com `?code=...`, e o código vira a sessão (salva por [SessaoEmArquivo]).
 * A URL de retorno precisa estar em Authentication → URL Configuration → Redirect URLs do Supabase.
 * Sem o verificador do PKCE (que só existe neste processo), o código não serve para mais ninguém.
 */
object Login {
    const val PORTA = 3737
    const val URL_RETORNO = "http://localhost:$PORTA/callback"

    /** Abre o navegador e espera o retorno (até 5 min). [aoAbrir] recebe a URL, para o caso de o navegador não abrir. */
    suspend fun entrarComGoogle(aoAbrir: (String) -> Unit = {}) {
        val auth = Supabase.cliente.auth
        val retorno = CompletableDeferred<Result<String>>()
        val servidor = try {
            HttpServer.create(InetSocketAddress(PORTA), 0)
        } catch (e: BindException) {
            throw ErroLogin("A porta $PORTA está ocupada (outro Lumini aberto?). Feche o outro programa e tente de novo.")
        }
        servidor.createContext("/callback") { troca ->
            val parametros = parametrosDaQuery(troca.requestURI.rawQuery)
            val codigo = parametros["code"]
            val ok = codigo != null
            val pagina = paginaDeRetorno(
                if (ok) "Pronto! Você já pode voltar ao Lumini." else "Não foi possível entrar: " +
                    (parametros["error_description"] ?: parametros["error"] ?: "retorno sem código."),
            ).toByteArray()
            troca.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            troca.sendResponseHeaders(200, pagina.size.toLong())
            troca.responseBody.use { it.write(pagina) }
            retorno.complete(
                if (ok) Result.success(codigo!!)
                else Result.failure(ErroLogin(parametros["error_description"] ?: "O Google não autorizou o login.")),
            )
        }
        servidor.start()
        try {
            val url = auth.getOAuthUrl(Google, URL_RETORNO)
            aoAbrir(url)
            abrirNoNavegador(url)
            val codigo = try {
                withTimeout(5.minutes) { retorno.await() }.getOrThrow()
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw ErroLogin("O login não foi concluído no navegador. Tente de novo.")
            }
            try {
                auth.exchangeCodeForSession(codigo)
            } catch (e: Exception) {
                throw ErroLogin("O Supabase recusou o login. Tente de novo.")
            }
        } finally {
            servidor.stop(0)
        }
    }

    /** Apaga a sessão (no servidor e no disco). Mesmo sem internet, sai localmente. */
    suspend fun sair() {
        val auth = Supabase.cliente.auth
        runCatching { auth.signOut() }
        runCatching { auth.clearSession() }
    }

    /** Chamado quando uma chamada volta 401: limpa a sessão local, e o app mostra a tela de entrada. */
    suspend fun sessaoExpirou() {
        runCatching { Supabase.cliente.auth.clearSession() }
    }

    fun usuarioDe(info: UserInfo): Usuario {
        val meta = info.userMetadata
        fun campo(nome: String) = (meta?.get(nome) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return Usuario(
            id = info.id,
            nome = campo("full_name") ?: campo("name") ?: info.email?.substringBefore('@') ?: "Professor",
            email = info.email,
            fotoUrl = campo("avatar_url") ?: campo("picture"),
        )
    }

    private suspend fun abrirNoNavegador(url: String) = withContext(Dispatchers.IO) {
        val viaDesktop = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url)); true
            } else false
        }.getOrDefault(false)
        if (viaDesktop) return@withContext
        val so = System.getProperty("os.name").lowercase()
        val comando = when {
            so.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            so.contains("mac") -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        runCatching { ProcessBuilder(comando).start() }
    }

    private fun parametrosDaQuery(query: String?): Map<String, String> =
        query.orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }

    private fun paginaDeRetorno(mensagem: String) = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Lumini</title></head>
        <body style="font-family:sans-serif;background:#F6F4EF;color:#1A1A1A;display:flex;align-items:center;
        justify-content:center;height:100vh;margin:0"><div style="background:#fff;border-radius:24px;padding:40px 48px;
        text-align:center"><h1 style="margin:0 0 12px">Lumini</h1><p style="font-size:18px;margin:0">${mensagem.escaparHtml()}</p>
        </div></body></html>
    """.trimIndent()

    private fun String.escaparHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
