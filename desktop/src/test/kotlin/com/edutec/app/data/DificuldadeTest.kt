package com.edutec.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DificuldadeTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** O mesmo formato de public.alunos_do_conceito (testado no pgTAP). */
    private val alunos = json.decodeFromString<AlunosDoConceito>("""
        {"conceito": "Base", "alunos": [
          {"aluno_id": "a", "nome": "Ana", "p_dominio": 0.1463, "estado": "revisar", "n_respostas": 1,
           "erros_com_certeza": 1, "acertos_no_chute": 0, "treino": {"n_treinos": 2, "acertos": 1}},
          {"aluno_id": "b", "nome": "Bia", "p_dominio": 0.6927, "estado": "em_desenvolvimento", "n_respostas": 4,
           "erros_com_certeza": 0, "acertos_no_chute": 1, "treino": {"n_treinos": 0, "acertos": 0}},
          {"aluno_id": "d", "nome": "Davi", "p_dominio": 0.91, "estado": "consolidado", "n_respostas": 6,
           "erros_com_certeza": 0, "acertos_no_chute": 0, "treino": {"n_treinos": 0, "acertos": 0}},
          {"aluno_id": "c", "nome": "Caio", "p_dominio": null, "estado": null, "n_respostas": 0,
           "erros_com_certeza": 0, "acertos_no_chute": 0, "treino": {"n_treinos": 0, "acertos": 0}}]}
    """)

    @Test
    fun `json da turma-api e grupos - revisar, em desenvolvimento e o resto recolhido`() {
        val g = alunos.agrupar()
        assertEquals(listOf("Ana"), g.revisar.map { it.nome })
        assertEquals(listOf("Bia"), g.desenvolvimento.map { it.nome })
        assertEquals(listOf("Davi", "Caio"), g.demais.map { it.nome })
        assertFalse(g.ninguemComDificuldade)
        assertEquals(TreinoConceito(2, 1), alunos.alunos[0].treino)
        assertNull(alunos.alunos[3].estado)
    }

    @Test
    fun `ninguem com dificuldade`() {
        val g = alunos.copy(alunos = alunos.alunos.drop(2)).agrupar()
        assertTrue(g.ninguemComDificuldade)
        assertEquals(2, g.demais.size)
    }

    @Test
    fun `poucos dados abaixo de 3 respostas, como no mapa do aluno`() {
        assertTrue(alunos.alunos[0].poucosDados)  // 1 resposta
        assertFalse(alunos.alunos[1].poucosDados) // 4
        assertFalse(alunos.alunos[3].poucosDados) // sem dados não é "poucos dados"
        assertTrue(AlunoNoConceito("x", dominio = 0.4, respostas = 2).poucosDados)
        assertFalse(AlunoNoConceito("x", dominio = 0.4, respostas = 3).poucosDados)
    }

    private val origens = listOf(
        OrigemConceito("Derivada", "pdf-a", AtividadeOrigem("a1", 1, "Limites e derivadas")),
        OrigemConceito("Continuidade", "pdf-a", AtividadeOrigem("a1", 1, "Limites e derivadas")),
        OrigemConceito("Regra da cadeia", "pdf-b", AtividadeOrigem("a2", 2, "Regra da cadeia")),
        OrigemConceito("Solto", null, null),
    )

    @Test
    fun `material do reforco - o pdf do principal, o primeiro escolhido`() {
        val mesmo = materialDoReforco(listOf("Continuidade", "Derivada"), origens)!!
        assertEquals("Continuidade", mesmo.principal)
        assertEquals("Atividade 1 · Limites e derivadas", mesmo.atividade!!.rotulo())
        assertTrue(mesmo.deOutroMaterial.isEmpty())

        val outro = materialDoReforco(listOf("Regra da cadeia", "Derivada", "Continuidade"), origens)!!
        assertEquals(2, outro.atividade!!.numero)
        assertEquals(listOf("Derivada", "Continuidade"), outro.deOutroMaterial)

        assertNull(materialDoReforco(listOf("Solto"), origens)!!.atividade)
        assertNull(materialDoReforco(emptyList(), origens))
    }

    @Test
    fun `titulo sugerido do reforco`() {
        assertEquals("Reforço: Derivada", tituloReforco(listOf("Derivada")))
        assertEquals("Reforço: A, B e C", tituloReforco(listOf("A", "B", "C")))
        assertTrue(tituloReforco(List(50) { "Conceito longo $it" }).length <= 200)
    }

    @Test
    fun `conceitos do mapa para a nova atividade - sem dados fica sem estado`() {
        val g = GrafoTurma(listOf(
            ConceitoGrafo("A", media = 0.42, estado = EstadoConceito.Revisar, respostas = 10, alunos = 3),
            ConceitoGrafo("B"),
        ), emptyList())
        assertEquals(listOf(Conceito("A", 42, EstadoConceito.Revisar), Conceito("B", 0, null)), g.conceitosParaReforco())
    }

    @Test
    fun `origem_conceitos da turma-api`() {
        val o = json.decodeFromString<List<OrigemConceito>>("""
            [{"conceito": "Base", "material": "aaa", "atividade": {"id": "x", "numero": 1, "titulo": "Material A"}},
             {"conceito": "Solto", "material": null, "atividade": null}]
        """)
        assertEquals("Material A", o[0].atividade!!.titulo)
        assertNull(o[1].atividade)
    }
}
