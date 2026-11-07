package com.edutec.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.CandidatoJuntar
import com.edutec.app.data.ConceitoGrafo
import com.edutec.app.data.DadosTurmaExemplo
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.PossivelDuplicata
import com.edutec.app.ui.theme.AmareloTexto
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.CremeFundo
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import org.jetbrains.compose.ui.tooling.preview.Preview

// UC05: juntar dois conceitos da turma que são o mesmo (nomes diferentes vindos de PDFs diferentes).

/** Aviso discreto acima do mapa: há conceitos com nomes parecidos que podem ser o mesmo. */
@Composable
fun AvisoDuplicatas(duplicatas: List<PossivelDuplicata>, onRevisar: () -> Unit, modifier: Modifier = Modifier) {
    if (duplicatas.isEmpty()) return
    val primeira = duplicatas.first()
    val texto = if (duplicatas.size == 1) {
        "≈ \"${primeira.conceitoA}\" e \"${primeira.conceitoB}\" têm nomes parecidos: podem ser o mesmo conceito."
    } else {
        "≈ ${duplicatas.size} pares de conceitos têm nomes parecidos e podem ser o mesmo conceito."
    }
    Surface(shape = RoundedCornerShape(16.dp), color = CremeFundo, modifier = modifier) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, color = AmareloTexto, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onRevisar, modifier = Modifier.maoAoPassar()) {
                Text("Revisar", color = AmareloTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * "Juntar com outro conceito…": escolhe o outro conceito (os de nome parecido primeiro) e confirma,
 * dizendo o que acontece. [onJuntar] recebe (origem, destino): a origem deixa de existir.
 * [onDistintos] marca um par parecido como conceitos diferentes (sai do aviso).
 */
@Composable
fun DialogoJuntarConceitos(
    conceito: ConceitoGrafo,
    grafo: GrafoTurma,
    juntando: Boolean,
    onJuntar: (origem: String, destino: String) -> Unit,
    onDistintos: (String, String) -> Unit,
    onFechar: () -> Unit,
    escolhidoInicial: String? = null,
) {
    val candidatos = remember(grafo, conceito.nome) { grafo.candidatosParaJuntar(conceito.nome) }
    var escolhido by rememberSaveable(conceito.nome) { mutableStateOf(escolhidoInicial) }
    val outro = escolhido?.let(grafo::conceito)

    if (outro == null) {
        EscolherConceito(conceito, candidatos, juntando, onEscolher = { escolhido = it }, onDistintos, onFechar)
    } else {
        ConfirmarJuntar(conceito, outro, juntando, onJuntar, onVoltar = { escolhido = null }, onFechar)
    }
}

@Composable
private fun EscolherConceito(
    conceito: ConceitoGrafo,
    candidatos: List<CandidatoJuntar>,
    ocupado: Boolean,
    onEscolher: (String) -> Unit,
    onDistintos: (String, String) -> Unit,
    onFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Cartao,
        title = { Text("Juntar \"${conceito.nome}\" com outro conceito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Use quando os dois são o mesmo conceito com nomes diferentes (por exemplo, vindos de PDFs diferentes).",
                    color = TextoCorpo, fontSize = 14.sp,
                )
                if (candidatos.isEmpty()) {
                    Text("A turma não tem outros conceitos.", color = TextoSecundario, fontSize = 14.sp)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(candidatos, key = { it.conceito.nome }) { c ->
                        LinhaCandidato(c, ocupado, onEscolher = { onEscolher(c.conceito.nome) },
                            onDistintos = { onDistintos(conceito.nome, c.conceito.nome) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onFechar, modifier = Modifier.maoAoPassar()) { Text("Cancelar") } },
    )
}

@Composable
private fun LinhaCandidato(c: CandidatoJuntar, ocupado: Boolean, onEscolher: () -> Unit, onDistintos: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Fundo,
        modifier = Modifier.fillMaxWidth().maoAoPassar().clickable(enabled = !ocupado, role = Role.Button, onClick = onEscolher),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(c.conceito.nome, color = TextoPrincipal, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (c.parecido) "≈ nome parecido · " else "") + respostasTexto(c.conceito.respostas),
                    color = if (c.parecido) AmareloTexto else TextoSecundario, fontSize = 12.sp,
                )
            }
            if (c.parecido) {
                TextButton(onClick = onDistintos, enabled = !ocupado, modifier = Modifier.maoAoPassar()) {
                    Text("São diferentes", fontSize = 12.sp, color = TextoSecundario)
                }
            }
        }
    }
}

