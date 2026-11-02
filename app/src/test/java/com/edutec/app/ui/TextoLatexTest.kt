package com.edutec.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoLatexTest {

    @Test
    fun `texto sem formula fica igual`() {
        assertEquals("Explique a poda alfa-beta.", textoSimplesComLatex("Explique a poda alfa-beta."))
    }

    @Test
    fun `remove delimitadores e mantem nomes simples`() {
        assertEquals("a função heurística (AVAL)", textoSimplesComLatex("a função heurística (\$AVAL\$)"))
    }

    @Test
    fun `vetor com subscritos do resultado real`() {
        val r = textoComLatex("um vetor \$\\langle v_A, v_B, v_C \\rangle\$")
        assertEquals("um vetor ⟨ vA, vB, vC ⟩", r.text)
        // A, B e C em subscrito.
        assertEquals(3, r.spanStyles.size)
    }

    @Test
    fun `sobrescrito, fracao e raiz`() {
        assertEquals("x2 + (a+b)/2 ≤ √(x+1)", textoSimplesComLatex("\$x^{2} + \\frac{a+b}{2} \\le \\sqrt{x+1}\$"))
        assertTrue(textoComLatex("\$A^*\$").spanStyles.isNotEmpty())
    }

    @Test
    fun `bloco e texto`() {
        assertEquals("f(x) = 3 se x ∈ ℝ", textoSimplesComLatex("\$\$f(x) = 3 \\text{ se } x \\in ℝ\$\$"))
    }

    @Test
    fun `cifrao sozinho nao quebra`() {
        assertEquals("custa R\$ 10", textoSimplesComLatex("custa R\$ 10"))
    }
}
