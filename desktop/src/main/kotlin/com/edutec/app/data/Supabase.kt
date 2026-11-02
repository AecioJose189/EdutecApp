package com.edutec.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Cliente Supabase único do app: só a URL e a chave pública (ver [Configuracao]) e a sessão do
 * professor (login Google, ver [Login]). Com o Auth instalado, as Edge Functions, as tabelas e o
 * Realtime recebem o JWT do usuário sozinhos, e o token é renovado antes de expirar.
 */
object Supabase {
    /** Cobre a geração (a função espera o Gemini por até 150 s). */
    val TEMPO_LIMITE = 160.seconds

    val cliente: SupabaseClient by lazy {
        val url = Configuracao.supabaseUrl
        val chave = Configuracao.chavePublica
        if (url.isBlank() || chave.isBlank()) throw ConfiguracaoAusente()
        createSupabaseClient(url, chave) {
            install(Auth) {
                flowType = FlowType.PKCE
                sessionManager = SessaoEmArquivo(Configuracao.arquivoSessao)
                // O verificador do PKCE só precisa durar o login (mesmo processo).
                codeVerifierCache = MemoryCodeVerifierCache()
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Functions)
            install(Postgrest)
            install(Realtime)
            requestTimeout = TEMPO_LIMITE
            // Sem isto o OkHttp corta a leitura em 10 s (padrão dele) enquanto a função espera o Gemini.
            httpEngine = OkHttp.create {
                config {
                    readTimeout(TEMPO_LIMITE.toJavaDuration())
                    writeTimeout(TEMPO_LIMITE.toJavaDuration())
                }
            }
        }
    }
}

/** Falta a URL ou a chave pública do Supabase; a mensagem diz ao professor onde configurar. */
class ConfiguracaoAusente : Exception(
    "Configure SUPABASE_URL e SUPABASE_CHAVE_PUBLICA em ${Configuracao.arquivoDoUsuario} (ou em variáveis de ambiente)."
)
