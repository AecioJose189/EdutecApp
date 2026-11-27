package com.edutec.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Mostra uma mensagem curta no Snackbar da janela (no Android era um Toast). */
val LocalAvisar = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Pilha de ações de "voltar" (no Android era o BackHandler): a tecla Esc da janela chama a mais recente.
 * Cada tela registra a sua com [TeclaVoltar].
 */
class AcoesVoltar {
    private val pilha = mutableStateListOf<() -> Unit>()

    /** Executa a ação de voltar da tela visível; devolve false se não houver nenhuma. */
    fun voltar(): Boolean {
        val acao = pilha.lastOrNull() ?: return false
        acao()
        return true
    }

    internal fun registrar(acao: () -> Unit) = pilha.add(acao)
    internal fun remover(acao: () -> Unit) = pilha.remove(acao)
}

val LocalAcoesVoltar = compositionLocalOf { AcoesVoltar() }

/** Enquanto estiver na composição, Esc chama [onVoltar]. */
@Composable
fun TeclaVoltar(onVoltar: () -> Unit) {
    val acoes = LocalAcoesVoltar.current
    val atual = rememberUpdatedState(onVoltar)
    DisposableEffect(acoes) {
        val acao: () -> Unit = { atual.value() }
        acoes.registrar(acao)
        onDispose { acoes.remover(acao) }
    }
}

/** Estado da janela (a projeção da sessão entra e sai da tela cheia). Nulo nos @Preview. */
val LocalJanela = staticCompositionLocalOf<androidx.compose.ui.window.WindowState?> { null }

/** Traz a janela do Lumini para a frente (ex.: na volta do login no navegador). Nada nos @Preview. */
val LocalTrazerParaFrente = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Restaura (se minimizada), põe na frente e pede o foco. Alguns sistemas (Windows, vários gerenciadores
 * do Linux) não deixam um programa em segundo plano roubar o foco: se ela não ganhou o foco em 300 ms,
 * fica "sempre no topo" por um instante, o que a traz para cima mesmo assim.
 */
fun trazerParaFrente(janela: java.awt.Frame) = java.awt.EventQueue.invokeLater {
    if (janela.extendedState and java.awt.Frame.ICONIFIED != 0) {
        janela.extendedState = janela.extendedState and java.awt.Frame.ICONIFIED.inv()
    }
    janela.toFront()
    janela.requestFocus()
    javax.swing.Timer(300) {
        if (!janela.isFocused) {
            janela.isAlwaysOnTop = true
            janela.toFront()
            janela.requestFocus()
            javax.swing.Timer(400) { janela.isAlwaysOnTop = false }.apply { isRepeats = false }.start()
        }
    }.apply { isRepeats = false }.start()
}
