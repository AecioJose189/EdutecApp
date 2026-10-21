@file:OptIn(ExperimentalLayoutApi::class)

package com.edutec.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.edutec.app.data.Aluno
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.DadosTurmaExemplo
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.Turma
import com.edutec.app.data.rotuloAlunos
import com.edutec.app.data.semDados
import com.edutec.app.ui.theme.AmareloTexto
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.BordaTracejada
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.CremeFundo
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaFundo
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.LaranjaFundo
import com.edutec.app.ui.theme.LaranjaTexto
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho

private val CantoCartao = RoundedCornerShape(24.dp)
private val Espaco = 14.dp
private val Abas = listOf("Grafo", "Atividades", "Materiais", "Estatísticas", "Turma")

/**
 * Tela de uma turma. Por enquanto é só visual: o que funciona de verdade é o botão
 * "Nova atividade" (e "Gerar atividade de reforço", que abre a mesma tela já com o conceito).
 */
@Composable
fun TurmaScreen(
    turma: Turma,
    onVoltar: () -> Unit,
    onNovaAtividade: (conceitosIniciais: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var aba by rememberSaveable { mutableStateOf(Abas.first()) }

    fun avisar(mensagem: String) = Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Fundo)
    ) {
        val expandido = maxWidth >= 1000.dp
        val margem = if (maxWidth >= 600.dp) 20.dp else 16.dp

        val cabecalho: @Composable () -> Unit = {
            Cabecalho(
                turma = turma,
                expandido = expandido,
                aba = aba,
                onAba = { aba = it; if (it != "Grafo") avisar("Aba \"$it\" ainda não implementada") },
                onVoltar = onVoltar,
                onNovaAtividade = { onNovaAtividade(emptySet()) },
            )
        }
        val painel: @Composable (Modifier) -> Unit = { mod ->
            PainelConceito(
                alunos = turma.alunos,
                onFechar = { avisar("Selecione um conceito no grafo") },
                onReforco = { onNovaAtividade(setOf("Regra da cadeia")) },
                onVerAlunos = { avisar("Lista de alunos com dificuldade") },
                preencherAltura = expandido,
                modifier = mod,
            )
        }

        val vazio: @Composable (Modifier) -> Unit = { mod ->
            // Só rola sozinho no tablet; no celular a coluna da tela já rola (rolagem dentro de rolagem quebra).
            EstadoVazio(onNovaAtividade = { onNovaAtividade(emptySet()) }, rolavel = expandido, modifier = mod)
        }

        if (turma.semDados) {
            // Turma nova: nada de grafo nem alunos de exemplo, só os primeiros passos.
            if (expandido) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = margem, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Espaco),
                ) {
                    cabecalho()
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Espaco)) {
                        ListaAlunos(emptyList(), Modifier.width(290.dp).fillMaxHeight())
                        vazio(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = margem, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Espaco),
                ) {
                    cabecalho()
                    vazio(Modifier.fillMaxWidth())
                    ListaAlunos(emptyList(), Modifier.fillMaxWidth())
                }
            }
        } else if (expandido) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = margem, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Espaco),
            ) {
                cabecalho()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Espaco),
                ) {
                    ListaAlunos(DadosTurmaExemplo.alunos, Modifier.width(290.dp).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Espaco),
                    ) {
                        CartaoGrafo(Modifier.fillMaxWidth().weight(1f))
                        LinhaDoTempo(Modifier.fillMaxWidth())
                    }
                    painel(Modifier.width(320.dp).fillMaxHeight())
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = margem, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Espaco),
            ) {
                cabecalho()
                CartaoGrafo(Modifier.fillMaxWidth().height(420.dp))
                LinhaDoTempo(Modifier.fillMaxWidth())
                painel(Modifier.fillMaxWidth())
                ListaAlunos(DadosTurmaExemplo.alunos, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cabeçalho (voltar, título, abas, nova atividade)
// ---------------------------------------------------------------------------

@Composable
private fun Cabecalho(
    turma: Turma,
    expandido: Boolean,
    aba: String,
    onAba: (String) -> Unit,
    onVoltar: () -> Unit,
    onNovaAtividade: () -> Unit,
) {
    val botaoNova: @Composable () -> Unit = {
        Button(
            onClick = onNovaAtividade,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nova atividade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoTopo(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onVoltar)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(turma.nome, color = TextoPrincipal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${turma.codigo} · ${turma.rotuloAlunos}", color = TextoSecundario, fontSize = 14.sp)
            }
            if (expandido) {
                Spacer(Modifier.width(36.dp))
                SeletorAbas(aba, onAba)
            }
            Spacer(Modifier.weight(1f))
            if (expandido) botaoNova()
        }
        if (!expandido) {
            Box(Modifier.horizontalScroll(rememberScrollState())) { SeletorAbas(aba, onAba) }
            Box(Modifier.fillMaxWidth()) { botaoNova() }
        }
    }
}

@Composable
private fun SeletorAbas(selecionada: String, onAba: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = Pilula) {
        Row(Modifier.padding(5.dp)) {
            Abas.forEach { aba ->
                val ativa = aba == selecionada
                Surface(
                    onClick = { onAba(aba) },
                    shape = RoundedCornerShape(50),
                    color = if (ativa) TextoPrincipal else Color.Transparent,
                ) {
                    Text(
                        text = aba,
                        color = if (ativa) Cartao else TextoCorpo,
                        fontSize = 15.sp,
                        fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lista de alunos
// ---------------------------------------------------------------------------

@Composable
private fun ListaAlunos(alunos: List<Aluno>, modifier: Modifier = Modifier) {
    if (alunos.isEmpty()) {
        Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alunos", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Nenhum aluno ainda", color = TextoCorpo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Os alunos aparecem aqui quando entrarem na turma com o código de convite.",
                    color = TextoSecundario, fontSize = 13.sp,
                )
            }
        }
        return
    }
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Pilula, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextoSecundario, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Buscar aluno", color = TextoSecundario, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(4.dp))

            // Linha "Turma toda" destacada
            Surface(shape = RoundedCornerShape(14.dp), color = AzulFundo, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Consolidado),
                        contentAlignment = Alignment.Center,
                    ) { Text("T", color = Cartao, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text("Turma toda", color = EtiquetaAzulTexto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            alunos.forEach { aluno ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Pilula),
                        contentAlignment = Alignment.Center,
                    ) { Text(aluno.nome.last().toString(), color = TextoCorpo, fontSize = 14.sp) }
                    Spacer(Modifier.width(14.dp))
                    Text(aluno.nome, color = TextoPrincipal, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SeloEstado(aluno.estado)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grafo da turma
// ---------------------------------------------------------------------------

/** Posição (fração da largura/altura) e diâmetro de cada nó do grafo. */
private data class No(val nome: String, val x: Float, val y: Float, val diametro: Dp, val estado: EstadoConceito?)

private val Nos = listOf(
    No("Funções", 0.11f, 0.46f, 64.dp, EstadoConceito.Consolidado),
    No("Limites", 0.30f, 0.27f, 58.dp, EstadoConceito.Consolidado),
    No("Continuidade", 0.30f, 0.66f, 44.dp, EstadoConceito.Desenvolvimento),
    No("Derivada", 0.51f, 0.42f, 62.dp, EstadoConceito.Desenvolvimento),
    No("Regra da cadeia", 0.72f, 0.19f, 60.dp, EstadoConceito.Revisar),
    No("Otimização", 0.88f, 0.40f, 38.dp, EstadoConceito.Desenvolvimento),
    No("Derivada implícita", 0.72f, 0.57f, 42.dp, EstadoConceito.Revisar),
    No("Integral", 0.51f, 0.81f, 32.dp, null), // ainda sem dados
)

private val Arestas = listOf(
    "Funções" to "Limites",
    "Funções" to "Continuidade",
    "Limites" to "Continuidade",
    "Limites" to "Derivada",
    "Derivada" to "Regra da cadeia",
    "Derivada" to "Otimização",
    "Derivada" to "Derivada implícita",
    "Regra da cadeia" to "Derivada implícita",
    "Derivada" to "Integral",
)

private const val NoSelecionado = "Regra da cadeia"

@Composable
private fun CartaoGrafo(modifier: Modifier = Modifier) {
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grafo da turma", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                // Quebra de linha em vez de rolagem: a legenda inteira sempre aparece.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EstadoConceito.entries.reversed().forEach { estado ->
                        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                            SeloEstado(estado, tamanho = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (estado == EstadoConceito.Desenvolvimento) "Em desenv." else estado.rotulo,
                                color = TextoCorpo, fontSize = 13.sp,
                            )
                        }
                    }
                    Text(
                        "Tamanho = nº de respostas", color = TextoSecundario, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            DesenhoGrafo(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun DesenhoGrafo(modifier: Modifier = Modifier) {
    val porNome = Nos.associateBy { it.nome }

    Box(modifier) {
        // Arestas com seta, cortadas na borda de cada nó.
        Canvas(Modifier.fillMaxSize()) {
            val cor = Color(0xFFBDB9B1)
            val traco = 1.5.dp.toPx()
            val tamanhoSeta = 8.dp.toPx()
            Arestas.forEach { (de, para) ->
                val a = porNome.getValue(de)
                val b = porNome.getValue(para)
                val pa = Offset(a.x * size.width, a.y * size.height)
                val pb = Offset(b.x * size.width, b.y * size.height)
                val dir = (pb - pa) / (pb - pa).getDistance()
                val inicio = pa + dir * (a.diametro.toPx() / 2 + 4.dp.toPx())
                val fim = pb - dir * (b.diametro.toPx() / 2 + 4.dp.toPx())
                drawLine(cor, inicio, fim, strokeWidth = traco, cap = StrokeCap.Round)

                val normal = Offset(-dir.y, dir.x)
                val base = fim - dir * tamanhoSeta
                val seta = Path().apply {
                    moveTo(fim.x, fim.y)
                    lineTo(base.x + normal.x * tamanhoSeta / 2, base.y + normal.y * tamanhoSeta / 2)
                    moveTo(fim.x, fim.y)
                    lineTo(base.x - normal.x * tamanhoSeta / 2, base.y - normal.y * tamanhoSeta / 2)
                }
                drawPath(seta, cor, style = Stroke(width = traco, cap = StrokeCap.Round))
            }
        }

        // Nós: cada filho é (círculo + rótulo) e é posicionado com o centro do círculo no ponto.
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = { Nos.forEach { NoDoGrafo(it) } },
        ) { medidas, restricoes ->
            val livres = restricoes.copy(minWidth = 0, minHeight = 0)
            val colocaveis = medidas.map { it.measure(livres) }
            layout(restricoes.maxWidth, restricoes.maxHeight) {
                colocaveis.forEachIndexed { i, p ->
                    val no = Nos[i]
                    val cx = (no.x * restricoes.maxWidth).toInt()
                    val cy = (no.y * restricoes.maxHeight).toInt()
                    p.place(cx - p.width / 2, cy - no.diametro.roundToPx() / 2)
                }
            }
        }
    }
}

@Composable
private fun NoDoGrafo(no: No) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val estado = no.estado
        if (estado == null) {
            Box(
                Modifier
                    .size(no.diametro)
                    .clip(CircleShape)
                    .background(Cartao)
                    .border(BorderStroke(1.5.dp, BordaTracejada), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("?", color = TextoSecundario, fontWeight = FontWeight.Bold) }
        } else if (no.nome == NoSelecionado) {
            // Nó em foco ganha um anel preto em volta.
            Box(
                Modifier.size(no.diametro).border(3.dp, TextoPrincipal, CircleShape).padding(5.dp),
                contentAlignment = Alignment.Center,
            ) { SeloEstado(estado, tamanho = no.diametro - 10.dp) }
        } else {
            SeloEstado(estado, tamanho = no.diametro)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            no.nome,
            color = TextoPrincipal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Linha do tempo (semanas)
// ---------------------------------------------------------------------------

@Composable
private fun LinhaDoTempo(modifier: Modifier = Modifier) {
    val semanaAtual = 8
    val total = 16
    val marcadas = listOf(2, 4, 6, 7, 8, 10, 13)

    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(TextoPrincipal),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Reproduzir", tint = Cartao) }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        "Semana $semanaAtual de $total",
                        color = TextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Arraste para ver a turma no tempo",
                        color = TextoSecundario, fontSize = 12.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val y = size.height / 2
                    fun xDa(semana: Int) = (semana - 1f) / (total - 1f) * size.width
                    drawLine(Trilho, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
                    drawLine(TextoPrincipal, Offset(0f, y), Offset(xDa(semanaAtual), y), 4.dp.toPx(), StrokeCap.Round)
                    marcadas.forEach { s ->
                        val c = Offset(xDa(s), y)
                        if (s == semanaAtual) {
                            drawCircle(Cartao, 11.dp.toPx(), c)
                            drawCircle(TextoPrincipal, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        } else if (s < semanaAtual) {
                            drawCircle(Cartao, 5.dp.toPx(), c)
                            drawCircle(TextoPrincipal, 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        } else {
                            drawCircle(BordaTracejada, 5.dp.toPx(), c)
                        }
                    }
                }
                Row {
                    listOf("Sem 1", "Sem 4", "Sem 8", "Sem 12", "Sem 16").forEachIndexed { i, rotulo ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Text(rotulo, color = TextoSecundario, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Painel do conceito selecionado
// ---------------------------------------------------------------------------

@Composable
private fun PainelConceito(
    alunos: Int,
    onFechar: () -> Unit,
    onReforco: () -> Unit,
    onVerAlunos: () -> Unit,
    preencherAltura: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // No tablet o conteúdo rola e os botões ficam fixos no rodapé do painel.
            Column(
                modifier = if (preencherAltura) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Conceito", color = TextoSecundario, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Surface(onClick = onFechar, shape = CircleShape, color = Pilula, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Fechar", tint = TextoPrincipal, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(NoSelecionado, color = TextoPrincipal, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(10.dp), color = EtiquetaVermelhaFundo) {
                    Text(
                        "! Revisar · domínio estimado 42%",
                        color = EtiquetaVermelhaTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, tint = TextoSecundario, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Chance estimada de a turma saber o conceito; não é nota.",
                        color = TextoSecundario, fontSize = 12.sp,
                    )
                }

                Surface(shape = RoundedCornerShape(18.dp), color = LaranjaFundo, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Domínio da turma nas últimas semanas", color = TextoCorpo, fontSize = 12.sp)
                        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 10.dp)) {
                            val pontos = listOf(0.15f, 0.2f, 0.45f, 0.75f, 0.95f)
                            val caminho = Path()
                            pontos.forEachIndexed { i, v ->
                                val p = Offset(i / (pontos.size - 1f) * size.width, size.height * (1 - v))
                                if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y)
                            }
                            drawPath(caminho, Revisar, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            drawCircle(Revisar, 4.dp.toPx(), Offset(size.width, size.height * (1 - pontos.last())))
                        }
                        Text("Subindo: +22 pontos desde a semana 5", color = LaranjaTexto, fontSize = 12.sp)
                    }
                }

                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Exemplo coerente com a turma: ~3 respostas por aluno neste conceito e, com domínio
                    // estimado de 42%, cerca de 58% dos alunos com dificuldade.
                    val respostas = alunos * 3
                    val comDificuldade = (alunos * 0.58).roundToInt()
                    MiniNumero("$respostas", "respostas", Modifier.weight(1f).fillMaxHeight())
                    MiniNumero(
                        if (alunos == 0) "0" else "$comDificuldade/$alunos", "com dificuldade",
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                Surface(shape = RoundedCornerShape(16.dp), color = CremeFundo, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        buildAnnotatedString {
                            append("Causa raiz provável: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Derivada") }
                            append(", pré-requisito ainda em desenvolvimento.")
                        },
                        color = AmareloTexto, fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Button(
                onClick = onReforco,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
            ) { Text("Gerar atividade de reforço", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onVerAlunos,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AzulFundo, contentColor = Consolidado),
            ) { Text("Ver alunos com dificuldade", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun MiniNumero(numero: String, legenda: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = Fundo, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(numero, color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(legenda, color = TextoSecundario, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Estado vazio (turma sem dados de aprendizagem)
// ---------------------------------------------------------------------------

@Composable
private fun EstadoVazio(onNovaAtividade: () -> Unit, rolavel: Boolean, modifier: Modifier = Modifier) {
    val passos = listOf(
        "Envie um material em PDF",
        "Gere e publique uma atividade",
        "Compartilhe o código de convite",
    )
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Box(
            (if (rolavel) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // "?" = sem dados, o mesmo símbolo dos conceitos ainda sem respostas.
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Cartao)
                        .border(BorderStroke(1.5.dp, BordaTracejada), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("?", color = TextoSecundario, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                Text(
                    "Ainda não há dados de aprendizagem nesta turma",
                    color = TextoPrincipal, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    "O grafo de conhecimento aparece quando os alunos começarem a responder às atividades.",
                    color = TextoCorpo, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    passos.forEachIndexed { i, passo ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(AzulFundo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", color = EtiquetaAzulTexto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(passo, color = TextoPrincipal, fontSize = 15.sp)
                        }
                    }
                }
                Button(
                    onClick = onNovaAtividade,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nova atividade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Turma · Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PreviewTurmaTablet() {
    EdutecTheme { TurmaScreen(DadosExemplo.turmas.first(), onVoltar = {}, onNovaAtividade = {}) }
}

@Preview(name = "Turma · Celular", widthDp = 400, heightDp = 1600, showBackground = true)
@Composable
private fun PreviewTurmaCelular() {
    EdutecTheme { TurmaScreen(DadosExemplo.turmas.first(), onVoltar = {}, onNovaAtividade = {}) }
}

private val TurmaNova = Turma(
    nome = "Programação I", codigo = "Turma A", alunos = 0,
    consolidado = 0, desenvolvimento = 0, revisar = 0, rodape = "Nenhuma atividade ainda",
)

@Preview(name = "Turma sem dados · Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PreviewTurmaVaziaTablet() {
    EdutecTheme { TurmaScreen(TurmaNova, onVoltar = {}, onNovaAtividade = {}) }
}

@Preview(name = "Turma sem dados · Celular", widthDp = 400, heightDp = 1100, showBackground = true)
@Composable
private fun PreviewTurmaVaziaCelular() {
    EdutecTheme { TurmaScreen(TurmaNova, onVoltar = {}, onNovaAtividade = {}) }
}
