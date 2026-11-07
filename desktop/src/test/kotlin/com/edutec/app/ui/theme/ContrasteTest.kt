package com.edutec.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contraste mínimo do Design System (WCAG 2.1 AA), nos dois temas: 4,5:1 em todo texto e 3:1 em
 * contornos de controles e no anel de foco. Os pares são os que as telas usam; ao usar uma combinação
 * nova de texto e fundo, acrescente-a aqui.
 */
class ContrasteTest {
    private val temas = mapOf("claro" to CoresClaras, "escuro" to CoresEscuras)

    /** (nome, texto, fundo) */
    private fun paresDeTexto(c: CoresLumini) = buildList {
        val fundosNeutros = listOf("fundo" to c.fundo, "superficie" to c.superficie, "menta-suave" to c.mentaSuave)
        for ((nf, f) in fundosNeutros) {
            add(Triple("tinta sobre $nf", c.tinta, f))
            add(Triple("tinta-suave sobre $nf", c.tintaSuave, f))
        }
        // Sobre menta (item selecionado, aba ativa), texto sempre tinta: tinta-suave dá 3,99:1 no claro.
        add(Triple("tinta sobre menta", c.tinta, c.menta))
        for ((nf, f) in fundosNeutros) {
            add(Triple("primaria sobre $nf", c.primaria, f)) // links e botões de texto
            add(Triple("erro sobre $nf", c.erro, f)) // mensagens de erro
        }
        add(Triple("primaria sobre menta", c.primaria, c.menta)) // "Correta" na alternativa marcada
        add(Triple("sobre-primaria sobre primaria", c.sobrePrimaria, c.primaria))
        add(Triple("sobre-primaria sobre primaria-forte", c.sobrePrimaria, c.primariaForte))
        add(Triple("sobre-erro sobre erro", c.sobreErro, c.erro))
        add(Triple("sobre-acento sobre acento", c.sobreAcento, c.acento))
        add(Triple("sobre-consolidado sobre estado-consolidado", c.sobreConsolidado, c.estadoConsolidado))
        add(Triple("sobre-desenvolvimento sobre estado-desenvolvimento", c.sobreDesenvolvimento, c.estadoDesenvolvimento))
        add(Triple("sobre-revisar sobre estado-revisar", c.sobreRevisar, c.estadoRevisar))
    }

    private fun paresDeControle(c: CoresLumini) = listOf(
        Triple("borda-controle sobre fundo", c.bordaControle, c.fundo),
        Triple("borda-controle sobre superficie", c.bordaControle, c.superficie),
        Triple("foco sobre fundo", c.foco, c.fundo),
        Triple("foco sobre superficie", c.foco, c.superficie),
        Triple("estado-sem-dados (contorno) sobre superficie", c.estadoSemDados, c.superficie),
    )

    @Test
    fun textoTemPeloMenos4e5() = conferir(4.5) { paresDeTexto(it) }

    @Test
    fun controlesTemPeloMenos3() = conferir(3.0) { paresDeControle(it) }

    private fun conferir(minimo: Double, pares: (CoresLumini) -> List<Triple<String, Color, Color>>) {
        val falhas = temas.flatMap { (tema, cores) ->
            pares(cores).mapNotNull { (nome, a, b) ->
                val r = contraste(a, b)
                println("%-8s %-52s %5.2f:1".format(tema, nome, r))
                if (r < minimo) "$tema: $nome = %.2f:1".format(r) else null
            }
        }
        assertTrue("Abaixo de $minimo:1 → $falhas", falhas.isEmpty())
    }

    private fun luminancia(c: Color): Double {
        fun canal(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    }

    private fun contraste(a: Color, b: Color): Double {
        val (claro, escuro) = listOf(luminancia(a), luminancia(b)).sortedDescending()
        return (claro + 0.05) / (escuro + 0.05)
    }
}
