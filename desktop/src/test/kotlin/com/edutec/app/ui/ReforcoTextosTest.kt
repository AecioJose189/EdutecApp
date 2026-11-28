package com.edutec.app.ui

import com.edutec.app.data.AlunoNoConceito
import com.edutec.app.data.AtividadeOrigem
import com.edutec.app.data.MaterialReforco
import com.edutec.app.data.PedidoGeracao
import com.edutec.app.data.TreinoConceito
import org.junit.Assert.assertEquals
import org.junit.Test

class ReforcoTextosTest {
    @Test
    fun `linha do aluno na lista de dificuldade`() {
        assertEquals("1 resposta · 1 erro com certeza · 0 acertos no chute · treinou 6 vezes (4 acertos)",
            detalhesAluno(AlunoNoConceito("a", "Ana", 0.2, "revisar", 1, errosComCerteza = 1, treino = TreinoConceito(6, 4))))
        assertEquals("0 respostas · 0 erros com certeza · 1 acerto no chute · não treinou com flashcards",
            detalhesAluno(AlunoNoConceito("b", "Bia", acertosNoChute = 1)))
    }

    @Test
    fun `aviso de pdfs diferentes`() {
        val m = MaterialReforco("Regra da cadeia", AtividadeOrigem("a2", 2, "Derivadas"), listOf("Derivada"))
        assertEquals("\"Derivada\" veio de outro PDF. A IA vai usar só o material de \"Regra da cadeia\" " +
            "(o conceito principal, o primeiro escolhido).", textoOutroMaterial(m))
        assertEquals(true, textoOutroMaterial(m.copy(deOutroMaterial = listOf("A", "B"))).startsWith("\"A\", \"B\" vieram"))
    }

    @Test
    fun `origem na revisao - pdf ou reforco`() {
        val pdf = PedidoGeracao(null, "aula.pdf", 5, "multipla_escolha", "media")
        assertEquals(" a partir de \"aula.pdf\"", origemDoPedido(pdf))
        val reforco = pdf.copy(nomeArquivo = "Atividade 2 · Derivadas", conceitosFoco = listOf("Regra da cadeia", "Derivada"))
        assertEquals(" de reforço em Regra da cadeia, Derivada, a partir do PDF da Atividade 2 · Derivadas", origemDoPedido(reforco))
    }
}
