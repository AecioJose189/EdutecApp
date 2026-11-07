package com.edutec.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Espaço e forma do Design System (docs/design/tokens.json, "spacing" e "radius"). Grade de 4 dp.

/** Entre ícone e texto colados. */
val Espaco1 = 4.dp
/** Entre chips e itens pequenos. */
val Espaco2 = 8.dp
/** Padding interno de chips e selos. */
val Espaco3 = 12.dp
/** Padding de botões e campos; espaço entre itens de lista. */
val Espaco4 = 16.dp
/** Padding de cartões; espaço entre cartões. */
val Espaco6 = 24.dp
/** Entre seções de uma tela. */
val Espaco8 = 32.dp
/** Margens externas da janela. */
val Espaco12 = 48.dp

/** Chips, selos retangulares, campos pequenos. */
val RaioSm = 8.dp
/** Botões e campos. */
val RaioMd = 14.dp
/** Cartões e painéis. */
val RaioLg = 22.dp

val FormaSm = RoundedCornerShape(RaioSm)
val FormaMd = RoundedCornerShape(RaioMd)
val FormaLg = RoundedCornerShape(RaioLg)
/** Abas em pílula, contadores e o fundo de grupo de abas (raio-pilula). */
val FormaPilula = RoundedCornerShape(percent = 50)

/** Borda dos cartões comuns (sem sombra): 1 dp na cor `borda`. */
val EspessuraBorda = 1.dp

val FormasLumini = Shapes(
    extraSmall = FormaSm,
    small = FormaSm,
    medium = FormaMd,
    large = FormaLg,
    extraLarge = FormaLg,
)
