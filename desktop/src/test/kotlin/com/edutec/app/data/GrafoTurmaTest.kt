package com.edutec.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrafoTurmaTest {
    private fun c(nome: String, media: Double?, vararg pres: String) = ConceitoGrafo(
        nome = nome,
        prerequisitos = pres.toList(),
        respostas = if (media == null) 0 else 10,
        media = media,
        estado = media?.let {
            when {
                it < 0.5 -> EstadoConceito.Revisar
                it < 0.8 -> EstadoConceito.Desenvolvimento
                else -> EstadoConceito.Consolidado
            }
        },
    )

    // Jogos (0,9) → Minimax (0,6) → Decisões (0,45) → Quiescência (0,3); Minimax → Poda (0,55)
    private val grafo = GrafoTurma(
        conceitos = listOf(
            c("Jogos", 0.9),
            c("Minimax", 0.6, "Jogos"),
            c("Poda", 0.55, "Minimax"),
            c("Decisões", 0.45, "Minimax"),
            c("Quiescência", 0.3, "Decisões"),
            c("Novo", null, "Quiescência"),
        ),
        alunos = emptyList(),
    )

    @Test
    fun causaRaizEhOPrerequisitoIndiretoComMenorDominio() {
        assertEquals("Decisões", grafo.causaRaiz("Quiescência")?.nome)
        assertEquals("Minimax", grafo.causaRaiz("Poda")?.nome)
        // "Novo" não tem dados, mas a causa raiz olha os pré-requisitos (direto: Quiescência).
        assertEquals("Quiescência", grafo.causaRaiz("Novo")?.nome)
    }

    @Test
    fun semCausaRaizQuandoOPiorPrerequisitoEstaConsolidado() {
        assertNull(grafo.causaRaiz("Minimax"))
        assertNull(grafo.causaRaiz("Jogos"))
    }

    @Test
    fun ancestraisDiretosEIndiretos() {
        assertEquals(setOf("Decisões", "Minimax", "Jogos"), grafo.ancestrais("Quiescência"))
    }

    @Test
    fun conceitoInicialEhOMaisFraco() {
        assertEquals("Quiescência", grafo.conceitoInicial()?.nome)
    }
}
