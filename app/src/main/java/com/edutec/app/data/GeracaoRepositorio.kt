package com.edutec.app.data

import com.edutec.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Falhas da geração, já separadas pelo que a tela precisa explicar ao professor. */
sealed class ErroGeracao(mensagem: String) : Exception(mensagem) {
    /** Cota do plano gratuito do Gemini esgotada (HTTP 429). */
    class Cota(detalhe: String) : ErroGeracao(detalhe)

    /** O Gemini está sobrecarregado ou fora do ar (HTTP 503 da função); costuma passar em instantes. */
    class Indisponivel : ErroGeracao("IA indisponível")

    /** A IA ou a rede passaram do tempo (timeout do app ou HTTP 504 da função). */
    class TempoEsgotado : ErroGeracao("Tempo esgotado")

    /** Sem internet ou servidor inacessível. */
    class SemConexao : ErroGeracao("Sem conexão")

    /** Qualquer outra resposta de erro; [detalhe] é a mensagem `erro` devolvida pela função. */
    class Servidor(val detalhe: String) : ErroGeracao(detalhe)
}

/** Acesso à Edge Function gerar-atividade (Supabase). */
object GeracaoRepositorio {
    private const val FUNCAO = "gerar-atividade"
    private val TEMPO_LIMITE = 160.seconds

    private val json = Json { ignoreUnknownKeys = true }

    private val supabase by lazy {
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

    suspend fun gerar(pedido: PedidoGeracao): AtividadeGerada = withContext(Dispatchers.IO) {
        val bytes = pedido.arquivo.readBytes()
        val corpo = MultiPartFormDataContent(
            formData {
                append(
                    "pdf", bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(HttpHeaders.ContentDisposition, "filename=\"${pedido.nomeArquivo.replace("\"", "")}\"")
                    },
                )
                append("quantidade", pedido.quantidade.toString())
                append("formato", pedido.formato)
                append("dificuldade", pedido.dificuldade)
            }
        )

        val resposta = try {
            supabase.functions.invoke(FUNCAO) { setBody(corpo) }
        } catch (e: RestException) {
            // Atenção: e.message inclui os cabeçalhos da requisição (com a chave); não exibir nem logar.
            val detalhe = mensagemDeErro(e.error)
            throw when (e.statusCode) {
                429 -> ErroGeracao.Cota(detalhe)
                503 -> ErroGeracao.Indisponivel()
                504 -> ErroGeracao.TempoEsgotado()
                else -> ErroGeracao.Servidor(detalhe)
            }
        } catch (e: HttpRequestTimeoutException) {
            throw ErroGeracao.TempoEsgotado()
        } catch (e: HttpRequestException) {
            // O supabase-kt embrulha falhas de rede aqui, inclusive o timeout de leitura do socket.
            throw if (e.message.orEmpty().contains("timeout", ignoreCase = true)) {
                ErroGeracao.TempoEsgotado()
            } else {
                ErroGeracao.SemConexao()
            }
        }

        try {
            json.decodeFromString<AtividadeGerada>(resposta.bodyAsText())
        } catch (e: Exception) {
            throw ErroGeracao.Servidor("Resposta do servidor em formato inesperado.")
        }
    }

    /** Extrai `erro` de {"erro": "..."}; se o corpo não for esse JSON, devolve um texto genérico. */
    private fun mensagemDeErro(corpo: String): String =
        runCatching { json.parseToJsonElement(corpo).jsonObject["erro"]?.jsonPrimitive?.content }
            .getOrNull()
            ?: "O servidor não conseguiu gerar a atividade."
}
