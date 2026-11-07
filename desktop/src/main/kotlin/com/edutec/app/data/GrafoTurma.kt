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

/**
 * Dois conceitos da turma com nomes parecidos mas não iguais (turma-api, pg_trgm): podem ser o mesmo
 * conceito com outro nome. Nunca são juntados sozinhos; o professor decide.
 */
data class PossivelDuplicata(val conceitoA: String, val conceitoB: String, val similaridade: Double) {
    fun outro(nome: String): String? = when (nome) {
        conceitoA -> conceitoB
        conceitoB -> conceitoA
        else -> null
    }
}

/** Um conceito que pode ser juntado com o do painel; [parecido] = está nas possíveis duplicatas. */
data class CandidatoJuntar(val conceito: ConceitoGrafo, val parecido: Boolean)

data class GrafoTurma(
    val conceitos: List<ConceitoGrafo>,
    /** Pseudônimos ("Aluno 01"...) com o pior estado de cada um, do pior para o melhor. */
    val alunos: List<Aluno>,
    /** Da mais parecida para a menos. */
    val possiveisDuplicatas: List<PossivelDuplicata> = emptyList(),
) {
    private val porNome = conceitos.associateBy { it.nome }

    /**
     * Conceitos que podem ser juntados com [nome], para "Juntar com outro conceito…": primeiro os
     * parecidos com ele (do mais parecido para o menos), depois os outros em ordem alfabética.
     */
    fun candidatosParaJuntar(nome: String): List<CandidatoJuntar> {
        val parecidos = possiveisDuplicatas.mapNotNull { it.outro(nome) }.distinct().mapNotNull { porNome[it] }
        val nomesParecidos = parecidos.map { it.nome }.toSet()
        val outros = conceitos
            .filter { it.nome != nome && it.nome !in nomesParecidos }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nome })
        return parecidos.map { CandidatoJuntar(it, true) } + outros.map { CandidatoJuntar(it, false) }
    }

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
