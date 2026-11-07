package com.edutec.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Tipografia do Design System (docs/design/tokens.json, "type"). Arquivos em resources/font (licença OFL junto).

/** Títulos e números de destaque. */
val Sora = FontFamily(
    Font("font/Sora-Regular.ttf", FontWeight.Normal),
    Font("font/Sora-SemiBold.ttf", FontWeight.SemiBold),
    Font("font/Sora-Bold.ttf", FontWeight.Bold),
)

/** Todo o resto. */
val DmSans = FontFamily(
    Font("font/DMSans-Regular.ttf", FontWeight.Normal),
    Font("font/DMSans-Medium.ttf", FontWeight.Medium),
    Font("font/DMSans-SemiBold.ttf", FontWeight.SemiBold),
    Font("font/DMSans-Bold.ttf", FontWeight.Bold),
)

/**
 * Algarismos tabulares, para números que se comparam (domínio, contagens). Só a Sora tem a variante
 * `tnum` (o DM Sans não tem), por isso os números usam [Sora] junto: ver [EstiloNumero] e [comNumeros].
 */
const val AlgarismosTabulares = "tnum"

val EstiloTitulo1 = TextStyle(fontFamily = Sora, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
val EstiloTitulo2 = TextStyle(fontFamily = Sora, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
val EstiloTitulo3 = TextStyle(fontFamily = Sora, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
val EstiloNumero = TextStyle(
    fontFamily = Sora, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
    fontFeatureSettings = AlgarismosTabulares,
)
val EstiloCorpo = TextStyle(fontFamily = DmSans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
val EstiloCorpoForte = EstiloCorpo.copy(fontWeight = FontWeight.SemiBold)
val EstiloApoio = TextStyle(fontFamily = DmSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
val EstiloRotulo = TextStyle(fontFamily = DmSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)

/** Números que se comparam em qualquer tamanho: Sora com algarismos tabulares. */
fun TextStyle.comNumeros(): TextStyle = copy(fontFamily = Sora, fontFeatureSettings = AlgarismosTabulares)

val TipografiaLumini = Typography(
    displayLarge = EstiloTitulo1,
    displayMedium = EstiloTitulo1,
    displaySmall = EstiloTitulo2,
    headlineLarge = EstiloTitulo1,
    headlineMedium = EstiloTitulo2,
    headlineSmall = EstiloTitulo3,
    titleLarge = EstiloTitulo3,
    titleMedium = EstiloCorpoForte,
    titleSmall = EstiloApoio.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = EstiloCorpo,
    bodyMedium = EstiloApoio,
    bodySmall = EstiloApoio,
    labelLarge = EstiloCorpoForte,
    labelMedium = EstiloApoio.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = EstiloRotulo,
)
