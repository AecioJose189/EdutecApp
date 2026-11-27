package com.edutec.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Espelho do JSON da turma-api: estatisticas (UC13), mapa_aluno (UC11), historico_conceito (UC12) e,
// na fase 4, o treino com flashcards fora de sala (treino_turma; vem também dentro de estatisticas).
// Domínio estimado sempre de 0 a 1: chance estimada de saber o conceito; não é nota.

@Serializable
data class EstatisticasTurma(
    @SerialName("tem_simulacao") val temSimulacao: Boolean = false,
    @SerialName("incluir_simulacao") val incluirSimulacao: Boolean = false,
    val resumo: ResumoTurma,
    val conceitos: List<EstatisticaConceito> = emptyList(),
    val evolucao: List<PontoEvolucao> = emptyList(),
    val confianca: ConfiancaTurma = ConfiancaTurma(),
    /** Fase 4. Servidor anterior não manda: fica vazio (seção mostra "ninguém treinou"). */
    val treino: TreinoTurma = TreinoTurma(),
)

// ---------------------------------------------------------------------------
// Treino fora de sala (flashcards, fase 4). NÃO altera o domínio estimado.
// ---------------------------------------------------------------------------

@Serializable
data class ResumoTreino(
    @SerialName("n_alunos") val alunos: Int = 0,
    /** Alunos com ao menos um treino nos últimos 7 dias. */
    @SerialName("n_alunos_treinando") val alunosTreinando: Int = 0,
    @SerialName("treinos_7_dias") val treinos7Dias: Int = 0,
    @SerialName("n_treinos") val treinos: Int = 0,
    /** Acerto em todos os treinos (0–1); null sem treinos. */
    @SerialName("taxa_acerto") val taxaAcerto: Double? = null,
)

@Serializable
data class CartoesPorCaixa(
    @SerialName("1") val caixa1: Int = 0,
    @SerialName("2") val caixa2: Int = 0,
    @SerialName("3") val caixa3: Int = 0,
)

@Serializable
data class TreinoAluno(
    @SerialName("aluno_id") val alunoId: String,
    val nome: String = "",
    @SerialName("treinos_7_dias") val treinos7Dias: Int = 0,
    @SerialName("n_treinos") val treinos: Int = 0,
    val acertos: Int = 0,
    @SerialName("taxa_acerto") val taxaAcerto: Double? = null,
    @SerialName("cartoes_por_caixa") val caixas: CartoesPorCaixa = CartoesPorCaixa(),
    @SerialName("ultimo_treino_em") val ultimoTreinoEm: String? = null,
)

@Serializable
data class TreinoConceitoTurma(
    val conceito: String,
    @SerialName("n_alunos_treinando") val alunosTreinando: Int = 0,
    @SerialName("treinos_7_dias") val treinos7Dias: Int = 0,
)

@Serializable
data class TreinoTurma(
    val resumo: ResumoTreino = ResumoTreino(),
    val alunos: List<TreinoAluno> = emptyList(),
    val conceitos: List<TreinoConceitoTurma> = emptyList(),
)

/** Treinos de um aluno num conceito (no histórico do mapa do aluno). */
@Serializable
data class TreinoConceito(
    @SerialName("n_treinos") val treinos: Int = 0,
    val acertos: Int = 0,
)

/** Só quem já treinou, de quem treinou mais nos últimos 7 dias para quem treinou menos. */
fun TreinoTurma.alunosQueTreinaram(): List<TreinoAluno> =
    alunos.filter { it.treinos > 0 }.sortedWith(compareByDescending<TreinoAluno> { it.treinos7Dias }.thenByDescending { it.treinos }.thenBy { it.nome })

/** "Treinou 6 vezes (4 acertos)"; sem treinos, avisa que ainda não treinou. */
fun textoTreinoConceito(t: TreinoConceito): String = when {
    t.treinos == 0 -> "Ainda não treinou este conceito com flashcards."
    else -> "Treinou ${t.treinos} ${if (t.treinos == 1) "vez" else "vezes"} (" +
        when (t.acertos) { 0 -> "nenhum acerto"; 1 -> "1 acerto"; else -> "${t.acertos} acertos" } + ")"
}

