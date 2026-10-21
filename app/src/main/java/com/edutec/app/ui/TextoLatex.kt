package com.edutec.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em

/**
 * Converte texto com fórmulas LaTeX ($...$ ou $$...$$) em texto legível, sem biblioteca externa:
 * comandos comuns viram símbolos Unicode (\alpha → α, \le → ≤), \frac{a}{b} vira a/b,
 * ^ e _ viram sobrescrito e subscrito. Comandos desconhecidos aparecem sem a barra.
 * Versão provisória até termos um renderizador de LaTeX de verdade.
 */
fun textoComLatex(texto: String): AnnotatedString = buildAnnotatedString {
    var inicio = 0
    for (m in Formula.findAll(texto)) {
        append(texto.substring(inicio, m.range.first))
        val formula = m.groupValues[1].ifEmpty { m.groupValues[2] }
        for (p in LeitorFormula(formula).ler()) {
            when (p.posicao) {
                Posicao.Normal -> append(p.texto)
                else -> {
                    val estilo = SpanStyle(
                        baselineShift = if (p.posicao == Posicao.Acima) BaselineShift.Superscript else BaselineShift.Subscript,
                        fontSize = 0.75.em,
                    )
                    pushStyle(estilo)
                    append(p.texto)
                    pop()
                }
            }
        }
        inicio = m.range.last + 1
    }
    append(texto.substring(inicio))
}

/** Mesma conversão, só o texto (sem sobrescrito/subscrito). Útil para resumos de uma linha. */
fun textoSimplesComLatex(texto: String): String = textoComLatex(texto).text

private val Formula = Regex("""\$\$(.+?)\$\$|\$(.+?)\$""", RegexOption.DOT_MATCHES_ALL)

private enum class Posicao { Normal, Acima, Abaixo }

private data class Pedaco(val texto: String, val posicao: Posicao)

private val Simbolos = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
    "theta" to "θ", "lambda" to "λ", "mu" to "μ", "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
    "phi" to "φ", "varphi" to "φ", "omega" to "ω", "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω",
    "Gamma" to "Γ", "Theta" to "Θ", "Lambda" to "Λ", "Pi" to "Π", "Phi" to "Φ",
    "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
    "equiv" to "≡", "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "infty" to "∞",
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒", "Leftrightarrow" to "⇔",
    "iff" to "⇔", "implies" to "⇒", "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
    "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "forall" to "∀", "exists" to "∃", "neg" to "¬",
    "land" to "∧", "lor" to "∨", "wedge" to "∧", "vee" to "∨", "sum" to "Σ", "prod" to "Π", "int" to "∫",
    "partial" to "∂", "nabla" to "∇", "langle" to "⟨", "rangle" to "⟩", "ldots" to "…", "cdots" to "⋯",
    "dots" to "…", "sin" to "sen", "tan" to "tg", "left" to "", "right" to "",
    "," to " ", ";" to " ", ":" to " ", "!" to "", " " to " ", "quad" to "  ", "qquad" to "    ",
    "\\" to "\n",
)

/** Comandos cujo argumento é só mostrado como texto. */
private val SoArgumento = setOf(
    "text", "textrm", "textbf", "textit", "mathrm", "mathbf", "mathit", "mathcal", "mathbb", "operatorname",
)

/** Leitor recursivo de uma fórmula; devolve pedaços de texto com a posição (normal, acima, abaixo). */
private class LeitorFormula(private val s: String) {
    private var i = 0

    fun ler(): List<Pedaco> = juntar(lerSequencia(fim = null))

    /** Lê até o fim ou até o caractere [fim] (que é consumido). */
    private fun lerSequencia(fim: Char?): List<Pedaco> {
        val saida = mutableListOf<Pedaco>()
        while (i < s.length) {
            val c = s[i]
            when {
                c == fim -> { i++; return saida }
                c == '{' -> { i++; saida += lerSequencia('}') }
                c == '}' -> i++ // chave sem par: ignora
                c == '^' || c == '_' -> {
                    i++
                    val posicao = if (c == '^') Posicao.Acima else Posicao.Abaixo
                    saida += lerArgumento().map { it.copy(posicao = posicao) }
                }
                c == '\\' -> saida += lerComando()
                else -> { saida += Pedaco(c.toString(), Posicao.Normal); i++ }
            }
        }
        return saida
    }

    /** Argumento de ^, _ ou de um comando: {grupo}, \comando ou um único caractere. */
    private fun lerArgumento(): List<Pedaco> {
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return emptyList()
        return when (s[i]) {
            '{' -> { i++; lerSequencia('}') }
            '\\' -> lerComando()
            else -> listOf(Pedaco(s[i++].toString(), Posicao.Normal))
        }
    }

    private fun lerComando(): List<Pedaco> {
        i++ // barra
        if (i >= s.length) return emptyList()
        val nome = if (s[i].isLetter()) {
            val inicio = i
            while (i < s.length && s[i].isLetter()) i++
            s.substring(inicio, i)
        } else {
            s[i++].toString()
        }
        return when (nome) {
            "frac", "dfrac", "tfrac" -> {
                val numerador = entreParenteses(lerArgumento())
                val denominador = entreParenteses(lerArgumento())
                numerador + Pedaco("/", Posicao.Normal) + denominador
            }
            "sqrt" -> listOf(Pedaco("√", Posicao.Normal)) + entreParenteses(lerArgumento())
            in SoArgumento -> lerArgumento()
            else -> listOf(Pedaco(Simbolos[nome] ?: nome, Posicao.Normal))
        }
    }

    /** (a+b) quando o argumento tem mais de um caractere; senão, o próprio argumento. */
    private fun entreParenteses(pedacos: List<Pedaco>): List<Pedaco> {
        val texto = pedacos.joinToString("") { it.texto }
        return if (texto.length <= 1) pedacos
        else listOf(Pedaco("(", Posicao.Normal)) + pedacos + Pedaco(")", Posicao.Normal)
    }

    /** Junta pedaços vizinhos com a mesma posição e colapsa espaços repetidos (como o LaTeX faz). */
    private fun juntar(pedacos: List<Pedaco>): List<Pedaco> {
        val saida = mutableListOf<Pedaco>()
        for (p in pedacos) {
            val ultimo = saida.lastOrNull()
            val texto = if (ultimo != null && ultimo.texto.endsWith(' ')) p.texto.trimStart(' ') else p.texto
            if (texto.isEmpty()) continue
            if (ultimo != null && ultimo.posicao == p.posicao) saida[saida.lastIndex] = ultimo.copy(texto = ultimo.texto + texto)
            else saida += p.copy(texto = texto)
        }
        return saida
    }
}
