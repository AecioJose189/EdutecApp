package com.edutec.app.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Falha ao falar com a turma-api; [message] já é um texto para mostrar ao professor. */
class ErroTurmaApi(mensagem: String) : Exception(mensagem)

/**
 * Acesso à Edge Function turma-api (Supabase) com o JWT do professor logado: turmas, convites,
 * publicação, sessões em sala, grafo e simulação. Membros da turma vêm direto da tabela (RLS).
 * HTTP 401 → [SessaoExpirada] (e a sessão local é apagada: o app volta para a tela de entrada).
 */
object TurmaRepositorio {
    private const val FUNCAO = "turma-api"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listarTurmas(): List<Turma> =
        chamar<RespostaTurmas>("listar_turmas").turmas.map { it.paraTurma() }

    suspend fun criarTurma(nome: String, codigo: String): Turma =
        chamar<RespostaTurma>("criar_turma", "nome" to nome, "codigo" to codigo).turma.paraTurma()

    /** Publica a geração (já revisada pelo professor) na turma; devolve o id da atividade. */
    suspend fun publicar(
        turmaId: String, geracaoId: Long, permitirRefazer: Boolean, tempoMinutos: Int?, titulo: String? = null,
    ): String =
        chamar<RespostaPublicar>(
            "publicar",
            "turma_id" to turmaId, "geracao_id" to geracaoId,
            "permitir_refazer" to permitirRefazer, "tempo_minutos" to tempoMinutos,
            "titulo" to titulo?.trim()?.ifEmpty { null },
        ).atividadeId

    /** Convite ativo da turma (cria um se não houver); [novo] troca o código. */
    suspend fun convite(turmaId: String, novo: Boolean = false): Convite =
        chamar<RespostaConvite>("criar_convite", "turma_id" to turmaId, "novo" to novo).convite

    suspend fun atividades(turmaId: String): List<AtividadePublicada> =
        chamar<RespostaAtividades>("listar_atividades", "turma_id" to turmaId).atividades

    /** Abre a sessão em sala da atividade (ou devolve a que já está aberta). */
    suspend fun abrirSessao(atividadeId: String): SessaoSala =
        chamar<RespostaSessao>("abrir_sessao", "atividade_id" to atividadeId).sessao

    suspend fun tokenQr(sessaoId: String): TokenQr = chamar("token_qr", "sessao_id" to sessaoId)

    suspend fun encerrarEntrada(sessaoId: String): SessaoSala =
        chamar<RespostaSessao>("encerrar_entrada", "sessao_id" to sessaoId).sessao

    suspend fun encerrarSessao(sessaoId: String): SessaoSala =
        chamar<RespostaSessao>("encerrar_sessao", "sessao_id" to sessaoId).sessao

    /** A sessão e quem entrou nela (com quem já enviou). */
    suspend fun participantes(sessaoId: String): PainelSessao = chamar("sessao", "sessao_id" to sessaoId)

    /** Alunos da turma com nome e e-mail, lidos direto das tabelas (o RLS só mostra os da turma do professor). */
    suspend fun membros(turmaId: String): List<Membro> = withContext(Dispatchers.IO) {
        try {
            Supabase.cliente.from("membros_turma")
                .select(Columns.raw("entrou_em, aluno:perfis!membros_turma_aluno_id_fkey(nome, email)")) {
                    filter { eq("turma_id", turmaId) }
                    order("entrou_em", Order.ASCENDING)
                }
                .decodeList<MembroApi>()
                .map { Membro(it.aluno?.nome ?: "Aluno", it.aluno?.email, it.entrouEm) }
        } catch (e: RestException) {
            if (e.statusCode == 401) { Login.sessaoExpirou(); throw SessaoExpirada() }
            throw ErroTurmaApi("Não foi possível carregar os alunos da turma.")
        } catch (e: HttpRequestException) {
            throw ErroTurmaApi("Sem conexão com o servidor. Verifique a internet e tente de novo.")
        }
    }

    /** Conceitos da turma com o resumo do domínio estimado, e os alunos (pseudônimos). */
    suspend fun grafo(turmaId: String): GrafoTurma =
        chamar<RespostaGrafo>("grafo", "turma_id" to turmaId).paraGrafo()

    /** DEMONSTRAÇÃO: simula 25 alunos (pseudônimos) respondendo; substitui a simulação anterior da turma. */
    suspend fun simular(turmaId: String) {
        chamar<RespostaSimular>("simular", "turma_id" to turmaId)
    }

