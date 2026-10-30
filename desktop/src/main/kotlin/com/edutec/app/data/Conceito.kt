package com.edutec.app.data

enum class EstadoConceito(val rotulo: String) {
    Revisar("Revisar"),
    Desenvolvimento("Em desenvolvimento"),
    Consolidado("Consolidado"),
}

data class Conceito(
    val nome: String,
    /** Chance estimada (0–100) de a turma saber o conceito; não é nota. */
    val dominio: Int,
    val estado: EstadoConceito,
)

data class Aluno(
    val nome: String,
    val estado: EstadoConceito,
)

object DadosTurmaExemplo {
    val conceitos = listOf(
        Conceito("Regra da cadeia", 42, EstadoConceito.Revisar),
        Conceito("Derivada implícita", 47, EstadoConceito.Revisar),
        Conceito("Derivada", 61, EstadoConceito.Desenvolvimento),
        Conceito("Continuidade", 66, EstadoConceito.Desenvolvimento),
        Conceito("Otimização", 55, EstadoConceito.Desenvolvimento),
        Conceito("Funções", 91, EstadoConceito.Consolidado),
        Conceito("Limites", 84, EstadoConceito.Consolidado),
    )

    val alunos = listOf(
        Aluno("Aluno 01", EstadoConceito.Revisar),
        Aluno("Aluno 02", EstadoConceito.Revisar),
        Aluno("Aluno 03", EstadoConceito.Revisar),
        Aluno("Aluno 04", EstadoConceito.Desenvolvimento),
        Aluno("Aluno 05", EstadoConceito.Desenvolvimento),
        Aluno("Aluno 06", EstadoConceito.Desenvolvimento),
        Aluno("Aluno 07", EstadoConceito.Consolidado),
    )

    /** Grafo da turma para os @Preview (no app o grafo vem da turma-api). */
    val grafo = GrafoTurma(
        conceitos = listOf(
            ConceitoGrafo("Funções", emptyList(), 38, 140, 0.91, 0.93, EstadoConceito.Consolidado, 1),
            ConceitoGrafo("Limites", listOf("Funções"), 38, 120, 0.84, 0.86, EstadoConceito.Consolidado, 3),
            ConceitoGrafo("Continuidade", listOf("Funções", "Limites"), 38, 60, 0.66, 0.70, EstadoConceito.Desenvolvimento, 9),
            ConceitoGrafo("Derivada", listOf("Limites"), 38, 130, 0.61, 0.64, EstadoConceito.Desenvolvimento, 12),
            ConceitoGrafo("Regra da cadeia", listOf("Derivada"), 38, 110, 0.42, 0.40, EstadoConceito.Revisar, 22),
            ConceitoGrafo("Otimização", listOf("Derivada"), 38, 40, 0.55, 0.52, EstadoConceito.Desenvolvimento, 15),
            ConceitoGrafo("Derivada implícita", listOf("Derivada", "Regra da cadeia"), 38, 50, 0.47, 0.45, EstadoConceito.Revisar, 20),
            ConceitoGrafo("Integral", listOf("Derivada")),
        ),
        alunos = alunos,
    )
}
