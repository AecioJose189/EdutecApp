package com.edutec.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.TextoPrincipal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/** Foto do Google (baixada uma vez por URL); sem foto ou sem rede, as iniciais. */
@Composable
fun FotoUsuario(nome: String, fotoUrl: String?, tamanho: Dp = 44.dp) {
    var foto by remember(fotoUrl) { mutableStateOf(fotosBaixadas[fotoUrl]) }
    LaunchedEffect(fotoUrl) {
        if (fotoUrl == null || foto != null) return@LaunchedEffect
        foto = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URI(fotoUrl).toURL().openStream().use { it.readBytes() }
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }?.also { fotosBaixadas[fotoUrl] = it }
    }
    Surface(shape = CircleShape, color = Pilula, modifier = Modifier.size(tamanho)) {
        val atual = foto
        if (atual != null) {
            Image(atual, contentDescription = nome, contentScale = ContentScale.Crop, modifier = Modifier.size(tamanho).clip(CircleShape))
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(iniciais(nome), color = TextoPrincipal, fontSize = (tamanho.value * 0.36f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val fotosBaixadas = mutableMapOf<String?, ImageBitmap>()

private fun iniciais(nome: String): String =
    nome.split(' ').filter { it.isNotBlank() }.let { p -> (p.firstOrNull()?.take(1).orEmpty() + if (p.size > 1) p.last().take(1) else "") }
        .uppercase().ifEmpty { "?" }
