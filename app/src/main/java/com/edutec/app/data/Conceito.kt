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
        Aluno("Aluno A", EstadoConceito.Revisar),
        Aluno("Aluno B", EstadoConceito.Revisar),
        Aluno("Aluno C", EstadoConceito.Revisar),
        Aluno("Aluno D", EstadoConceito.Desenvolvimento),
        Aluno("Aluno E", EstadoConceito.Desenvolvimento),
        Aluno("Aluno F", EstadoConceito.Desenvolvimento),
        Aluno("Aluno G", EstadoConceito.Consolidado),
    )
}
