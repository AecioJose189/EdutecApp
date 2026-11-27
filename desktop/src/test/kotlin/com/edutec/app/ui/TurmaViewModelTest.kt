package com.edutec.app.ui

import com.edutec.app.data.Aluno
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.DominioAlunoConceito
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.EstatisticasTurma
import com.edutec.app.data.HistoricoConceito
import com.edutec.app.data.MapaAluno
import com.edutec.app.data.ResumoTurma
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.Membro
import com.edutec.app.data.SessaoSala
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O ViewModel roda no Dispatchers.Main (EDT do Swing); os testes esperam a condição com um prazo. */
class TurmaViewModelTest {
    private val atividade = AtividadePublicada("a1", "Probabilidade", "2026-10-28T12:00:00Z", numero = 1)

    private fun viewModel(membros: List<Membro>, atividades: List<AtividadePublicada> = listOf(atividade)) = TurmaViewModel(
        buscarGrafo = { GrafoTurma(emptyList(), emptyList()) },
        simularRespostas = {},
        buscarAtividades = { atividades },
        buscarMembros = { membros },
        buscarConvite = { _, _ -> error("sem convite no teste") },
        fundirConceitos = { _, _, _ -> error("sem fusão no teste") },
        marcarDistintos = { _, _, _ -> },
        buscarEstatisticas = { _, incluir -> pedidosEstatisticas += incluir; EstatisticasTurma(resumo = ResumoTurma(alunos = if (incluir) 28 else 3)) },
        buscarMapaAluno = { _, chave -> MapaAluno(listOf(DominioAlunoConceito("Base", if (chave == "ana") 0.9 else null))) },
        buscarHistorico = { _, _, conceito -> HistoricoConceito(conceito) },
    )

    private val pedidosEstatisticas = mutableListOf<Boolean>()

    private fun esperar(condicao: () -> Boolean) = runBlocking {
        withTimeout(3_000) { while (!condicao()) delay(10) }
    }

    @Test
    fun `turma sem alunos avisa antes de abrir, com a atividade guardada`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        var aberta: AtividadePublicada? = null
        vm.abrirSessao(atividade) { aberta = it }
        esperar { vm.sessaoSemAlunos != null }
        assertEquals(atividade, vm.sessaoSemAlunos) // "Abrir mesmo assim" abre esta
        assertNull(aberta)
    }

    @Test
    fun `com aluno abre a sessao`() {
        val vm = viewModel(membros = listOf(Membro("Ana", null, "2026-10-28T12:00:00Z")))
        vm.abrir("t1")
        var aberta: AtividadePublicada? = null
        vm.abrirSessao(atividade) { aberta = it }
        esperar { aberta != null }
        assertNull(vm.sessaoSemAlunos)
    }

    @Test
    fun `sessao ja aberta volta direto, sem conferir`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        val emAndamento = atividade.copy(sessaoAberta = SessaoSala("s1", "a1", true, true, "2026-10-28T12:00:00Z"))
        var aberta: AtividadePublicada? = null
        vm.abrirSessao(emAndamento) { aberta = it }
        assertEquals(emAndamento, aberta)
        assertNull(vm.sessaoSemAlunos)
    }

    @Test
    fun `depois de publicar, aviso com o botao da atividade nova`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        vm.avisarPublicada("a1")
        esperar { vm.aviso?.atividade != null }
        val aviso = vm.aviso!!
        assertEquals("Atividade publicada. Para a turma responder, abra a sessão em sala.", aviso.texto)
        assertEquals("Abrir sessão em sala", aviso.acao)
        assertTrue(vm.atividades is Carga.Pronto)
    }

    @Test
    fun `estatisticas - so reais por padrao, simulacao quando pedida`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        vm.aba = TurmaViewModel.ABA_ESTATISTICAS
        vm.carregarEstatisticas()
        esperar { (vm.estatisticas as? Carga.Pronto)?.valor?.resumo?.alunos == 3 }
        vm.mudarIncluirSimulacao(true)
        esperar { (vm.estatisticas as? Carga.Pronto)?.valor?.resumo?.alunos == 28 }
        assertEquals(false, pedidosEstatisticas.first())
        assertEquals(true, pedidosEstatisticas.last())
    }

    @Test
    fun `clicar num aluno carrega o mapa dele e o historico do conceito`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        val ana = Aluno("Ana", EstadoConceito.Consolidado, "ana")
        vm.selecionarAluno(ana)
        esperar { (vm.mapaDoAluno as? Carga.Pronto)?.valor?.conceitos?.first()?.dominio == 0.9 }
        vm.carregarHistorico("Base")
        esperar { (vm.historico as? Carga.Pronto)?.valor?.conceito == "Base" }
        vm.selecionarAluno(null)
        assertNull(vm.alunoSelecionado)
        assertNull(vm.historico)
    }

    @Test
    fun `conceito das estatisticas abre no mapa da turma toda`() {
        val vm = viewModel(membros = emptyList())
        vm.abrir("t1")
        vm.selecionarAluno(Aluno("Ana", EstadoConceito.Consolidado, "ana"))
        vm.aba = TurmaViewModel.ABA_ESTATISTICAS
        vm.abrirConceitoNoMapa("Base")
        assertEquals(TurmaViewModel.ABA_GRAFO, vm.aba)
        assertEquals("Base", vm.conceitoEmFoco)
        assertNull(vm.alunoSelecionado)
    }
}
