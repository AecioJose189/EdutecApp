package com.edutec.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutGrafoTest {
    @Test
    fun camadaEhOMaiorCaminhoDePrerequisitos() {
        // D depende de A (direto) e de C (A → B → C): fica na camada 3, não na 1.
        val grafo = mapOf(
            "A" to emptyList(),
            "B" to listOf("A"),
            "C" to listOf("B"),
            "D" to listOf("A", "C"),
        )
        val layout = layoutEmCamadas(grafo)
        assertEquals(listOf(0, 1, 2, 3), listOf("A", "B", "C", "D").map { layout.getValue(it).camada })
        assertEquals(4, layout.getValue("A").totalCamadas)
    }

    @Test
    fun camadasVaoDaEsquerdaParaADireita() {
        val layout = layoutEmCamadas(mapOf("Base" to emptyList(), "Topo" to listOf("Base")))
        assertTrue(layout.getValue("Base").x < layout.getValue("Topo").x)
        assertEquals(0.5f, layout.getValue("Base").y)
    }

    @Test
    fun baricentroDesfazCruzamentos() {
        // Na ordem de entrada, X2 depende de A1 e X1 de A2: as setas se cruzam. O layout troca a ordem.
        val grafo = mapOf(
            "A1" to emptyList(),
            "A2" to emptyList(),
            "X1" to listOf("A2"),
            "X2" to listOf("A1"),
        )
        val layout = layoutEmCamadas(grafo)
        assertEquals(0, cruzamentos(grafo, layout))
    }

    @Test
    fun grafoDoTestePdfSemCruzamentos() {
        val grafo = mapOf(
            "Jogos de Soma Zero" to emptyList(),
            "Algoritmo Minimax" to listOf("Jogos de Soma Zero"),
            "Poda Alfa-Beta" to listOf("Algoritmo Minimax"),
            "Decisões em Tempo Real" to listOf("Algoritmo Minimax"),
            "Quiescência" to listOf("Decisões em Tempo Real"),
        )
        val layout = layoutEmCamadas(grafo)
        assertEquals(0, cruzamentos(grafo, layout))
        assertEquals(2, layout.getValue("Poda Alfa-Beta").tamanhoCamada)
        assertEquals(3, layout.getValue("Quiescência").camada)
    }

    @Test
    fun cicloEPrerequisitoDesconhecidoNaoTravam() {
        val layout = layoutEmCamadas(mapOf("A" to listOf("B", "Fora"), "B" to listOf("A")))
        assertEquals(setOf("A", "B"), layout.keys)
    }

    @Test
    fun vazioDaVazio() {
        assertTrue(layoutEmCamadas(emptyMap()).isEmpty())
    }
}
