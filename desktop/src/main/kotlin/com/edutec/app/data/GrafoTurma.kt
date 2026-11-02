package com.edutec.app.data

/** Conceito do grafo da turma com o resumo do domínio estimado (vem da turma-api, ação grafo). */
data class ConceitoGrafo(
    val nome: String,
    val prerequisitos: List<String> = emptyList(),
    val alunos: Int = 0,
    val respostas: Int = 0,
    /** Domínio estimado médio da turma (0–1); null = ainda sem respostas. Não é nota. */
    val media: Double? = null,
    val mediana: Double? = null,
    /** Estado pela média da turma; null = sem dados (?). */
    val estado: EstadoConceito? = null,
    /** Alunos com o conceito em "revisar". */
    val alunosRevisar: Int = 0,
) {
    val semDados get() = estado == null || respostas == 0
}

data class GrafoTurma(
    val conceitos: List<ConceitoGrafo>,
    /** Pseudônimos ("Aluno 01"...) com o pior estado de cada um, do pior para o melhor. */
    val alunos: List<Aluno>,
) {
    private val porNome = conceitos.associateBy { it.nome }

    fun conceito(nome: String): ConceitoGrafo? = porNome[nome]

    /** Pré-requisitos diretos e indiretos de [nome] (sem repetir, sem o próprio conceito). */
    fun ancestrais(nome: String): Set<String> {
        val vistos = linkedSetOf<String>()
        val pendentes = ArrayDeque(porNome[nome]?.prerequisitos.orEmpty())
        while (pendentes.isNotEmpty()) {
            val atual = pendentes.removeFirst()
            if (atual == nome || !vistos.add(atual)) continue
            pendentes.addAll(porNome[atual]?.prerequisitos.orEmpty())
        }
        return vistos
    }

    /**
     * "Causa raiz provável" de [nome]: o pré-requisito (direto ou indireto) com menor domínio
     * estimado médio, se ele não estiver consolidado. Pré-requisitos sem respostas não entram.
     */
    fun causaRaiz(nome: String): ConceitoGrafo? =
        ancestrais(nome)
            .mapNotNull { porNome[it] }
            .filter { !it.semDados && it.media != null }
            .minByOrNull { it.media!! }
            ?.takeIf { it.estado != EstadoConceito.Consolidado }

    /** Conceito para abrir o painel: o de menor domínio entre os que têm dados, ou o primeiro. */
    fun conceitoInicial(): ConceitoGrafo? =
        conceitos.filter { !it.semDados && it.media != null }.minByOrNull { it.media!! } ?: conceitos.firstOrNull()
}

/** "revisar" | "em_desenvolvimento" | "consolidado" (Postgres) → estado do app; outro valor → sem dados. */
fun estadoDoServidor(valor: String?): EstadoConceito? = when (valor) {
    "revisar" -> EstadoConceito.Revisar
    "em_desenvolvimento" -> EstadoConceito.Desenvolvimento
    "consolidado" -> EstadoConceito.Consolidado
    else -> null
}