private fun respostasTexto(n: Int) = when (n) {
    0 -> "sem respostas"
    1 -> "1 resposta"
    else -> "$n respostas"
}

@Composable
private fun ConfirmarJuntar(
    conceito: ConceitoGrafo,
    outro: ConceitoGrafo,
    juntando: Boolean,
    onJuntar: (origem: String, destino: String) -> Unit,
    onVoltar: () -> Unit,
    onFechar: () -> Unit,
) {
    // Nome que fica: por padrão, o que tem mais respostas (empate: o do painel).
    var manter by rememberSaveable(conceito.nome, outro.nome) {
        mutableStateOf(if (outro.respostas > conceito.respostas) outro.nome else conceito.nome)
    }
    val destino = if (manter == outro.nome) outro else conceito
    val origem = if (destino == outro) conceito else outro
    val confirmar = { if (!juntando) onJuntar(origem.nome, destino.nome) }

    AlertDialog(
        onDismissRequest = { if (!juntando) onFechar() },
        containerColor = Cartao,
        title = { Text("Juntar os dois conceitos?") },
        text = {
            // O foco fica aqui para o Enter confirmar (Esc fecha o diálogo).
            val foco = remember { FocusRequester() }
            LaunchedEffect(Unit) { foco.requestFocus() }
            Column(
                Modifier.focusRequester(foco).focusable().onPreviewKeyEvent {
                    val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                    if (enter && it.type == KeyEventType.KeyDown) confirmar()
                    enter
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Nome que fica:", color = TextoSecundario, fontSize = 13.sp)
                listOf(conceito, outro).forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().maoAoPassar()
                            .selectable(selected = manter == c.nome, enabled = !juntando, role = Role.RadioButton) { manter = c.nome },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = manter == c.nome, onClick = null, enabled = !juntando,
                            colors = RadioButtonDefaults.colors(selectedColor = Consolidado))
                        Text(c.nome, color = TextoPrincipal, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(
                    "\"${origem.nome}\" deixa de existir e passa a fazer parte de \"${destino.nome}\":",
                    color = TextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "as questões de \"${origem.nome}\" passam a avaliar \"${destino.nome}\";",
                        "os pré-requisitos dos dois são somados, e o que dependia de \"${origem.nome}\" passa a depender de \"${destino.nome}\";",
                        "${respostasTexto(origem.respostas).replaceFirstChar { it.uppercase() }} de \"${origem.nome}\" " +
                            "${if (origem.respostas == 1) "passa" else "passam"} a contar para \"${destino.nome}\", e o domínio " +
                            "estimado é recalculado com todas as respostas, na ordem em que foram dadas.",
                    ).forEach { Text("• $it", color = TextoCorpo, fontSize = 13.sp) }
                }
                Text("Não dá para desfazer.", color = TextoSecundario, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = confirmar,
                enabled = !juntando,
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                modifier = Modifier.maoAoPassar(),
            ) {
                if (juntando) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Cartao, strokeWidth = 2.dp)
                } else {
                    Text("Juntar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onVoltar, enabled = !juntando, modifier = Modifier.maoAoPassar()) { Text("Voltar") }
        },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val GrafoComDuplicatas = DadosTurmaExemplo.grafo.let { g ->
    val (a, b) = g.conceitos.take(2).map { it.nome }
    g.copy(possiveisDuplicatas = listOf(PossivelDuplicata(a, b, 0.8)))
}

@Preview
@Composable
private fun PreviewAvisoDuplicatas() {
    EdutecTheme { AvisoDuplicatas(GrafoComDuplicatas.possiveisDuplicatas, onRevisar = {}) }
}

@Preview
@Composable
private fun PreviewEscolherConceito() {
    EdutecTheme {
        DialogoJuntarConceitos(GrafoComDuplicatas.conceitos.first(), GrafoComDuplicatas, juntando = false,
            onJuntar = { _, _ -> }, onDistintos = { _, _ -> }, onFechar = {})
    }
}

@Preview
@Composable
private fun PreviewConfirmarJuntar() {
    EdutecTheme {
        DialogoJuntarConceitos(GrafoComDuplicatas.conceitos.first(), GrafoComDuplicatas, juntando = false,
            onJuntar = { _, _ -> }, onDistintos = { _, _ -> }, onFechar = {},
            escolhidoInicial = GrafoComDuplicatas.conceitos[1].nome)
    }
}
