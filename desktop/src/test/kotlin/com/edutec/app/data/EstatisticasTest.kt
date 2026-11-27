package com.edutec.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstatisticasTest {
    private val base = EstatisticasTurma(
        resumo = ResumoTurma(alunos = 20, atividades = 3, atividadesEncerradas = 3, respostas = 400),
        confianca = ConfiancaTurma(turma = ContagemConfianca(comConfianca = 50)),
    )

    @Test
    fun `amostra suficiente nao tem aviso`() {
        assertTrue(avisosPoucosDados(base).isEmpty())
    }

    @Test
    fun `cada falta de amostra tem o seu motivo`() {
        val poucos = base.copy(
            resumo = base.resumo.copy(alunos = 4, atividadesEncerradas = 1),
            confianca = ConfiancaTurma(turma = ContagemConfianca(comConfianca = 4)),
        )
        val avisos = avisosPoucosDados(poucos)
        assertEquals(3, avisos.size)
        assertTrue(avisos[0].startsWith("4 alunos (menos de 5)"))
        assertTrue(avisos[1].startsWith("1 atividade encerrada (menos de 2)"))
        assertTrue(avisos[2].startsWith("4 respostas com confiança informada (menos de 5)"))
    }

    @Test
    fun `destaques - subiram, cairam e em revisar`() {
        val p = PontoEvolucao(
            AtividadeRef("a", 2, "A"),
            conceitos = listOf(
                ConceitoEvolucao("Sobe pouco", 0.60, 0.55, "em_desenvolvimento"),
                ConceitoEvolucao("Sobe muito", 0.85, 0.50, "consolidado"),
                ConceitoEvolucao("Cai", 0.40, 0.52, "revisar"),
                ConceitoEvolucao("Novo", 0.30, null, "revisar"),
                ConceitoEvolucao("Igual", 0.70, 0.70, "em_desenvolvimento"),
            ),
        )
        val d = destaques(p)
        assertEquals(listOf("Sobe muito", "Sobe pouco"), d.subiram.map { it.conceito })
        assertEquals(listOf("Cai"), d.cairam.map { it.conceito })
        assertEquals(listOf("Novo", "Cai"), d.emRevisar.map { it.conceito })
    }

    @Test
    fun `mapa do aluno usa os nos da turma e marca sem dados`() {
        val turma = GrafoTurma(
            listOf(ConceitoGrafo("A", emptyList(), 10, 40, 0.7, 0.7, EstadoConceito.Desenvolvimento),
                ConceitoGrafo("B", listOf("A"), 10, 30, 0.4, 0.4, EstadoConceito.Revisar)),
            emptyList(),
        )
        val dele = turma.doAluno(MapaAluno(listOf(
            DominioAlunoConceito("A", 0.92, "consolidado", 3),
            DominioAlunoConceito("B", null, null, 0),
        )))
        assertEquals(listOf("A", "B"), dele.conceitos.map { it.nome })
        assertEquals(listOf("A"), dele.conceitos[1].prerequisitos)
        assertEquals(EstadoConceito.Consolidado, dele.conceitos[0].estado)
        assertEquals(0.92, dele.conceitos[0].media!!, 1e-9)
        assertTrue(dele.conceitos[1].semDados)
        assertNull(dele.conceitos[1].media)
    }

    @Test
    fun `poucos dados - menos de 3 no mapa do aluno, menos de 5 na turma`() {
        val turma = GrafoTurma(listOf(ConceitoGrafo("A", respostas = 4, media = 0.9, estado = EstadoConceito.Consolidado)), emptyList())
        assertTrue(turma.conceitos[0].poucosDados) // turma: 4 < 5
        val dele = { n: Int -> turma.doAluno(MapaAluno(listOf(DominioAlunoConceito("A", 0.9, "consolidado", n)))).conceitos[0] }
        assertTrue(dele(2).poucosDados)
        assertEquals(false, dele(3).poucosDados)
        assertEquals(false, dele(4).poucosDados)
    }

    @Test
    fun `json da turma-api`() {
        val json = Json { ignoreUnknownKeys = true }
        val e = json.decodeFromString<EstatisticasTurma>("""
            {"tem_simulacao": true, "incluir_simulacao": false,
             "resumo": {"n_alunos": 3, "n_atividades": 2, "n_atividades_encerradas": 1, "n_respostas": 8,
                        "media_dominio": 0.6123, "mediana_dominio": 0.6, "estado_moda": "em_desenvolvimento"},
             "conceitos": [{"conceito": "Base", "media_dominio": 0.5, "mediana_dominio": 0.5, "estado_moda": "revisar",
                            "n_respostas": 4, "alunos_por_estado": {"consolidado": 1, "em_desenvolvimento": 0, "revisar": 1, "sem_dados": 1}}],
             "evolucao": [{"atividade": {"id": "x", "numero": 1, "titulo": "T"}, "encerrada_em": "2026-11-29T10:00:00.000Z",
                           "n_enviadas": 2, "taxa_acerto": 0.5, "media_dominio": 0.55,
                           "conceitos": [{"conceito": "Base", "media_dominio": 0.55, "media_anterior": null, "estado": "em_desenvolvimento"}]}],
             "confianca": {"turma": {"n_respostas": 6, "n_com_confianca": 2, "erros_com_certeza": 1, "acertos_no_chute": 1},
                           "conceitos": [{"conceito": "Base", "n_respostas": 4, "n_com_confianca": 2, "erros_com_certeza": 1, "acertos_no_chute": 1}],
                           "alunos": []}}
        """)
        assertEquals(3, e.resumo.alunos)
        assertEquals(1, e.resumo.atividadesEncerradas)
        assertEquals(3, e.conceitos[0].alunosPorEstado.total)
        assertEquals(0.5, e.evolucao[0].taxaAcerto!!, 1e-9)
        assertEquals(1, e.confianca.doConceito("Base").errosComCerteza)
        assertEquals(0, e.confianca.doConceito("Outro").acertosNoChute)
        val h = json.decodeFromString<HistoricoConceito>("""
            {"conceito": "Base", "p_inicial": 0.3, "erros_com_certeza": 1, "acertos_no_chute": 0,
             "respostas": [{"criado_em": "2026-11-29T10:00:00.000Z", "atividade": null, "acertou": false, "confianca": "certeza", "p_dominio": 0.13}]}
        """)
        assertNull(h.respostas[0].atividade)
        assertEquals("Certeza", rotuloConfianca(h.respostas[0].confianca))
        // Servidor anterior à fase 4 (sem "treino"): vazio, sem quebrar.
        assertEquals(0, e.treino.resumo.treinos)
        assertEquals(0, h.treino.treinos)
    }

    @Test
    fun `json do treino fora de sala (fase 4)`() {
        val json = Json { ignoreUnknownKeys = true }
        val t = json.decodeFromString<TreinoTurma>("""
            {"resumo": {"n_alunos": 2, "n_alunos_treinando": 1, "treinos_7_dias": 8, "n_treinos": 8, "taxa_acerto": 0.5},
             "alunos": [
               {"aluno_id": "a", "nome": "Ana", "treinos_7_dias": 8, "n_treinos": 8, "acertos": 4, "taxa_acerto": 0.5,
                "cartoes_por_caixa": {"1": 2, "2": 1, "3": 0}, "ultimo_treino_em": "2026-11-30T12:00:00.000Z"},
               {"aluno_id": "b", "nome": "Bia", "treinos_7_dias": 0, "n_treinos": 0, "acertos": 0, "taxa_acerto": null,
                "cartoes_por_caixa": {"1": 0, "2": 0, "3": 0}, "ultimo_treino_em": null}],
             "conceitos": [{"conceito": "Base", "n_alunos_treinando": 1, "treinos_7_dias": 6}]}
        """)
        assertEquals(0.5, t.resumo.taxaAcerto!!, 1e-9)
        assertEquals(CartoesPorCaixa(2, 1, 0), t.alunos[0].caixas)
        assertNull(t.alunos[1].taxaAcerto)
        assertEquals(listOf("Ana"), t.alunosQueTreinaram().map { it.nome })
        assertEquals(1, t.conceitos[0].alunosTreinando)
        val h = json.decodeFromString<HistoricoConceito>("""{"conceito": "Base", "respostas": [], "treino": {"n_treinos": 6, "acertos": 4}}""")
        assertEquals(TreinoConceito(6, 4), h.treino)
    }

    @Test
    fun `quem treinou - mais treinos nos ultimos 7 dias primeiro`() {
        val t = TreinoTurma(alunos = listOf(
            TreinoAluno("1", "Caio", treinos7Dias = 0, treinos = 30),
            TreinoAluno("2", "Bia", treinos7Dias = 5, treinos = 5),
            TreinoAluno("3", "Ana", treinos7Dias = 5, treinos = 9),
            TreinoAluno("4", "Davi"),
        ))
        assertEquals(listOf("Ana", "Bia", "Caio"), t.alunosQueTreinaram().map { it.nome })
    }

    @Test
    fun `linha do treino no historico do conceito`() {
        assertEquals("Treinou 6 vezes (4 acertos)", textoTreinoConceito(TreinoConceito(6, 4)))
        assertEquals("Treinou 1 vez (1 acerto)", textoTreinoConceito(TreinoConceito(1, 1)))
        assertEquals("Treinou 2 vezes (nenhum acerto)", textoTreinoConceito(TreinoConceito(2, 0)))
        assertEquals("Ainda não treinou este conceito com flashcards.", textoTreinoConceito(TreinoConceito()))
    }

    @Test
    fun `limiares do estado iguais aos do banco`() {
        assertEquals(EstadoConceito.Revisar, estadoDominioLocal(0.4999))
        assertEquals(EstadoConceito.Desenvolvimento, estadoDominioLocal(0.5))
        assertEquals(EstadoConceito.Consolidado, estadoDominioLocal(0.8))
    }
}
