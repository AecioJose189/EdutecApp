package com.edutec.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.edutec.app.ui.AcoesVoltar
import com.edutec.app.ui.EdutecApp
import com.edutec.app.ui.LocalAcoesVoltar
import com.edutec.app.ui.LocalAvisar
import com.edutec.app.ui.LocalJanela
import com.edutec.app.ui.theme.EdutecTheme
import kotlinx.coroutines.launch
import java.awt.Dimension

/** Lumini para o professor (desktop). Abre direto no layout de tela larga, o mesmo do tablet. */
fun main() = application {
    val acoesVoltar = remember { AcoesVoltar() }
    val estadoJanela = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lumini",
        state = estadoJanela,
        onPreviewKeyEvent = { evento ->
            evento.type == KeyEventType.KeyDown && evento.key == Key.Escape && acoesVoltar.voltar()
        },
    ) {
        // Abaixo disto as telas passariam para o layout de celular (Turma pede 1000 dp).
        window.minimumSize = Dimension(1024, 680)

        val avisos = remember { SnackbarHostState() }
        val escopo = rememberCoroutineScope()
        CompositionLocalProvider(
            LocalAvisar provides { mensagem: String -> escopo.launch { avisos.showSnackbar(mensagem) } },
            LocalAcoesVoltar provides acoesVoltar,
            LocalJanela provides estadoJanela,
        ) {
            EdutecTheme {
                Box(Modifier.fillMaxSize()) {
                    EdutecApp()
                    SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(24.dp))
                }
            }
        }
    }
}
