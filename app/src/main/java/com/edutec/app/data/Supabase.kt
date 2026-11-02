package com.edutec.app.data

import com.edutec.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Cliente Supabase único do app: só a URL e a chave pública (local.properties → BuildConfig).
 * Todo acesso a dados passa por Edge Functions (gerar-atividade, turma-api).
 */
object Supabase {
    /** Cobre a geração (a função espera o Gemini por até 150 s). */
    val TEMPO_LIMITE = 160.seconds

    val cliente: SupabaseClient by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_CHAVE_PUBLICA.isNotBlank()) {
            "Defina SUPABASE_URL e SUPABASE_CHAVE_PUBLICA no local.properties"
        }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_CHAVE_PUBLICA) {
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
