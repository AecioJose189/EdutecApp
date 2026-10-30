package com.edutec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.Convite
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario

/** "Convidar alunos": código curto em letras grandes, link com "Copiar" e QR do link. */
@Composable
fun ConviteDialogo(
    convite: Carga<Convite>,
    nomeTurma: String,
    onTrocarCodigo: () -> Unit,
    onFechar: () -> Unit,
    onTentarDeNovo: () -> Unit,
) {
    val avisar = LocalAvisar.current
    val area = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Cartao,
        title = { Text("Convidar alunos · $nomeTurma", fontWeight = FontWeight.Bold) },
        text = {
            when (convite) {
                Carga.Carregando -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Consolidado)
                }
                is Carga.Erro -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(convite.mensagem, color = EtiquetaVermelhaTexto)
                    TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo") }
                }
                is Carga.Pronto -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CodigoQr(convite.valor.link, Modifier.size(220.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(340.dp)) {
                        Text("Código da turma", color = TextoSecundario, fontSize = 14.sp)
                        Text(
                            convite.valor.codigo, color = TextoPrincipal, fontSize = 52.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, letterSpacing = 6.sp,
                        )
                        Text("Os alunos abrem o link (ou leem o QR) no celular e entram com o Google.", color = TextoCorpo, fontSize = 14.sp)
                        Surface(shape = RoundedCornerShape(12.dp), color = Pilula) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(convite.valor.link, color = TextoPrincipal, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        area.setText(AnnotatedString(convite.valor.link))
                                        avisar("Link do convite copiado")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                                    modifier = Modifier.maoAoPassar(),
                                ) { Text("Copiar") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar, modifier = Modifier.maoAoPassar()) { Text("Fechar") } },
        dismissButton = {
            if (convite is Carga.Pronto) {
                TextButton(onClick = onTrocarCodigo, modifier = Modifier.maoAoPassar()) {
                    Text("Trocar código", color = TextoSecundario)
                }
            }
        },
    )
}
