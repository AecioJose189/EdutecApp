package com.edutec.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Espelho do JSON da turma-api para convites, atividades publicadas e sessões em sala.

/** Convite da turma: o aluno abre [link] (<SITE_URL>/convite/<codigo>) e entra com o Google. */
@Serializable
data class Convite(val codigo: String, val link: String)

@Serializable
data class AtividadePublicada(
    val id: String,
    val titulo: String,
    @SerialName("publicada_em") val publicadaEm: String,
    @SerialName("n_questoes") val questoes: Int = 0,
    @SerialName("tempo_minutos") val tempoMinutos: Int? = null,
    @SerialName("sessao_aberta") val sessaoAberta: SessaoSala? = null,
    /** Ordem na turma (1, 2...), a mesma que o aluno vê; 0 = servidor antigo, sem o campo. */
    val numero: Int = 0,
    /** Sessões em sala já abertas para ela (abertas ou encerradas). */
    @SerialName("n_sessoes") val sessoes: Int = 0,
    /** Alunos que enviaram as respostas, somando todas as sessões. */
    @SerialName("n_enviadas") val enviadas: Int = 0,
    @SerialName("ultima_encerrada_em") val ultimaEncerradaEm: String? = null,
) {
    val estado: EstadoAtividade
        get() = when {
            sessaoAberta != null -> EstadoAtividade.SessaoAberta
            sessoes > 0 -> EstadoAtividade.Encerrada
            else -> EstadoAtividade.Publicada
        }
}

/** Selo da atividade na lista. Rascunho = gerada e ainda não publicada (só existe no app aberto). */
enum class EstadoAtividade(val rotulo: String) {
    Rascunho("Rascunho"),
    Publicada("Publicada"),
    SessaoAberta("Sessão aberta"),
    Encerrada("Encerrada"),
}

@Serializable
data class SessaoSala(
    val id: String,
    @SerialName("atividade_id") val atividadeId: String,
    val aberta: Boolean,
    @SerialName("entrada_aberta") val entradaAberta: Boolean,
    @SerialName("aberta_em") val abertaEm: String,
)

/** Conteúdo do QR: [url] = <SITE_URL>/s/<token>, válido até [expiraEm] (+ uma janela de 20 s). */
@Serializable
data class TokenQr(
    val token: String,
    val url: String,
    @SerialName("expira_em") val expiraEm: String,
    @SerialName("janela_segundos") val janelaSegundos: Int = 20,
)

@Serializable
data class PainelSessao(val sessao: SessaoSala, val participantes: List<Participante>)

@Serializable
data class Participante(
    @SerialName("tentativa_id") val tentativaId: String,
    val nome: String,
    @SerialName("iniciada_em") val iniciadaEm: String,
    @SerialName("enviada_em") val enviadaEm: String? = null,
    val acertos: Int? = null,
    @SerialName("total_multipla_escolha") val totalMultiplaEscolha: Int? = null,
    @SerialName("abertas_aguardando_correcao") val abertasAguardando: Int? = null,
) {
    val enviou get() = enviadaEm != null
}

/** Aluno matriculado (aceitou o convite). */
data class Membro(val nome: String, val email: String?, val entrouEm: String)
