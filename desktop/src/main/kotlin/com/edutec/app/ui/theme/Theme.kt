package com.edutec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tokens do tema em uso. Nas telas, use os atalhos abaixo (Primaria, Tinta, EstadoRevisar...). */
val LocalCoresLumini = staticCompositionLocalOf { CoresClaras }

/**
 * Tema do app. Segue o tema do sistema; `LUMINI_TEMA=claro|escuro` (variável de ambiente) ou
 * `-Dlumini.tema=claro|escuro` força um dos dois (útil para conferir contraste e para a projeção).
 */
@Composable
fun EdutecTheme(escuro: Boolean = temaEscuroPreferido(), content: @Composable () -> Unit) {
    val cores = if (escuro) CoresEscuras else CoresClaras
    CompositionLocalProvider(LocalCoresLumini provides cores) {
        MaterialTheme(
            colorScheme = esquemaMaterial(cores),
            typography = TipografiaLumini,
            shapes = FormasLumini,
            content = content,
        )
    }
}

@Composable
private fun temaEscuroPreferido(): Boolean {
    val escolhido = (System.getenv("LUMINI_TEMA") ?: System.getProperty("lumini.tema"))?.trim()?.lowercase()
    return when (escolhido) {
        "escuro", "dark" -> true
        "claro", "light" -> false
        else -> isSystemInDarkTheme()
    }
}

/** Os tokens no ColorScheme do Material 3, para os componentes prontos (botões, campos, diálogos...). */
private fun esquemaMaterial(c: CoresLumini): ColorScheme {
    val base = if (c.escuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primaria,
        onPrimary = c.sobrePrimaria,
        primaryContainer = c.menta,
        onPrimaryContainer = c.tinta,
        inversePrimary = c.menta,
        secondary = c.primaria,
        onSecondary = c.sobrePrimaria,
        secondaryContainer = c.menta,
        onSecondaryContainer = c.tinta,
        tertiary = c.acento,
        onTertiary = c.sobreAcento,
        tertiaryContainer = c.mentaSuave,
        onTertiaryContainer = c.tinta,
        background = c.fundo,
        onBackground = c.tinta,
        surface = c.superficie,
        onSurface = c.tinta,
        surfaceVariant = c.mentaSuave,
        onSurfaceVariant = c.tintaSuave,
        surfaceTint = c.superficie,
        surfaceBright = c.superficie,
        surfaceDim = c.fundo,
        surfaceContainerLowest = c.superficie,
        surfaceContainerLow = c.superficie,
        surfaceContainer = c.superficie,
        surfaceContainerHigh = c.superficie,
        surfaceContainerHighest = c.mentaSuave,
        inverseSurface = c.tinta,
        inverseOnSurface = c.superficie,
        error = c.erro,
        onError = c.sobreErro,
        errorContainer = c.mentaSuave,
        onErrorContainer = c.erro,
        outline = c.bordaControle,
        outlineVariant = c.borda,
        scrim = c.tinta,
    )
}

// Atalhos para os tokens do tema em uso (mesmos nomes do tokens.json, em CamelCase).
val Primaria: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.primaria
val PrimariaForte: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.primariaForte
val SobrePrimaria: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobrePrimaria
val Menta: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.menta
val MentaSuave: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.mentaSuave
val TealMedio: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.tealMedio
val Acento: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.acento
val SobreAcento: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobreAcento
val Fundo: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.fundo
val Superficie: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.superficie
val Tinta: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.tinta
val TintaSuave: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.tintaSuave
val Borda: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.borda
val BordaControle: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.bordaControle
val Foco: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.foco
val EstadoConsolidado: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.estadoConsolidado
val EstadoDesenvolvimento: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.estadoDesenvolvimento
val EstadoRevisar: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.estadoRevisar
val EstadoSemDados: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.estadoSemDados
val SobreConsolidado: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobreConsolidado
val SobreDesenvolvimento: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobreDesenvolvimento
val SobreRevisar: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobreRevisar
val Erro: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.erro
val SobreErro: Color @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.sobreErro
val TemaEscuro: Boolean @Composable @ReadOnlyComposable get() = LocalCoresLumini.current.escuro