@Serializable
data class ResumoTurma(
    @SerialName("n_alunos") val alunos: Int = 0,
    @SerialName("n_atividades") val atividades: Int = 0,
    @SerialName("n_atividades_encerradas") val atividadesEncerradas: Int = 0,
    @SerialName("n_respostas") val respostas: Int = 0,
    @SerialName("media_dominio") val media: Double? = null,
    @SerialName("mediana_dominio") val mediana: Double? = null,
    @SerialName("estado_moda") val estadoModa: String? = null,
)

@Serializable
data class AlunosPorEstado(
    val consolidado: Int = 0,
    @SerialName("em_desenvolvimento") val desenvolvimento: Int = 0,
    val revisar: Int = 0,
    @SerialName("sem_dados") val semDados: Int = 0,
) {
    val total get() = consolidado + desenvolvimento + revisar + semDados
}

@Serializable
data class EstatisticaConceito(
    val conceito: String,
    @SerialName("media_dominio") val media: Double? = null,
    @SerialName("mediana_dominio") val mediana: Double? = null,
    @SerialName("estado_moda") val estadoModa: String? = null,
    @SerialName("n_respostas") val respostas: Int = 0,
    @SerialName("alunos_por_estado") val alunosPorEstado: AlunosPorEstado = AlunosPorEstado(),
)

@Serializable
data class AtividadeRef(val id: String, val numero: Int = 0, val titulo: String = "")

@Serializable
data class ConceitoEvolucao(
    val conceito: String,
    @SerialName("media_dominio") val media: Double? = null,
    @SerialName("media_anterior") val mediaAnterior: Double? = null,
    val estado: String? = null,
) {
    /** Variação desde a atividade anterior (null = conceito sem dado antes). */
    val variacao: Double? get() = if (media != null && mediaAnterior != null) media - mediaAnterior else null
}

@Serializable
data class PontoEvolucao(
    val atividade: AtividadeRef,
    @SerialName("encerrada_em") val encerradaEm: String? = null,
    @SerialName("n_enviadas") val enviadas: Int = 0,
    /** Acertos / questões de múltipla escolha das tentativas enviadas (0–1). */
    @SerialName("taxa_acerto") val taxaAcerto: Double? = null,
    /** Domínio estimado médio logo depois da atividade (BKT refeito até o fim da sessão). */
    @SerialName("media_dominio") val media: Double? = null,
    val conceitos: List<ConceitoEvolucao> = emptyList(),
)

@Serializable
data class ContagemConfianca(
    val conceito: String? = null,
    @SerialName("aluno_id") val alunoId: String? = null,
    val nome: String? = null,
    @SerialName("n_respostas") val respostas: Int = 0,
    @SerialName("n_com_confianca") val comConfianca: Int = 0,
    @SerialName("erros_com_certeza") val errosComCerteza: Int = 0,
    @SerialName("acertos_no_chute") val acertosNoChute: Int = 0,
)

@Serializable
data class ConfiancaTurma(
    val turma: ContagemConfianca = ContagemConfianca(),
    val conceitos: List<ContagemConfianca> = emptyList(),
    val alunos: List<ContagemConfianca> = emptyList(),
)

@Serializable
data class DominioAlunoConceito(
    val conceito: String,
    @SerialName("p_dominio") val dominio: Double? = null,
    val estado: String? = null,
    @SerialName("n_respostas") val respostas: Int = 0,
)

@Serializable
data class MapaAluno(val conceitos: List<DominioAlunoConceito> = emptyList())

@Serializable
data class RespostaHistorico(
    @SerialName("criado_em") val criadoEm: String,
    /** null = resposta da simulação (sem sessão). */
    val atividade: AtividadeRef? = null,
    val acertou: Boolean,
    val confianca: String? = null,
    /** Domínio estimado logo depois desta resposta. */
    @SerialName("p_dominio") val dominio: Double,
)

@Serializable
data class HistoricoConceito(
    val conceito: String,
    @SerialName("p_inicial") val dominioInicial: Double = 0.3,
    val respostas: List<RespostaHistorico> = emptyList(),
    @SerialName("erros_com_certeza") val errosComCerteza: Int = 0,
    @SerialName("acertos_no_chute") val acertosNoChute: Int = 0,
    /** Fase 4: flashcards deste aluno neste conceito (servidor anterior: zeros). */
    val treino: TreinoConceito = TreinoConceito(),
)

