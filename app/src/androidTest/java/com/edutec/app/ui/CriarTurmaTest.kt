package com.edutec.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edutec.app.data.Turma
import com.edutec.app.ui.theme.EdutecTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** O caminho da demo ao vivo: app vazio → criar turma (com acento e ç) → turma sem dados de exemplo. */
@RunWith(AndroidJUnit4::class)
class CriarTurmaTest {
    @get:Rule
    val regra = createComposeRule()

    @Test
    fun criaTurmaComAcentoECedilha() {
        val turmas = mutableStateListOf<Turma>()
        regra.setContent { EdutecTheme { MinhasTurmasScreen(turmas = turmas) } }

        regra.onNodeWithText("Nenhuma turma ainda").assertIsDisplayed()
        regra.onNodeWithText("Nova turma").performClick()
        regra.onNodeWithText("Disciplina").performTextInput("Programação Orientada a Objetos")
        regra.onNodeWithText("Turma (ex.: Turma C)").performTextInput("Turma Única")
        regra.onNodeWithText("Criar").performClick()

        assertEquals("Programação Orientada a Objetos", turmas.single().nome)
        assertEquals("Turma Única", turmas.single().codigo)
        regra.onNodeWithText("Programação Orientada a Objetos").assertIsDisplayed()
        regra.onNodeWithText("Turma Única · nenhum aluno").assertIsDisplayed()
        // "Nova turma" duas vezes: a etiqueta do cartão da turma sem dados e o cartão para criar outra.
        assertEquals(2, regra.onAllNodesWithText("Nova turma").fetchSemanticsNodes().size)
        // Sem resumo inventado nem marcadores do wireframe.
        assertEquals(0, regra.onAllNodesWithText("aguarda", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, regra.onAllNodesWithText("[", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun codigoEmBrancoViraTurmaUnica() {
        val turmas = mutableStateListOf<Turma>()
        regra.setContent { EdutecTheme { MinhasTurmasScreen(turmas = turmas) } }

        regra.onNodeWithText("Nova turma").performClick()
        regra.onNodeWithText("Disciplina").performTextInput("Física Experimental")
        regra.onNodeWithText("Criar").performClick()

        assertEquals("Turma única", turmas.single().codigo)
        regra.onNodeWithText("Turma única · nenhum aluno").assertIsDisplayed()
    }

    @Test
    fun turmaNovaNaoMostraDadosDeExemplo() {
        val turma = Turma(
            nome = "Programação I", codigo = "Turma C", alunos = 0,
            consolidado = 0, desenvolvimento = 0, revisar = 0, rodape = "Nenhuma atividade ainda",
        )
        regra.setContent { EdutecTheme { TurmaScreen(turma, onVoltar = {}, onNovaAtividade = {}) } }

        regra.onNodeWithText("Ainda não há dados de aprendizagem nesta turma").assertIsDisplayed()
        regra.onNodeWithText("Nenhum aluno ainda").assertIsDisplayed()
        for (exemplo in listOf("Aluno A", "Regra da cadeia", "Derivada", "Funções", "respostas", "Turma toda")) {
            assertEquals(
                "não deveria aparecer: $exemplo", 0,
                regra.onAllNodesWithText(exemplo, substring = true).fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    fun novaAtividadeDaTurmaNovaAbreEmPdfSemConceitosDeExemplo() {
        val turma = Turma(
            nome = "Programação I", codigo = "Turma C", alunos = 0,
            consolidado = 0, desenvolvimento = 0, revisar = 0, rodape = "Nenhuma atividade ainda",
        )
        regra.setContent { EdutecTheme { NovaAtividadeScreen(turma, onFechar = {}) } }

        regra.onNodeWithText("Selecionar PDF").assertIsDisplayed()
        regra.onNodeWithText("Programação I · Turma C").assertIsDisplayed()
        for (exemplo in listOf("Regra da cadeia", "Derivada", "Limites", "[")) {
            assertEquals(
                "não deveria aparecer: $exemplo", 0,
                regra.onAllNodesWithText(exemplo, substring = true).fetchSemanticsNodes().size,
            )
        }
    }
}
