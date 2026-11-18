package com.edutec.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokens de cor do Design System (docs/design/tokens.json), um por tema.
// Só este arquivo e o Theme.kt têm cores literais; as telas usam os nomes de Theme.kt (Primaria, Tinta...).
// Ao mudar a paleta, mude aqui e no tokens.json.

// Claro
val PrimariaClaro = Color(0xFF0F5765)
val PrimariaForteClaro = Color(0xFF0B4651)
val SobrePrimariaClaro = Color(0xFFFEFEFE)
val MentaClaro = Color(0xFFA2D7C9)
val MentaSuaveClaro = Color(0xFFDCEFE9)
val TealMedioClaro = Color(0xFF709FA5)
val AcentoClaro = Color(0xFF02D496)
val SobreAcentoClaro = Color(0xFF12302F)
val FundoClaro = Color(0xFFF3F8F6)
val SuperficieClaro = Color(0xFFFEFEFE)
val TintaClaro = Color(0xFF12302F)
val TintaSuaveClaro = Color(0xFF4A6461)
val BordaClaro = Color(0xFFD5E3DF)
val BordaControleClaro = Color(0xFF5E8C92)
val FocoClaro = Color(0xFF0F5765)
val EstadoConsolidadoClaro = Color(0xFF0F5765)
val EstadoDesenvolvimentoClaro = Color(0xFFF2C14E)
val EstadoRevisarClaro = Color(0xFFB8461A)
val EstadoSemDadosClaro = Color(0xFF6F807D)
val SobreConsolidadoClaro = Color(0xFFFEFEFE)
val SobreDesenvolvimentoClaro = Color(0xFF12302F)
val SobreRevisarClaro = Color(0xFFFEFEFE)
val ErroClaro = Color(0xFFB3261E)

// Escuro
val PrimariaEscuro = Color(0xFFA2D7C9)
val PrimariaForteEscuro = Color(0xFFC3E6DC)
val SobrePrimariaEscuro = Color(0xFF0C1F22)
val MentaEscuro = Color(0xFF24504C)
val MentaSuaveEscuro = Color(0xFF183B3A)
val TealMedioEscuro = Color(0xFF709FA5)
val AcentoEscuro = Color(0xFF02D496)
val SobreAcentoEscuro = Color(0xFF0C1F22)
val FundoEscuro = Color(0xFF0C1F22)
val SuperficieEscuro = Color(0xFF132B2F)
val TintaEscuro = Color(0xFFE6F1EE)
val TintaSuaveEscuro = Color(0xFF9DB5B1)
val BordaEscuro = Color(0xFF24393C)
val BordaControleEscuro = Color(0xFF6F9AA0)
val FocoEscuro = Color(0xFFA2D7C9)
val EstadoConsolidadoEscuro = Color(0xFF1F7A88)
val EstadoDesenvolvimentoEscuro = Color(0xFFF2C14E)
val EstadoRevisarEscuro = Color(0xFFE8723F)
val EstadoSemDadosEscuro = Color(0xFF7F9894)
val SobreConsolidadoEscuro = Color(0xFFFEFEFE)
val SobreDesenvolvimentoEscuro = Color(0xFF12302F)
val SobreRevisarEscuro = Color(0xFF0C1F22)
val ErroEscuro = Color(0xFFFF8A80)

/** Os tokens de um tema. O tema em uso vem de [LocalCoresLumini] (ver Theme.kt). */
@Immutable
data class CoresLumini(
    val primaria: Color,
    val primariaForte: Color,
    val sobrePrimaria: Color,
    val menta: Color,
    val mentaSuave: Color,
    val tealMedio: Color,
    val acento: Color,
    val sobreAcento: Color,
    val fundo: Color,
    val superficie: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val borda: Color,
    val bordaControle: Color,
    val foco: Color,
    val estadoConsolidado: Color,
    val estadoDesenvolvimento: Color,
    val estadoRevisar: Color,
    val estadoSemDados: Color,
    val sobreConsolidado: Color,
    val sobreDesenvolvimento: Color,
    val sobreRevisar: Color,
    val erro: Color,
    /** Texto sobre [erro] (botão destrutivo). Não é token próprio: branco no claro, o fundo no escuro. */
    val sobreErro: Color,
    /** Para escolher a versão do logo (primaria no claro, branco no escuro). */
    val escuro: Boolean,
)

val CoresClaras = CoresLumini(
    primaria = PrimariaClaro,
    primariaForte = PrimariaForteClaro,
    sobrePrimaria = SobrePrimariaClaro,
    menta = MentaClaro,
    mentaSuave = MentaSuaveClaro,
    tealMedio = TealMedioClaro,
    acento = AcentoClaro,
    sobreAcento = SobreAcentoClaro,
    fundo = FundoClaro,
    superficie = SuperficieClaro,
    tinta = TintaClaro,
    tintaSuave = TintaSuaveClaro,
    borda = BordaClaro,
    bordaControle = BordaControleClaro,
    foco = FocoClaro,
    estadoConsolidado = EstadoConsolidadoClaro,
    estadoDesenvolvimento = EstadoDesenvolvimentoClaro,
    estadoRevisar = EstadoRevisarClaro,
    estadoSemDados = EstadoSemDadosClaro,
    sobreConsolidado = SobreConsolidadoClaro,
    sobreDesenvolvimento = SobreDesenvolvimentoClaro,
    sobreRevisar = SobreRevisarClaro,
    erro = ErroClaro,
    sobreErro = SobrePrimariaClaro,
    escuro = false,
)

val CoresEscuras = CoresLumini(
    primaria = PrimariaEscuro,
    primariaForte = PrimariaForteEscuro,
    sobrePrimaria = SobrePrimariaEscuro,
    menta = MentaEscuro,
    mentaSuave = MentaSuaveEscuro,
    tealMedio = TealMedioEscuro,
    acento = AcentoEscuro,
    sobreAcento = SobreAcentoEscuro,
    fundo = FundoEscuro,
    superficie = SuperficieEscuro,
    tinta = TintaEscuro,
    tintaSuave = TintaSuaveEscuro,
    borda = BordaEscuro,
    bordaControle = BordaControleEscuro,
    foco = FocoEscuro,
    estadoConsolidado = EstadoConsolidadoEscuro,
    estadoDesenvolvimento = EstadoDesenvolvimentoEscuro,
    estadoRevisar = EstadoRevisarEscuro,
    estadoSemDados = EstadoSemDadosEscuro,
    sobreConsolidado = SobreConsolidadoEscuro,
    sobreDesenvolvimento = SobreDesenvolvimentoEscuro,
    sobreRevisar = SobreRevisarEscuro,
    erro = ErroEscuro,
    sobreErro = FundoEscuro,
    escuro = true,
)

/** Cores fixas do QR code: preto sobre branco nos dois temas (leitores de QR esperam módulo escuro). */
val QrModulo = Color(0xFF000000)
val QrFundo = Color(0xFFFFFFFF)

/** Fundo de aba inativa dentro do grupo de abas. */
val Transparente = Color.Transparent
