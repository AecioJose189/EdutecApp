package com.edutec.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Cliente Supabase único do app: só a URL e a chave pública (ver [Configuracao]).
 * Todo acesso a dados passa por Edge Functions (gerar-atividade, turma-api).
 */
object Supabase {
    /** Cobre a geração (a função espera o Gemini por até 150 s). */
    val TEMPO_LIMITE = 160.seconds

    val cliente: SupabaseClient by lazy {
        val url = Configuracao.supabaseUrl
        val chave = Configuracao.chavePublica
        if (url.isBlank() || chave.isBlank()) throw ConfiguracaoAusente()
        createSupabaseClient(url, chave) {
            install(Functions)
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