// ---------------------------------------------------------------------------
// Regras da tela (puras, com testes)
// ---------------------------------------------------------------------------

const val MINIMO_ALUNOS = 5
const val MINIMO_ATIVIDADES_ENCERRADAS = 2
const val MINIMO_RESPOSTAS_COM_CONFIANCA = 5

/** Por que os números ainda são frágeis. Vazio = amostra suficiente. Os números aparecem do mesmo jeito. */
fun avisosPoucosDados(e: EstatisticasTurma): List<String> = buildList {
    val r = e.resumo
    if (r.alunos < MINIMO_ALUNOS) {
        add("${r.alunos} ${if (r.alunos == 1) "aluno" else "alunos"} (menos de $MINIMO_ALUNOS): a média muda muito com um só aluno.")
    }
    if (r.atividadesEncerradas < MINIMO_ATIVIDADES_ENCERRADAS) {
        add("${r.atividadesEncerradas} ${if (r.atividadesEncerradas == 1) "atividade encerrada" else "atividades encerradas"} " +
            "(menos de $MINIMO_ATIVIDADES_ENCERRADAS): ainda não dá para ver tendência na evolução.")
    }
    val conf = e.confianca.turma.comConfianca
    if (conf < MINIMO_RESPOSTAS_COM_CONFIANCA) {
        add("$conf ${if (conf == 1) "resposta" else "respostas"} com confiança informada (menos de $MINIMO_RESPOSTAS_COM_CONFIANCA): " +
            "os erros com certeza e os acertos no chute ainda dizem pouco.")
    }
}

/** Destaques de uma atividade: os que mais subiram, os que caíram e os que estão em "revisar". */
data class DestaquesAtividade(
    val subiram: List<ConceitoEvolucao>,
    val cairam: List<ConceitoEvolucao>,
    val emRevisar: List<ConceitoEvolucao>,
)

fun destaques(ponto: PontoEvolucao, maximo: Int = 3): DestaquesAtividade = DestaquesAtividade(
    subiram = ponto.conceitos.filter { (it.variacao ?: 0.0) > 0.005 }.sortedByDescending { it.variacao }.take(maximo),
    cairam = ponto.conceitos.filter { (it.variacao ?: 0.0) < -0.005 }.sortedBy { it.variacao },
    emRevisar = ponto.conceitos.filter { it.estado == "revisar" }.sortedBy { it.media },
)

/** Confiança de um conceito (contagem zerada se ninguém respondeu com confiança). */
fun ConfiancaTurma.doConceito(nome: String): ContagemConfianca =
    conceitos.find { it.conceito == nome } ?: ContagemConfianca(conceito = nome)

/** Mapa da turma repintado com o domínio estimado de um aluno: mesmos nós e setas, números dele. */
fun GrafoTurma.doAluno(mapa: MapaAluno): GrafoTurma {
    val dele = mapa.conceitos.associateBy { it.conceito }
    return copy(
        conceitos = conceitos.map { c ->
            val d = dele[c.nome]
            ConceitoGrafo(
                nome = c.nome,
                prerequisitos = c.prerequisitos,
                alunos = if (d?.dominio != null) 1 else 0,
                respostas = d?.respostas ?: 0,
                media = d?.dominio,
                mediana = d?.dominio,
                estado = estadoDoServidor(d?.estado),
                alunosRevisar = if (d?.estado == "revisar") 1 else 0,
                minimoRespostas = ConceitoGrafo.MINIMO_RESPOSTAS_ALUNO,
            )
        },
        possiveisDuplicatas = emptyList(),
    )
}

/** "certeza" → "Certeza"; null quando o aluno não informou. */
fun rotuloConfianca(valor: String?): String? = when (valor) {
    "certeza" -> "Certeza"
    "acho" -> "Acho que sei"
    "chute" -> "Chute"
    else -> null
}

/** Mesmos limiares (propostos) de public.estado_dominio: 0,5 e 0,8. */
fun estadoDominioLocal(p: Double): EstadoConceito = when {
    p < 0.5 -> EstadoConceito.Revisar
    p < 0.8 -> EstadoConceito.Desenvolvimento
    else -> EstadoConceito.Consolidado
}
