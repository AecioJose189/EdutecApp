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
