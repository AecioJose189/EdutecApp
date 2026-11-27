package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.EtapaPasso
import com.edutec.app.data.Passo
import com.edutec.app.data.SituacaoPasso
import com.edutec.app.data.proximosPassos
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.SobrePrimaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import org.jetbrains.compose.ui.tooling.preview.Preview

// ---------------------------------------------------------------------------
// Painel "Próximos passos" (topo da turma)
// ---------------------------------------------------------------------------

/** Texto do botão da etapa atual: diz o que acontece. */
fun Passo.acao(): String = when (this) {
    Passo.Convidar -> "Convidar alunos"
    Passo.Criar -> "Criar atividade"
    Passo.Publicar -> "Publicar para a turma"
    Passo.AbrirSessao -> "Abrir sessão em sala"
    Passo.VerMapa -> "Ver o mapa do conhecimento"
}

/**
 * Os cinco passos do caminho da turma, numa faixa: concluídos com ✓, o atual em destaque com o botão
 * dele, os seguintes apagados.
 */
@Composable
fun PainelProximosPassos(etapas: List<EtapaPasso>, onPasso: (Passo) -> Unit, modifier: Modifier = Modifier) {
    val atual = etapas.firstOrNull { it.situacao == SituacaoPasso.Atual }
    Surface(shape = FormaLg, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Espaco4, vertical = Espaco3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco2),
        ) {
            Text(
                "Próximos passos", color = Tinta, fontFamily = Sora, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = Espaco2),
            )
            etapas.forEachIndexed { i, etapa ->
                // A etapa atual ganha mais espaço (o detalhe "3 alunos entraram" não é cortado).
                ItemPasso(i + 1, etapa, Modifier.weight(if (etapa.situacao == SituacaoPasso.Atual) 1.4f else 1f))
            }
            if (atual != null) {
                Button(
                    onClick = { onPasso(atual.passo) },
                    shape = FormaMd,
                    colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                    contentPadding = PaddingValues(horizontal = Espaco4, vertical = Espaco2),
                    modifier = Modifier.padding(start = Espaco2).maoAoPassar(),
                ) { Text(atual.passo.acao(), fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ItemPasso(numero: Int, etapa: EtapaPasso, modifier: Modifier = Modifier) {
    val situacao = etapa.situacao
    val atual = situacao == SituacaoPasso.Atual
    val feito = situacao == SituacaoPasso.Concluido
    Row(
        modifier
            .clip(FormaMd)
            .then(if (atual) Modifier.background(MentaSuave).border(1.5.dp, Primaria, FormaMd) else Modifier)
            .padding(horizontal = Espaco2, vertical = Espaco2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    when {
                        feito || atual -> Modifier.background(Primaria)
                        else -> Modifier.border(1.5.dp, Borda, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (feito) {
                Icon(Icons.Filled.Check, contentDescription = "Concluído", tint = SobrePrimaria, modifier = Modifier.size(16.dp))
            } else {
                Text("$numero", color = if (atual) SobrePrimaria else TintaSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(Espaco2))
        Column {
            Text(
                etapa.passo.titulo,
                color = if (situacao == SituacaoPasso.Pendente) TintaSuave else Tinta,
                fontSize = 13.sp,
                fontWeight = if (atual) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            etapa.detalhe?.let { Text(it, color = TintaSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ---------------------------------------------------------------------------
// Aviso no topo da turma (depois de publicar / encerrar a sessão)
// ---------------------------------------------------------------------------

@Composable
fun FaixaAvisoTurma(aviso: AvisoTurma, onAcao: () -> Unit, onFechar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = FormaLg, color = MentaSuave, border = BorderStroke(1.dp, Primaria), modifier = modifier) {
        Row(
            Modifier.padding(start = Espaco4, end = Espaco2, top = Espaco2, bottom = Espaco2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Primaria), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = SobrePrimaria, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(Espaco3))
            Text(aviso.texto, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            aviso.acao?.let {
                Button(
                    onClick = onAcao, shape = FormaMd,
                    colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                    contentPadding = PaddingValues(horizontal = Espaco4, vertical = Espaco2),
                    modifier = Modifier.maoAoPassar(),
                ) { Text(it, fontWeight = FontWeight.SemiBold) }
            }
            IconButton(onClick = onFechar, modifier = Modifier.maoAoPassar()) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar aviso", tint = TintaSuave)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogos
// ---------------------------------------------------------------------------

/** Enter chama [onEnter] (o botão principal do diálogo), como pede o CLAUDE.md. */
@Composable
private fun TextoComEnter(texto: String, onEnter: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    Text(
        texto, color = TintaSuave, fontSize = 15.sp,
        modifier = Modifier.focusRequester(foco).focusable().onPreviewKeyEvent {
            val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
            if (enter && it.type == KeyEventType.KeyDown) onEnter()
            enter
        },
    )
}

/** "Abrir sessão em sala" numa turma sem alunos. */
@Composable
fun DialogoSemAlunos(onConvidar: () -> Unit, onFechar: () -> Unit) {
    val convidar = { onFechar(); onConvidar() }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Superficie,
        title = { Text("Ninguém entrou na turma ainda", color = Tinta, fontFamily = Sora, fontWeight = FontWeight.Bold) },
        text = { TextoComEnter("Convide os alunos antes de abrir a sessão. Eles entram pelo link ou pelo código do convite.", convidar) },
        confirmButton = {
            Button(onClick = convidar, colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                modifier = Modifier.maoAoPassar()) { Text("Convidar alunos") }
        },
        dismissButton = {
            TextButton(onClick = onFechar, modifier = Modifier.maoAoPassar()) { Text("Agora não", color = Primaria) }
        },
    )
}

/** "O que é domínio estimado?": explicação curta, sem fórmula. */
@Composable
fun DialogoDominioEstimado(onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Superficie,
        title = { Text("O que é domínio estimado?", color = Tinta, fontFamily = Sora, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
                TextoComEnter(
                    "É a chance estimada de o aluno saber o conceito. Ela é atualizada a cada resposta: " +
                        "acertos aumentam a chance e erros diminuem, considerando que dá para acertar no chute " +
                        "e errar por descuido.",
                    onFechar,
                )
                Text(
                    "Não é nota. Com poucas respostas a estimativa ainda muda bastante: por isso cada conceito " +
                        "mostra em quantas respostas ela se baseia, e com menos de 5 aparece o selo \"poucos dados\".",
                    color = TintaSuave, fontSize = 15.sp,
                )
                HorizontalDivider(color = Borda)
                Text("Na turma, o mapa mostra a média dos alunos em cada conceito:", color = Tinta, fontSize = 14.sp)
                LegendaEstados(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onFechar, colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                modifier = Modifier.maoAoPassar()) { Text("Entendi") }
        },
    )
}

/** Legenda dos estados: cor + símbolo, com o limiar de cada um. */
@Composable
fun LegendaEstados(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espaco2)) {
        val linhas = listOf(
            EstadoConceito.Consolidado to "Consolidado · 80% ou mais",
            EstadoConceito.Desenvolvimento to "Em desenvolvimento · de 50% a 79%",
            EstadoConceito.Revisar to "Revisar · abaixo de 50%",
            null to "Sem dados · ainda sem respostas",
        )
        linhas.forEach { (estado, texto) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeloEstado(estado, tamanho = 20.dp)
                Spacer(Modifier.width(Espaco3))
                Text(texto, color = Tinta, fontSize = 14.sp)
            }
        }
    }
}

/** Selo "poucos dados": o domínio estimado se baseia em menos de 5 respostas. */
@Composable
fun SeloPoucosDados(modifier: Modifier = Modifier) {
    Surface(shape = FormaMd, color = Fundo, border = BorderStroke(1.dp, Borda), modifier = modifier) {
        Text(
            "poucos dados", color = TintaSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Espaco2, vertical = Espaco1),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview
@Composable
private fun PreviewPassos() {
    EdutecTheme {
        Column(Modifier.background(Fundo).padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco4)) {
            PainelProximosPassos(proximosPassos(0, false, emptyList(), false), {}, Modifier.fillMaxWidth())
            PainelProximosPassos(proximosPassos(12, true, emptyList(), false), {}, Modifier.fillMaxWidth())
            FaixaAvisoTurma(
                AvisoTurma("Atividade publicada. Para a turma responder, abra a sessão em sala.", "Abrir sessão em sala"),
                {}, {}, Modifier.fillMaxWidth(),
            )
            FaixaAvisoTurma(AvisoTurma("Sessão encerrada · 12 alunos enviaram"), {}, {}, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Espaco4))
        }
    }
}

@Preview
@Composable
private fun PreviewDominioEstimado() {
    EdutecTheme { DialogoDominioEstimado(onFechar = {}) }
}

@Preview
@Composable
private fun PreviewSemAlunos() {
    EdutecTheme { DialogoSemAlunos(onConvidar = {}, onFechar = {}) }
}
