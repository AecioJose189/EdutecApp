package com.edutec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tela de entrada: "Entrar com Google" abre o navegador do sistema e espera o retorno. */
@Composable
fun LoginScreen(
    aviso: String?,
    entrando: Boolean,
    urlLogin: String?,
    erro: String?,
    onEntrar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avisar = LocalAvisar.current
    val area = LocalClipboardManager.current
    Box(modifier.fillMaxSize().background(Fundo), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = Cartao, modifier = Modifier.widthIn(max = 460.dp).padding(24.dp)) {
            Column(
                Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("LUMINI", color = TextoPrincipal, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    "Grafo de conhecimento da turma e domínio estimado de cada conceito.",
                    color = TextoSecundario, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                aviso?.let { Text(it, color = TextoCorpo, fontSize = 14.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(4.dp))
                if (entrando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Consolidado)
                        Spacer(Modifier.width(12.dp))
                        Text("Continue no navegador…", color = TextoCorpo, fontSize = 15.sp)
                    }
                    if (urlLogin != null) {
                        TextButton(onClick = {
                            area.setText(AnnotatedString(urlLogin))
                            avisar("Link copiado: cole no navegador")
                        }, modifier = Modifier.maoAoPassar()) { Text("O navegador não abriu? Copiar o link") }
                    }
                    TextButton(onClick = onCancelar, modifier = Modifier.maoAoPassar()) { Text("Cancelar", color = TextoSecundario) }
                } else {
                    Button(
                        onClick = onEntrar,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                        modifier = Modifier.fillMaxWidth().height(52.dp).maoAoPassar(),
                    ) { Text("Entrar com Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                erro?.let { Text(it, color = EtiquetaVermelhaTexto, fontSize = 14.sp, textAlign = TextAlign.Center) }
            }
        }
    }
}

/** Abrindo o app: lendo a sessão salva. */
@Composable
fun CarregandoSessao() {
    Box(Modifier.fillMaxSize().background(Fundo), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Consolidado)
    }
}

@Preview
@Composable
private fun PreviewLogin() {
    EdutecTheme { LoginScreen(null, entrando = false, urlLogin = null, erro = null, onEntrar = {}, onCancelar = {}) }
}

@Preview
@Composable
private fun PreviewLoginEsperando() {
    EdutecTheme {
        LoginScreen("Sua sessão expirou. Entre de novo para continuar de onde parou.", entrando = true,
            urlLogin = "https://exemplo", erro = null, onEntrar = {}, onCancelar = {})
    }
}