    /** POST { acao, ...parametros } → corpo JSON decodificado como [T]. */
    private suspend inline fun <reified T> chamar(acao: String, vararg parametros: Pair<String, Any?>): T =
        withContext(Dispatchers.IO) {
            val corpo = JsonObject(
                mapOf("acao" to JsonPrimitive(acao)) + parametros.associate { (chave, valor) ->
                    chave to when (valor) {
                        null -> kotlinx.serialization.json.JsonNull
                        is Number -> JsonPrimitive(valor)
                        is Boolean -> JsonPrimitive(valor)
                        else -> JsonPrimitive(valor.toString())
                    }
                }
            )
            val resposta = try {
                Supabase.cliente.functions.invoke(FUNCAO) {
                    setBody(TextContent(corpo.toString(), ContentType.Application.Json))
                }
            } catch (e: RestException) {
                // Atenção: e.message inclui os cabeçalhos da requisição (com a chave); não exibir nem logar.
                if (e.statusCode == 401) {
                    Login.sessaoExpirou()
                    throw SessaoExpirada()
                }
                throw ErroTurmaApi(mensagemDeErro(e.error))
            } catch (e: HttpRequestTimeoutException) {
                throw ErroTurmaApi("O servidor demorou demais para responder. Tente de novo.")
            } catch (e: HttpRequestException) {
                throw ErroTurmaApi("Sem conexão com o servidor. Verifique a internet e tente de novo.")
            }
            try {
                json.decodeFromJsonElement<T>(json.parseToJsonElement(resposta.bodyAsText()))
            } catch (e: Exception) {
                throw ErroTurmaApi("Resposta do servidor em formato inesperado.")
            }
        }

    /** Extrai `erro` de {"erro": "..."}; se o corpo não for esse JSON, devolve um texto genérico. */
    private fun mensagemDeErro(corpo: String): String =
        runCatching { json.parseToJsonElement(corpo).jsonObject["erro"]?.jsonPrimitive?.content }
            .getOrNull()
            ?: "O servidor não conseguiu atender o pedido."
}

// Espelho do JSON devolvido por supabase/functions/turma-api.

@Serializable
private data class RespostaTurmas(val turmas: List<TurmaApi>)

@Serializable
private data class RespostaTurma(val turma: TurmaApi)

@Serializable
private data class RespostaPublicar(@SerialName("atividade_id") val atividadeId: String)

@Serializable
private data class RespostaConvite(val convite: Convite)

@Serializable
private data class RespostaAtividades(val atividades: List<AtividadePublicada>)

@Serializable
private data class RespostaSessao(val sessao: SessaoSala)

@Serializable
private data class MembroApi(
    @SerialName("entrou_em") val entrouEm: String,
    val aluno: PerfilApi? = null,
)

@Serializable
private data class PerfilApi(val nome: String, val email: String? = null)

@Serializable
private data class RespostaSimular(@SerialName("n_respostas") val respostas: Int)

@Serializable
private data class RespostaGrafo(
    val conceitos: List<ConceitoApi>,
    val alunos: List<AlunoApi>,
) {
    fun paraGrafo() = GrafoTurma(
        conceitos = conceitos.map {
            ConceitoGrafo(
                nome = it.nome,
                prerequisitos = it.prerequisitos,
                alunos = it.alunos,
                respostas = it.respostas,
                media = it.media,
                mediana = it.mediana,
                estado = estadoDoServidor(it.estado),
                alunosRevisar = it.alunosRevisar,
            )
        },
        alunos = alunos.mapNotNull { a -> estadoDoServidor(a.piorEstado)?.let { Aluno(a.rotulo, it) } },
    )
}

@Serializable
private data class ConceitoApi(
    val nome: String,
    val prerequisitos: List<String> = emptyList(),
    @SerialName("n_alunos") val alunos: Int = 0,
    @SerialName("n_respostas") val respostas: Int = 0,
    @SerialName("media_dominio") val media: Double? = null,
    @SerialName("mediana_dominio") val mediana: Double? = null,
    @SerialName("estado_da_media") val estado: String? = null,
    @SerialName("n_revisar") val alunosRevisar: Int = 0,
)

@Serializable
private data class AlunoApi(
    val rotulo: String,
    @SerialName("pior_estado") val piorEstado: String,
)

@Serializable
private data class TurmaApi(
    val id: String,
    val nome: String,
    val codigo: String,
    @SerialName("n_alunos") val alunos: Int = 0,
    @SerialName("n_atividades") val atividades: Int = 0,
    @SerialName("pct_consolidado") val consolidado: Int = 0,
    @SerialName("pct_desenvolvimento") val desenvolvimento: Int = 0,
    @SerialName("pct_revisar") val revisar: Int = 0,
    @SerialName("conceitos_a_revisar") val conceitosARevisar: Int = 0,
) {
    fun paraTurma() = Turma(
        id = id,
        nome = nome,
        codigo = codigo,
        alunos = alunos,
        consolidado = consolidado,
        desenvolvimento = desenvolvimento,
        revisar = revisar,
        conceitosARevisar = conceitosARevisar,
        atividades = atividades,
        rodape = when (atividades) {
            0 -> "Nenhuma atividade ainda"
            1 -> "1 atividade publicada"
            else -> "$atividades atividades publicadas"
        },
    )
}
