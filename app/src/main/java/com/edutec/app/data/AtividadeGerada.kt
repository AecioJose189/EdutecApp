package com.edutec.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/** O que o professor pediu na tela Nova atividade (valores já no formato da Edge Function). */
data class PedidoGeracao(
    val arquivo: File,
    val nomeArquivo: String,
    val quantidade: Int,
    /** multipla_escolha | aberta | mista */
    val formato: String,
    /** facil | media | dificil */
    val dificuldade: String,
    /** true: gera de novo mesmo que o servidor já tenha um resultado para este PDF e parâmetros. */
    val forcarNova: Boolean = false,
)

// Espelho do JSON devolvido por supabase/functions/gerar-atividade.

@Serializable
data class AtividadeGerada(
    val conceitos: List<ConceitoGerado>,
    val questoes: List<QuestaoGerada>,
    val arquivo: String? = null,
    val modelo: String? = null,
    @SerialName("nivel_raciocinio") val nivelRaciocinio: String? = null,
    val tokens: TokensGeracao? = null,
    @SerialName("duracao_ms") val duracaoMs: Long? = null,
    /** true: o servidor devolveu o resultado salvo de uma geração anterior do mesmo PDF (RN12). */
    val reaproveitado: Boolean = false,
    /** Data da geração original, em ISO 8601 UTC (ex.: 2026-10-21T02:28:23.983Z). */
    @SerialName("gerado_em") val geradoEm: String? = null,
)

@Serializable
data class ConceitoGerado(
    val nome: String,
    val prerequisitos: List<String> = emptyList(),
)

@Serializable
data class QuestaoGerada(
    /** multipla_escolha | aberta */
    val tipo: String,
    val enunciado: String,
    val conceitos: List<String>,
    @SerialName("pagina_origem") val paginaOrigem: Int? = null,
    val alternativas: List<AlternativaGerada>? = null,
    val rubrica: String? = null,
) {
    val multiplaEscolha get() = tipo == "multipla_escolha"
}

@Serializable
data class AlternativaGerada(
    val texto: String,
    val correta: Boolean,
    val feedback: String,
)

@Serializable
data class TokensGeracao(
    val entrada: Int = 0,
    val saida: Int = 0,
    val raciocinio: Int = 0,
    val total: Int = 0,
)
