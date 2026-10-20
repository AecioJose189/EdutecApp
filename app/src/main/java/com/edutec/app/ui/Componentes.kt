package com.edutec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.EstadoConceito
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EmDesenvolvimento
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.TextoPrincipal

fun EstadoConceito.cor(): Color = when (this) {
    EstadoConceito.Consolidado -> Consolidado
    EstadoConceito.Desenvolvimento -> EmDesenvolvimento
    EstadoConceito.Revisar -> Revisar
}

/** Círculo colorido com ✓, ~ ou ! indicando o estado de aprendizagem. */
@Composable
fun SeloEstado(estado: EstadoConceito, modifier: Modifier = Modifier, tamanho: Dp = 24.dp) {
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(estado.cor()),
        contentAlignment = Alignment.Center,
    ) {
        val fonte = (tamanho.value * 0.55f).sp
        when (estado) {
            EstadoConceito.Consolidado -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = estado.rotulo,
                tint = Cartao,
                modifier = Modifier.size(tamanho * 0.6f),
            )
            EstadoConceito.Desenvolvimento ->
                Text("~", color = TextoPrincipal, fontSize = fonte, fontWeight = FontWeight.Bold)
            EstadoConceito.Revisar ->
                Text("!", color = Cartao, fontSize = fonte, fontWeight = FontWeight.Bold)
        }
    }
}

/** Botão quadrado arredondado usado para voltar / fechar no topo das telas. */
@Composable
fun BotaoTopo(icone: ImageVector, descricao: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Pilula,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = descricao, tint = TextoPrincipal, modifier = Modifier.size(22.dp))
        }
    }
}
