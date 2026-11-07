package com.edutec.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.edutec.app.ui.theme.TemaEscuro

// Logo do Design System (docs/design/logo, empacotado em resources/logo pelo build.gradle.kts).

private const val HORIZONTAL_PRIMARIA = "logo/lumini-horizontal-primaria.svg"
private const val HORIZONTAL_BRANCO = "logo/lumini-horizontal-branco.svg"
private const val SIMBOLO_PRIMARIA = "logo/lumini-simbolo-primaria.svg"

/**
 * SVG das resources como Painter (vetorial: nítido em qualquer tamanho).
 *
 * Os arquivos do grupo de design só têm `viewBox`; sem `width`/`height` o Skia assume 100×100 e estica o
 * desenho fora da proporção. Por isso o tamanho do `viewBox` é posto no `<svg>` antes de carregar.
 */
fun carregarSvg(caminho: String, densidade: Density): Painter {
    val entrada = requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(caminho)) {
        "Recurso não encontrado: $caminho"
    }
    val texto = entrada.use { it.readBytes().decodeToString() }
    return loadSvgPainter(comTamanho(texto).byteInputStream(), densidade)
}

private val ViewBox = Regex("""viewBox="\s*[-\d.]+[\s,]+[-\d.]+[\s,]+([\d.]+)[\s,]+([\d.]+)\s*"""")

internal fun comTamanho(svg: String): String {
    val inicio = svg.indexOf("<svg")
    val fim = svg.indexOf('>', inicio)
    if (inicio < 0 || fim < 0) return svg
    val tag = svg.substring(inicio, fim)
    if (Regex("""\swidth=""").containsMatchIn(tag)) return svg
    val (largura, altura) = ViewBox.find(tag)?.destructured ?: return svg
    return svg.replaceRange(inicio, inicio + 4, """<svg width="$largura" height="$altura"""")
}

/**
 * Logo horizontal (símbolo + nome). Versão primaria sobre fundo claro; branco no tema escuro, onde a
 * haste em primaria sumiria. Mínimo de 120 dp de largura (≈ 44 dp de altura) pelo Design System.
 */
@Composable
fun LogoLumini(modifier: Modifier = Modifier, altura: Dp = 48.dp) {
    val densidade = LocalDensity.current
    val escuro = TemaEscuro
    val logo = remember(escuro, densidade) { carregarSvg(if (escuro) HORIZONTAL_BRANCO else HORIZONTAL_PRIMARIA, densidade) }
    val proporcao = logo.intrinsicSize.width / logo.intrinsicSize.height
    Image(logo, contentDescription = "Lumini", modifier = modifier.height(altura).aspectRatio(proporcao))
}

/** Símbolo (o L em forma de chave com os raios), para o ícone da janela. */
fun simboloLumini(densidade: Density): Painter = carregarSvg(SIMBOLO_PRIMARIA, densidade)
