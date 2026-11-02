package com.edutec.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import kotlin.math.min

/** Matriz do QR (com 2 módulos de margem branca), gerada localmente com o ZXing. */
fun matrizQr(texto: String): BitMatrix = QRCodeWriter().encode(
    texto, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
)

/** QR desenhado módulo a módulo: nítido em qualquer tamanho (projetor incluído). */
@Composable
fun CodigoQr(texto: String, modifier: Modifier = Modifier) {
    val matriz = remember(texto) { matrizQr(texto) }
    Canvas(modifier) {
        val n = matriz.width
        // Módulo e origem em pixels inteiros: sem isso as bordas saem antisserrilhadas e aparecem
        // linhas cinza entre os módulos, que atrapalham a leitura (testado com o leitor do ZXing).
        val modulo = floor(min(size.width, size.height) / n).coerceAtLeast(1f)
        val lado = modulo * n
        val origem = Offset(floor((size.width - lado) / 2), floor((size.height - lado) / 2))
        drawRect(Color.White, origem, Size(lado, lado))
        for (y in 0 until n) for (x in 0 until n) {
            if (matriz[x, y]) drawRect(Color.Black, Offset(origem.x + x * modulo, origem.y + y * modulo), Size(modulo, modulo))
        }
    }
}
