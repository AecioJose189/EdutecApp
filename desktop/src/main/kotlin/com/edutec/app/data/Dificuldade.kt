package com.edutec.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// Fase 5: "Ver alunos com dificuldade" (turma-api alunos_conceito) e a origem de cada conceito para a
// atividade de reforço (turma-api origem_conceitos).

@Serializable
data class AlunoNoConceito(
    @SerialName("aluno_id") val alunoId: String,
    val nome: String = "",
    /** Domínio estimado (0–1); null = sem respostas neste conceito. Não é nota. */
    @SerialName("p_dominio") val dominio: Double? = null,
    @SerialName("estado") val estadoTexto: String? = null,
    @SerialName("n_respostas") val respostas: Int = 0,
    @SerialName("erros_com_certeza") val errosComCerteza: Int = 0,
    @SerialName("acertos_no_chute") val acertosNoChute: Int = 0,
    val treino: TreinoConceito = TreinoConceito(),
) {
    val estado: EstadoConceito? get() = estadoDoServidor(estadoTexto)

    /** Mesmo limite do mapa do aluno: abaixo de 3 respostas, "poucos dados". */
    val poucosDados: Boolean get() = dominio != null && respostas < ConceitoGrafo.MINIMO_RESPOSTAS_ALUNO
}

@Serializable
data class AlunosDoConceito(
    val conceito: String,
    val alunos: List<AlunoNoConceito> = emptyList(),
)

/** Alunos de um conceito separados para a lista: com dificuldade primeiro; o resto fica recolhido. */
data class GruposDificuldade(
    val revisar: List<AlunoNoConceito>,
    val desenvolvimento: List<AlunoNoConceito>,
    /** Consolidados e sem dados ("Ver todos"). */
    val demais: List<AlunoNoConceito>,
) {
    val ninguemComDificuldade get() = revisar.isEmpty() && desenvolvimento.isEmpty()
}

/** Mantém a ordem do servidor (piores primeiro, sem dados no fim). */
fun AlunosDoConceito.agrupar(): GruposDificuldade = GruposDificuldade(
    revisar = alunos.filter { it.estado == EstadoConceito.Revisar },
    desenvolvimento = alunos.filter { it.estado == EstadoConceito.Desenvolvimento },
    demais = alunos.filter { it.estado != EstadoConceito.Revisar && it.estado != EstadoConceito.Desenvolvimento },
)

// ---------------------------------------------------------------------------
// Origem dos conceitos (atividade de reforço)
// ---------------------------------------------------------------------------

@Serializable
data class AtividadeOrigem(val id: String, val numero: Int = 0, val titulo: String = "")

@Serializable
data class OrigemConceito(
    val conceito: String,
    /** Identifica o PDF (hash): mesmo material ⇔ mesmo PDF. null = origem desconhecida. */
    val material: String? = null,
    val atividade: AtividadeOrigem? = null,
)

/** De onde vem o PDF do reforço: o do conceito principal (o 1º escolhido). */
data class MaterialReforco(
    val principal: String,
    /** null = não há material de origem para o principal (não dá para gerar). */
    val atividade: AtividadeOrigem?,
    /** Escolhidos que vieram de outro PDF: a IA usa só o do principal. */
    val deOutroMaterial: List<String>,
)

/** Máximo de conceitos num reforço (o mesmo da gerar-atividade). */
const val MAX_CONCEITOS_REFORCO = 8

fun materialDoReforco(escolhidos: List<String>, origens: List<OrigemConceito>): MaterialReforco? {
    val principal = escolhidos.firstOrNull() ?: return null
    val porNome = origens.associateBy { it.conceito }
    val dele = porNome[principal]
    return MaterialReforco(
        principal = principal,
        atividade = dele?.atividade?.takeIf { dele.material != null },
        deOutroMaterial = escolhidos.drop(1).filter { porNome[it]?.material != dele?.material },
    )
}

/** "Atividade 2 · Probabilidade" (ou só o título, se não houver número). */
fun AtividadeOrigem.rotulo(): String = if (numero > 0) "Atividade $numero · $titulo" else titulo

/** Título sugerido do reforço: "Reforço: A, B e C". */
fun tituloReforco(escolhidos: List<String>): String {
    val nomes = when (escolhidos.size) {
        0 -> ""
        1 -> escolhidos[0]
        else -> escolhidos.dropLast(1).joinToString(", ") + " e " + escolhidos.last()
    }
    return "Reforço: $nomes".take(200)
}

/** Conceitos do mapa da turma para a Nova atividade (domínio estimado em %, estado; null = sem dados). */
fun GrafoTurma.conceitosParaReforco(): List<Conceito> = conceitos.map { c ->
    Conceito(c.nome, ((c.media ?: 0.0) * 100).roundToInt(), c.estado.takeUnless { c.semDados })
}
