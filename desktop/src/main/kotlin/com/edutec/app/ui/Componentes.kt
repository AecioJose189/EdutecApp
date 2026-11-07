package com.edutec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.EstadoConceito
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.EstadoConsolidado
import com.edutec.app.ui.theme.EstadoDesenvolvimento
import com.edutec.app.ui.theme.EstadoRevisar
import com.edutec.app.ui.theme.EstadoSemDados
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.FormaSm
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.RaioSm
import com.edutec.app.ui.theme.SobreConsolidado
import com.edutec.app.ui.theme.SobreDesenvolvimento
import com.edutec.app.ui.theme.SobreRevisar
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import kotlin.math.PI
import kotlin.math.roundToInt

/** Cor do estado de aprendizagem (tokens estado-*). Nunca sozinha: sempre com [simbolo]. */
@Composable
@ReadOnlyComposable
fun EstadoConceito.cor(): Color = when (this) {
    EstadoConceito.Consolidado -> EstadoConsolidado
    EstadoConceito.Desenvolvimento -> EstadoDesenvolvimento
    EstadoConceito.Revisar -> EstadoRevisar
}

/** Cor do símbolo e do texto sobre [cor] (tokens sobre-*). */
@Composable
@ReadOnlyComposable
fun EstadoConceito.corPorCima(): Color = when (this) {
    EstadoConceito.Consolidado -> SobreConsolidado
    EstadoConceito.Desenvolvimento -> SobreDesenvolvimento
    EstadoConceito.Revisar -> SobreRevisar
}

/** ✓ consolidado, ~ em desenvolvimento, ! revisar. Sem dados é [SIMBOLO_SEM_DADOS]. */
val EstadoConceito.simbolo: String
    get() = when (this) {
        EstadoConceito.Consolidado -> "✓"
        EstadoConceito.Desenvolvimento -> "~"
        EstadoConceito.Revisar -> "!"
    }

const val SIMBOLO_SEM_DADOS = "?"
const val ROTULO_SEM_DADOS = "Sem dados"

/**
 * Círculo do estado de aprendizagem com o símbolo no centro (✓ ~ !). `estado = null` é "sem dados":
 * contorno tracejado em estado-sem-dados, sem preenchimento (fundo superficie), com "?".
 */
@Composable
fun SeloEstado(estado: EstadoConceito?, modifier: Modifier = Modifier, tamanho: Dp = 24.dp) {
    val fonte = (tamanho.value * 0.55f).sp
    if (estado == null) {
        val contorno = EstadoSemDados
        Box(
            modifier
                .size(tamanho)
                .clip(CircleShape)
                .background(Superficie)
                .drawBehind { contornoTracejado(contorno, (if (tamanho < 24.dp) 1.5.dp else 2.dp).toPx()) }
                .semantics { contentDescription = ROTULO_SEM_DADOS },
            contentAlignment = Alignment.Center,
        ) {
            Text(SIMBOLO_SEM_DADOS, color = TintaSuave, fontSize = fonte, lineHeight = fonte, fontWeight = FontWeight.Bold)
        }
        return
    }
    Box(
        modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(estado.cor())
            .semantics { contentDescription = estado.rotulo },
        contentAlignment = Alignment.Center,
    ) {
        if (estado == EstadoConceito.Consolidado) {
            // O ✓ como ícone: o caractere não existe nas fontes do app e mudaria de desenho em cada sistema.
            Icon(Icons.Filled.Check, contentDescription = null, tint = estado.corPorCima(), modifier = Modifier.size(tamanho * 0.6f))
        } else {
            Text(estado.simbolo, color = estado.corPorCima(), fontSize = fonte, lineHeight = fonte, fontWeight = FontWeight.Bold)
        }
    }
}

/** Círculo tracejado na borda da área desenhada (nó e selo "sem dados"). */
fun DrawScope.contornoTracejado(cor: Color, espessura: Float) {
    val raio = (size.minDimension - espessura) / 2
    val circunferencia = 2 * PI.toFloat() * raio
    // Traços e vãos que fecham o círculo certinho (sem um traço cortado no fim).
    val passo = (espessura * 4.5f).coerceAtLeast(6f)
    val n = (circunferencia / passo).roundToInt().coerceAtLeast(6)
    val p = circunferencia / n
    drawCircle(
        cor, raio, style = Stroke(espessura, pathEffect = PathEffect.dashPathEffect(floatArrayOf(p * 0.6f, p * 0.4f))),
    )
}

/** Selo retangular com símbolo e nome do estado (ex.: "! Revisar"); `null` = "? Sem dados". */
@Composable
fun ChipEstado(estado: EstadoConceito?, modifier: Modifier = Modifier, texto: String? = null) {
    val rotulo = texto ?: estado?.rotulo ?: ROTULO_SEM_DADOS
    val simbolo = estado?.simbolo ?: SIMBOLO_SEM_DADOS
    val corTexto = estado?.corPorCima() ?: TintaSuave
    val contorno = EstadoSemDados
    Row(
        modifier
            .clip(FormaSm)
            .then(
                if (estado != null) Modifier.background(estado.cor())
                else Modifier.background(Superficie).drawBehind {
                    val e = 1.5.dp.toPx()
                    drawRoundRect(
                        contorno, topLeft = Offset(e / 2, e / 2), size = Size(size.width - e, size.height - e),
                        cornerRadius = CornerRadius(RaioSm.toPx()),
                        style = Stroke(e, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            )
            .padding(horizontal = Espaco3, vertical = Espaco1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco2),
    ) {
        Text(simbolo, color = corTexto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(rotulo, color = corTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Cursor de mão ao passar o mouse; o realce de hover vem da própria indicação do clique. */
fun Modifier.maoAoPassar(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/** Botão quadrado arredondado usado para voltar / fechar no topo das telas. */
@Composable
fun BotaoTopo(icone: ImageVector, descricao: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = FormaMd,
        color = MentaSuave,
        modifier = Modifier.size(52.dp).maoAoPassar(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = descricao, tint = Tinta, modifier = Modifier.size(22.dp))
        }
    }
}
