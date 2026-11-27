package com.edutec.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximosPassosTest {
    private fun atividade(sessoes: Int = 0, aberta: Boolean = false) = AtividadePublicada(
        id = "a", titulo = "A", publicadaEm = "2026-10-28T12:00:00Z", sessoes = sessoes,
        sessaoAberta = if (aberta) SessaoSala("s", "a", true, true, "2026-10-28T12:00:00Z") else null,
    )

    private fun atual(etapas: List<EtapaPasso>) = etapas.single { it.situacao == SituacaoPasso.Atual }.passo

    @Test
    fun `turma nova comeca em convidar`() {
        val etapas = proximosPassos(0, false, emptyList(), false)
        assertEquals(Passo.Convidar, atual(etapas))
        assertEquals("ninguém entrou ainda", etapas.first().detalhe)
    }

    @Test
    fun `com alunos e sem atividade, criar`() {
        val etapas = proximosPassos(3, false, emptyList(), false)
        assertEquals(Passo.Criar, atual(etapas))
        assertEquals("3 alunos entraram", etapas.first().detalhe)
    }

    @Test
    fun `rascunho leva a publicar`() {
        assertEquals(Passo.Publicar, atual(proximosPassos(1, true, emptyList(), false)))
    }

    @Test
    fun `publicada sem sessao leva a abrir sessao`() {
        assertEquals(Passo.AbrirSessao, atual(proximosPassos(1, false, listOf(atividade()), false)))
    }

    @Test
    fun `sessao aberta sem respostas leva ao mapa`() {
        assertEquals(Passo.VerMapa, atual(proximosPassos(1, false, listOf(atividade(aberta = true)), false)))
    }

    @Test
    fun `publicar antes de convidar ainda pede o convite primeiro`() {
        val etapas = proximosPassos(0, false, listOf(atividade(sessoes = 1)), true)
        assertEquals(Passo.Convidar, atual(etapas))
        assertEquals(SituacaoPasso.Concluido, etapas.first { it.passo == Passo.Publicar }.situacao)
    }

    @Test
    fun `tudo feito`() {
        assertTrue(proximosPassos(2, false, listOf(atividade(sessoes = 1)), true).tudoFeito())
    }

    @Test
    fun `estado da atividade`() {
        assertEquals(EstadoAtividade.Publicada, atividade().estado)
        assertEquals(EstadoAtividade.SessaoAberta, atividade(sessoes = 1, aberta = true).estado)
        assertEquals(EstadoAtividade.Encerrada, atividade(sessoes = 2).estado)
    }
}
