package com.edutec.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EdutecColors = lightColorScheme(
    primary = Consolidado,
    onPrimary = Cartao,
    secondary = EmDesenvolvimento,
    tertiary = Revisar,
    background = Fundo,
    onBackground = TextoPrincipal,
    surface = Cartao,
    onSurface = TextoPrincipal,
    surfaceVariant = Pilula,
    onSurfaceVariant = TextoSecundario,
)

@Composable
fun EdutecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EdutecColors, content = content)
}
