package com.edutec.app.data

data class Turma(
    val nome: String,
    val codigo: String,
    val alunos: Int,
    /** Percentuais de conceitos em cada estado (somam 100, ou 0 se não houver dados). */
    val consolidado: Int,
    val desenvolvimento: Int,
    val revisar: Int,
    val conceitosARevisar: Int = 0,
    /** Status exibido quando não há conceitos a revisar, ex.: "1 em revisão". */
    val status: String? = null,
    val rodape: String,
)

data class AtividadePendente(
    val disciplina: String,
    val turma: String,
)

object DadosExemplo {
    val turmas = listOf(
        Turma(
            nome = "Cálculo I", codigo = "TURMA A", alunos = 38,
            consolidado = 45, desenvolvimento = 30, revisar = 25,
            conceitosARevisar = 2, rodape = "1 atividade aguarda revisão",
        ),
        Turma(
            nome = "Cálculo I", codigo = "TURMA B", alunos = 41,
            consolidado = 60, desenvolvimento = 28, revisar = 12,
            conceitosARevisar = 1, rodape = "Última atividade: há 2 dias",
        ),
        Turma(
            nome = "Álgebra Linear", codigo = "TURMA", alunos = 29,
            consolidado = 72, desenvolvimento = 22, revisar = 6,
            rodape = "Última atividade: há 5 dias",
        ),
        Turma(
            nome = "Estatística", codigo = "TURMA", alunos = 33,
            consolidado = 50, desenvolvimento = 40, revisar = 10,
            status = "1 em revisão", rodape = "Gerando 1 atividade",
        ),
        Turma(
            nome = "Geometria Analítica", codigo = "TURMA", alunos = 25,
            consolidado = 30, desenvolvimento = 45, revisar = 25,
            conceitosARevisar = 3, rodape = "Última atividade: há 9 dias",
        ),
    )

    val atividadesPendentes = listOf(
        AtividadePendente(disciplina = "Cálculo I", turma = "Turma A"),
    )

    val semestres = listOf("2026.2", "2026.1", "2025.2", "2025.1")
}
