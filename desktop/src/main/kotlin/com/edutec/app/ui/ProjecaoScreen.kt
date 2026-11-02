package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.Participante
import com.edutec.app.data.SessaoSala
import com.edutec.app.data.TokenQr
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.net.URI

/**
 * Projeção da sessão em sala (tela cheia): QR que muda a cada 15 s, contagem de quem entrou e de quem
 * enviou ao vivo, "Encerrar entrada" e "Encerrar sessão". Esc sai da tela cheia; de novo, sai da tela.
 * Na projeção não aparecem acertos (só quem entrou e quem enviou).
 */
@Composable
fun ProjecaoScreen(
    atividade: AtividadePublicada,
    nomeTurma: String,
    sessao: SessaoSala?,
    qr: TokenQr?,
    segundosParaTrocar: Int,
    participantes: List<Participante>,
    aoVivo: Boolean,
    erro: String?,
    onEncerrarEntrada: () -> Unit,
    onEncerrarSessao: () -> Unit,
    onSair: () -> Unit,
) {
    val janela = LocalJanela.current
    // Entra em tela cheia ao abrir e volta ao normal ao sair.
    DisposableEffect(janela) {
        val anterior = janela?.placement
        janela?.placement = WindowPlacement.Fullscreen
        onDispose { janela?.placement = if (anterior == WindowPlacement.Fullscreen) WindowPlacement.Floating else anterior ?: WindowPlacement.Floating }
    }
    TeclaVoltar {
        if (janela?.placement == WindowPlacement.Fullscreen) janela.placement = WindowPlacement.Floating else onSair()
    }
    var confirmarEncerrar by remember { mutableStateOf(false) }

    val enviaram = participantes.count { it.enviou }
    val encerrada = sessao?.aberta == false
    val entradaFechada = sessao?.entradaAberta == false

    BoxWithConstraints(Modifier.fillMaxSize().background(Fundo).padding(28.dp)) {
        val largo = maxWidth >= 1100.dp
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Topo: atividade, ao vivo, botões
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(atividade.titulo, color = TextoPrincipal, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(nomeTurma, color = TextoSecundario, fontSize = 17.sp)
                }
                if (!encerrada) {
                    IndicadorAoVivo(aoVivo)
                    Spacer(Modifier.width(16.dp))
                }
                if (!encerrada) {
                    if (!entradaFechada) {
                        OutlinedButton(onClick = onEncerrarEntrada, border = BorderStroke(1.dp, Consolidado),
                            modifier = Modifier.maoAoPassar()) { Text("Encerrar entrada", color = Consolidado) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Button(onClick = { confirmarEncerrar = true }, colors = ButtonDefaults.buttonColors(containerColor = Revisar),
                        modifier = Modifier.maoAoPassar()) { Text("Encerrar sessão") }
                    Spacer(Modifier.width(10.dp))
                }
                TextButton(onClick = onSair, modifier = Modifier.maoAoPassar()) {
                    Text(if (encerrada) "Ver a turma" else "Sair da projeção", color = TextoSecundario)
                }
            }
            erro?.let { Text(it, color = EtiquetaVermelhaTexto, fontSize = 15.sp) }

            val painelQr: @Composable (Modifier) -> Unit = { mod ->
                PainelQr(qr, segundosParaTrocar, sessao, enviaram, mod)
            }
            val painelAlunos: @Composable (Modifier) -> Unit = { mod ->
                PainelParticipantes(participantes, enviaram, mod)
            }
            if (largo) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    painelQr(Modifier.weight(1.3f).fillMaxHeight())
                    painelAlunos(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    painelQr(Modifier.fillMaxWidth().weight(1.3f))
                    painelAlunos(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    if (confirmarEncerrar) {
        AlertDialog(
            onDismissRequest = { confirmarEncerrar = false },
            containerColor = Cartao,
            title = { Text("Encerrar a sessão?") },
            text = {
                // O foco fica aqui para o Enter confirmar (Esc fecha o diálogo).
                val foco = remember { FocusRequester() }
                LaunchedEffect(Unit) { foco.requestFocus() }
                Text(
                    "Quem ainda não enviou não poderá mais enviar. $enviaram de ${participantes.size} já enviaram.",
                    modifier = Modifier.focusRequester(foco).focusable().onPreviewKeyEvent {
                        val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                        if (enter && it.type == KeyEventType.KeyDown) { confirmarEncerrar = false; onEncerrarSessao() }
                        enter
                    },
                )
            },
            confirmButton = {
                Button(onClick = { confirmarEncerrar = false; onEncerrarSessao() },
                    colors = ButtonDefaults.buttonColors(containerColor = Revisar), modifier = Modifier.maoAoPassar()) { Text("Encerrar") }
            },
            dismissButton = { TextButton(onClick = { confirmarEncerrar = false }, modifier = Modifier.maoAoPassar()) { Text("Continuar") } },
        )
    }
}

@Composable
private fun PainelQr(qr: TokenQr?, segundosParaTrocar: Int, sessao: SessaoSala?, enviaram: Int, modifier: Modifier) {
    val avisar = LocalAvisar.current
    val area = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(28.dp), color = Cartao, modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            when {
                sessao == null -> CircularProgressIndicator(color = Consolidado)
                !sessao.aberta -> MensagemGrande(
                    "Sessão encerrada",
                    if (enviaram > 0) "Obrigado! O grafo da turma já tem as respostas." else "Ninguém enviou respostas nesta sessão.",
                )
                !sessao.entradaAberta -> MensagemGrande("Entrada encerrada", "Quem já entrou ainda pode enviar as respostas.")
                qr == null -> CircularProgressIndicator(color = Consolidado)
                else -> {
                    Text("Aponte a câmera do celular para o código", color = TextoPrincipal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    CodigoQr(qr.url, Modifier.weight(1f).aspectRatio(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { segundosParaTrocar / ProjecaoViewModel.INTERVALO_QR.toFloat() },
                                modifier = Modifier.size(40.dp), color = Consolidado, trackColor = Trilho, strokeWidth = 4.dp,
                            )
                            Text("$segundosParaTrocar", color = TextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("O código muda a cada ${ProjecaoViewModel.INTERVALO_QR} s · ${host(qr.url)}", color = TextoSecundario, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        // Para ensaiar sem celular (scripts/simular-aluno.ts); o link vale ~40 s.
                        TextButton(onClick = {
                            area.setText(AnnotatedString(qr.url))
                            avisar("Link do QR copiado (vale ~40 s)")
                        }, modifier = Modifier.maoAoPassar()) { Text("Copiar link", color = TextoSecundario, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PainelParticipantes(participantes: List<Participante>, enviaram: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), color = Cartao, modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Contador(participantes.size, "entraram", Modifier.weight(1f))
                Contador(enviaram, "enviaram", Modifier.weight(1f))
            }
            if (participantes.isEmpty()) {
                Text("Ninguém entrou ainda.", color = TextoSecundario, fontSize = 18.sp)
            } else {
                // Quem ainda responde primeiro; depois quem enviou, pela ordem de envio.
                val ordem = participantes.sortedWith(compareBy<Participante>({ it.enviou }, { it.enviadaEm ?: it.iniciadaEm }))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ordem, key = { it.tentativaId }) { p ->
                        Row(Modifier.fillMaxWidth().background(if (p.enviou) AzulFundo else Pilula, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.nome, color = TextoPrincipal, fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                if (p.enviou) "✓ enviou" else "… respondendo",
                                color = if (p.enviou) EtiquetaAzulTexto else TextoCorpo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Contador(numero: Int, legenda: String, modifier: Modifier) {
    Column(modifier.background(Fundo, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("$numero", color = TextoPrincipal, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(legenda, color = TextoSecundario, fontSize = 17.sp)
    }
}

@Composable
private fun IndicadorAoVivo(ligado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (ligado) Consolidado else Trilho, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (ligado) "ao vivo" else "atualiza a cada 10 s", color = TextoSecundario, fontSize = 14.sp)
    }
}

@Composable
private fun MensagemGrande(titulo: String, texto: String) {
    Text(titulo, color = TextoPrincipal, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    Text(texto, color = TextoCorpo, fontSize = 20.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
}

private fun host(url: String) = runCatching { URI(url).host + (URI(url).port.takeIf { it > 0 }?.let { ":$it" } ?: "") }.getOrDefault("")

@Preview
@Composable
private fun PreviewProjecao() {
    EdutecTheme {
        ProjecaoScreen(
            AtividadePublicada("a", "Jogos de Soma Zero, Algoritmo Minimax e outros", "2026-10-28T12:00:00Z", 5, 30),
            "Inteligência Artificial · Turma A",
            SessaoSala("s", "a", aberta = true, entradaAberta = true, abertaEm = "2026-10-28T12:00:00Z"),
            TokenQr("t", "http://localhost:5173/s/exemplo", "2026-10-28T12:00:20Z"),
            9,
            listOf(
                Participante("1", "Ana Souza", "2026-10-28T12:01:00Z", enviadaEm = "2026-10-28T12:05:00Z", acertos = 3),
                Participante("2", "Bruno Lima", "2026-10-28T12:01:30Z"),
            ),
            aoVivo = true, erro = null, onEncerrarEntrada = {}, onEncerrarSessao = {}, onSair = {},
        )
    }
}
