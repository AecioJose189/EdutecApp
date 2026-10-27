@file:OptIn(ExperimentalLayoutApi::class)

package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.Aluno
import com.edutec.app.data.ConceitoGrafo
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.DadosTurmaExemplo
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.Turma
import com.edutec.app.data.rotuloAlunos
import com.edutec.app.data.semAtividade
import com.edutec.app.ui.theme.AmareloFundo
import com.edutec.app.ui.theme.AmareloTexto
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.BordaTracejada
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.CremeFundo
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EtiquetaAzulFundo
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaFundo
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val CantoCartao = RoundedCornerShape(24.dp)
private val Espaco = 14.dp
private val Abas = listOf("Grafo", "Atividades", "Materiais", "Estatísticas", "Turma")

/**
 * Tela de uma turma: grafo de conhecimento com o domínio estimado de cada conceito (dados reais da
 * turma-api), painel do conceito selecionado e lista de alunos (pseudônimos).
 * Turma sem atividade publicada mostra os primeiros passos em vez do grafo.
 */
@Composable
fun TurmaScreen(
    turma: Turma,
    grafo: EstadoGrafo,
    onVoltar: () -> Unit,
    onNovaAtividade: (conceitosIniciais: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    onTentarDeNovo: () -> Unit = {},
    /** Botão de demonstração "Simular respostas da turma"; null esconde o botão. */
    onSimular: (() -> Unit)? = null,
    simulando: Boolean = false,
) {
    var aba by rememberSaveable { mutableStateOf(Abas.first()) }

    val avisar = LocalAvisar.current

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
        val colunaTablet = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = margem, vertical = 12.dp)
        val colunaCelular = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = margem, vertical = 12.dp)

        val dados = (grafo as? EstadoGrafo.Pronto)?.grafo
        if (turma.semAtividade || dados == null) {
            // Sem atividade publicada (primeiros passos), ou o grafo ainda carregando / com erro.
            val centro: @Composable (Modifier) -> Unit = { mod ->
                when {
                    turma.semAtividade -> EstadoVazio(
                        onNovaAtividade = { onNovaAtividade(emptySet()) },
                        // Só rola sozinho no tablet; no celular a coluna da tela já rola.
                        rolavel = expandido,
                        modifier = mod,
                    )
                    grafo is EstadoGrafo.Erro -> ErroGrafo(grafo.mensagem, onTentarDeNovo, mod)
                    else -> CarregandoGrafo(mod)
                }
            }
            if (expandido) {
                Column(colunaTablet, verticalArrangement = Arrangement.spacedBy(Espaco)) {
                    cabecalho()
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Espaco)) {
                        ListaAlunos(emptyList(), rolavel = true, modifier = Modifier.width(290.dp).fillMaxHeight())
                        centro(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(colunaCelular, verticalArrangement = Arrangement.spacedBy(Espaco)) {
                    cabecalho()
                    centro(Modifier.fillMaxWidth())
                    ListaAlunos(emptyList(), rolavel = false, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            ConteudoGrafo(turma, dados, expandido, colunaTablet, colunaCelular, cabecalho, onNovaAtividade, onSimular, simulando)
        }
    }
}

@Composable
private fun ConteudoGrafo(
    turma: Turma,
    dados: GrafoTurma,
    expandido: Boolean,
    colunaTablet: Modifier,
    colunaCelular: Modifier,
    cabecalho: @Composable () -> Unit,
    onNovaAtividade: (Set<String>) -> Unit,
    onSimular: (() -> Unit)?,
    simulando: Boolean,
) {
    val avisar = LocalAvisar.current
    // Conceito selecionado: o tocado no grafo, ou o mais fraco da turma.
    var selecionado by rememberSaveable(turma.id) { mutableStateOf<String?>(null) }
    val conceito = selecionado?.let(dados::conceito) ?: dados.conceitoInicial()

    val cartaoGrafo: @Composable (Modifier) -> Unit = { mod ->
        CartaoGrafo(dados, conceito?.nome, onSelecionar = { selecionado = it }, modifier = mod)
    }
    val demonstracao: @Composable () -> Unit = {
        onSimular?.let { FaixaDemonstracao(it, simulando, Modifier.fillMaxWidth()) }
    }
    val painel: @Composable (Modifier) -> Unit = { mod ->
        if (conceito != null) {
            PainelConceito(
                conceito = conceito,
                grafo = dados,
                onReforco = { onNovaAtividade(setOf(conceito.nome)) },
                onVerAlunos = { avisar("Lista de alunos com dificuldade (próxima versão)") },
                preencherAltura = expandido,
                modifier = mod,
            )
        }
    }

    if (expandido) {
        Column(colunaTablet, verticalArrangement = Arrangement.spacedBy(Espaco)) {
            cabecalho()
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Espaco)) {
                ListaAlunos(dados.alunos, rolavel = true, modifier = Modifier.width(290.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Espaco)) {
                    cartaoGrafo(Modifier.fillMaxWidth().weight(1f))
                    demonstracao()
                }
                painel(Modifier.width(320.dp).fillMaxHeight())
            }
        }
    } else {
        Column(colunaCelular, verticalArrangement = Arrangement.spacedBy(Espaco)) {
            cabecalho()
            cartaoGrafo(Modifier.fillMaxWidth().height(460.dp))
            demonstracao()
            painel(Modifier.fillMaxWidth())
            ListaAlunos(dados.alunos, rolavel = false, modifier = Modifier.fillMaxWidth())
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
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
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
// Lista de alunos (só pseudônimos, RN13)
// ---------------------------------------------------------------------------

@Composable
private fun ListaAlunos(alunos: List<Aluno>, rolavel: Boolean, modifier: Modifier = Modifier) {
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
        Column(
            (if (rolavel) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Alunos (${alunos.size})", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Pseudônimos, com o pior estado de cada aluno.",
                color = TextoSecundario, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(4.dp))

            // Linha "Turma toda" destacada: o grafo mostra a turma inteira.
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Pilula),
                        contentAlignment = Alignment.Center,
                    ) { Text(aluno.nome.takeLast(2), color = TextoCorpo, fontSize = 12.sp) }
                    Spacer(Modifier.width(14.dp))
                    Text(aluno.nome, color = TextoPrincipal, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SeloEstado(aluno.estado)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grafo da turma (layout automático em camadas)
// ---------------------------------------------------------------------------

private val DiametroMinimo = 36.dp
private val DiametroMaximo = 72.dp
private val LarguraRotulo = 128.dp
/** Espaço abaixo do círculo reservado ao rótulo (até 3 linhas). */
private val AlturaRotulo = 52.dp

/** Tamanho = nº de respostas (raiz quadrada: a área cresce com as respostas), entre o mínimo e o máximo. */
private fun diametro(conceito: ConceitoGrafo, maxRespostas: Int): Dp =
    if (conceito.semDados || maxRespostas == 0) DiametroMinimo
    else DiametroMinimo + (DiametroMaximo - DiametroMinimo) * sqrt(conceito.respostas.toFloat() / maxRespostas)

@Composable
private fun CartaoGrafo(
    grafo: GrafoTurma,
    selecionado: String?,
    onSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        SeloSemDados(16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Sem dados", color = TextoCorpo, fontSize = 13.sp)
                    }
                    Text(
                        "Tamanho = nº de respostas", color = TextoSecundario, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            DesenhoGrafo(grafo, selecionado, onSelecionar, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun DesenhoGrafo(
    grafo: GrafoTurma,
    selecionado: String?,
    onSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val conceitos = grafo.conceitos
    val layout = remember(conceitos) { layoutEmCamadas(conceitos.associate { it.nome to it.prerequisitos }) }
    val maxRespostas = conceitos.maxOfOrNull { it.respostas } ?: 0
    val diametros = conceitos.associate { it.nome to diametro(it, maxRespostas) }

    BoxWithConstraints(modifier.padding(top = 12.dp)) {
        val largura = maxWidth
        val altura = maxHeight
        // Centro do círculo de cada nó, em dp. Na vertical, desconta o espaço do rótulo embaixo.
        fun centro(nome: String): Pair<Dp, Dp> {
            val p = layout.getValue(nome)
            val util = (altura - AlturaRotulo - 8.dp).coerceAtLeast(0.dp)
            return largura * p.x to (8.dp + util * p.y)
        }

        // Arestas pré-requisito → conceito, com seta, cortadas na borda de cada nó.
        Canvas(Modifier.fillMaxSize()) {
            val cor = Color(0xFFBDB9B1)
            val traco = 1.5.dp.toPx()
            val tamanhoSeta = 8.dp.toPx()
            conceitos.forEach { c ->
                c.prerequisitos.filter { it in layout }.forEach { pre ->
                    val (ax, ay) = centro(pre)
                    val (bx, by) = centro(c.nome)
                    val pa = Offset(ax.toPx(), ay.toPx())
                    val pb = Offset(bx.toPx(), by.toPx())
                    val distancia = (pb - pa).getDistance()
                    if (distancia == 0f) return@forEach
                    val dir = (pb - pa) / distancia
                    val inicio = pa + dir * (diametros.getValue(pre).toPx() / 2 + 4.dp.toPx())
                    val fim = pb - dir * (diametros.getValue(c.nome).toPx() / 2 + 4.dp.toPx())
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
        }

        // Nós: cada filho é (círculo + rótulo), posicionado com o centro do círculo no ponto do layout.
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                conceitos.forEach { c ->
                    NoDoGrafo(c, diametros.getValue(c.nome), c.nome == selecionado, onClick = { onSelecionar(c.nome) })
                }
            },
        ) { medidas, restricoes ->
            val livres = restricoes.copy(minWidth = 0, minHeight = 0)
            val colocaveis = medidas.map { it.measure(livres) }
            layout(restricoes.maxWidth, restricoes.maxHeight) {
                colocaveis.forEachIndexed { i, p ->
                    val c = conceitos[i]
                    val (cx, cy) = centro(c.nome)
                    p.place(cx.roundToPx() - p.width / 2, cy.roundToPx() - diametros.getValue(c.nome).roundToPx() / 2)
                }
            }
        }
    }
}

@Composable
private fun NoDoGrafo(conceito: ConceitoGrafo, diametro: Dp, selecionado: Boolean, onClick: () -> Unit) {
    val estado = conceito.estado
    val descricao = buildString {
        append(conceito.nome).append(": ")
        if (estado == null || conceito.semDados) append("sem dados")
        else append("${estado.rotulo}, domínio estimado ${porcento(conceito.media)}")
    }
    Column(
        modifier = Modifier
            .widthIn(max = LarguraRotulo)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = descricao }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Nó em foco ganha um anel preto em volta.
        Box(
            if (selecionado) Modifier.size(diametro).border(3.dp, TextoPrincipal, CircleShape).padding(5.dp)
            else Modifier.size(diametro),
            contentAlignment = Alignment.Center,
        ) {
            val interno = if (selecionado) diametro - 10.dp else diametro
            if (estado == null || conceito.semDados) SeloSemDados(interno) else SeloEstado(estado, tamanho = interno)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            conceito.nome,
            color = TextoPrincipal,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Conceito ainda sem respostas: círculo cinza com "?". */
@Composable
private fun SeloSemDados(tamanho: Dp) {
    Box(
        Modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(Pilula)
            .border(BorderStroke(1.5.dp, BordaTracejada), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = TextoSecundario, fontSize = (tamanho.value * 0.45f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CarregandoGrafo(modifier: Modifier = Modifier) {
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            CircularProgressIndicator(color = Consolidado)
            Text("Carregando o grafo da turma…", color = TextoCorpo, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErroGrafo(mensagem: String, onTentarDeNovo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(EtiquetaVermelhaFundo),
                contentAlignment = Alignment.Center,
            ) { Text("!", color = EtiquetaVermelhaTexto, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Não foi possível carregar o grafo", color = TextoPrincipal, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(mensagem, color = TextoCorpo, fontSize = 15.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onTentarDeNovo,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
            ) { Text("Tentar de novo", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Demonstração: simular respostas da turma
// ---------------------------------------------------------------------------

@Composable
private fun FaixaDemonstracao(onSimular: () -> Unit, simulando: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = Cartao, border = BorderStroke(1.dp, BordaTracejada),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Demonstração: simula 25 alunos respondendo",
                        color = TextoCorpo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (simulando) "Registrando as respostas e atualizando o domínio estimado…"
                        else "Pseudônimos, sem dados pessoais. Rodar de novo substitui a simulação anterior.",
                        color = TextoSecundario, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onSimular, enabled = !simulando) {
                    Text(
                        if (simulando) "Simulando…" else "Simular respostas da turma",
                        color = if (simulando) TextoSecundario else Consolidado, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (simulando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Consolidado, trackColor = Pilula)
        }
    }
}

// ---------------------------------------------------------------------------
// Painel do conceito selecionado
// ---------------------------------------------------------------------------

private fun porcento(valor: Double?): String = valor?.let { "${(it * 100).roundToInt()}%" } ?: "—"

private fun EstadoConceito.simbolo() = when (this) {
    EstadoConceito.Consolidado -> "✓"
    EstadoConceito.Desenvolvimento -> "~"
    EstadoConceito.Revisar -> "!"
}

@Composable
private fun PainelConceito(
    conceito: ConceitoGrafo,
    grafo: GrafoTurma,
    onReforco: () -> Unit,
    onVerAlunos: () -> Unit,
    preencherAltura: Boolean,
    modifier: Modifier = Modifier,
) {
    val estado = conceito.estado.takeUnless { conceito.semDados }
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // No tablet o conteúdo rola e os botões ficam fixos no rodapé do painel, separados por uma
            // linha: o que passa da altura fica claramente "para rolar", não escondido atrás dos botões.
            // Nova rolagem a cada conceito: o painel abre sempre no topo, com o nome completo visível.
            val rolagem = remember(conceito.nome) { ScrollState(0) }
            Column(
                modifier = if (preencherAltura) Modifier.weight(1f).verticalScroll(rolagem).padding(bottom = 4.dp) else Modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Conceito", color = TextoSecundario, fontSize = 14.sp)
                Text(conceito.nome, color = TextoPrincipal, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                val (fundo, cor) = when (estado) {
                    EstadoConceito.Revisar -> EtiquetaVermelhaFundo to EtiquetaVermelhaTexto
                    EstadoConceito.Desenvolvimento -> AmareloFundo to AmareloTexto
                    EstadoConceito.Consolidado -> EtiquetaAzulFundo to EtiquetaAzulTexto
                    null -> Pilula to TextoCorpo
                }
                Surface(shape = RoundedCornerShape(10.dp), color = fundo) {
                    Text(
                        if (estado == null) "? Sem dados · ainda sem respostas"
                        else "${estado.simbolo()} ${estado.rotulo} · domínio estimado ${porcento(conceito.media)}",
                        color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
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

                // Logo abaixo do estado: é a leitura principal do painel.
                CausaRaiz(conceito, grafo)

                if (estado == null) {
                    Text(
                        "O domínio estimado aparece quando os alunos responderem a questões deste conceito.",
                        color = TextoCorpo, fontSize = 14.sp,
                    )
                } else {
                    MiniNumero(
                        porcento(conceito.media),
                        "domínio estimado médio · mediana ${porcento(conceito.mediana)}" +
                            if (turmaDividida(conceito)) " · turma dividida" else "",
                        Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniNumero("${conceito.respostas}", "respostas", Modifier.weight(1f).fillMaxHeight())
                        MiniNumero(
                            "${conceito.alunosRevisar}/${conceito.alunos}", "alunos com dificuldade",
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (preencherAltura) HorizontalDivider(color = Trilho)

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

/** Média e mediana a mais de 15 pontos: parte da turma domina e parte não (a média esconde isso). */
private fun turmaDividida(conceito: ConceitoGrafo): Boolean {
    val media = conceito.media ?: return false
    val mediana = conceito.mediana ?: return false
    return kotlin.math.abs(media - mediana) > 0.15
}

/** "Causa raiz provável": o pré-requisito (direto ou indireto) mais fraco, se não estiver consolidado. */
@Composable
private fun CausaRaiz(conceito: ConceitoGrafo, grafo: GrafoTurma) {
    val causa = grafo.causaRaiz(conceito.nome)
    val texto = when {
        causa != null -> buildAnnotatedString {
            append("Causa raiz provável: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(causa.nome) }
            append(", pré-requisito ")
            append(if (causa.nome in conceito.prerequisitos) "direto" else "indireto")
            append(
                if (causa.estado == EstadoConceito.Revisar) " a revisar"
                else " ainda em desenvolvimento"
            )
            append(" (domínio estimado ${porcento(causa.media)}).")
        }
        conceito.semDados || conceito.estado == EstadoConceito.Consolidado -> null
        grafo.ancestrais(conceito.nome).isEmpty() -> null
        else -> buildAnnotatedString {
            append("Os pré-requisitos estão consolidados: a dificuldade parece estar no próprio conceito.")
        }
    } ?: return
    Surface(shape = RoundedCornerShape(16.dp), color = CremeFundo, modifier = Modifier.fillMaxWidth()) {
        Text(texto, color = AmareloTexto, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
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
// Estado vazio (turma sem atividade publicada)
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
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
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

private val TurmaComDados = DadosExemplo.turmas.first().copy(atividades = 3)
private val GrafoExemplo = EstadoGrafo.Pronto(DadosTurmaExemplo.grafo)

@Preview
@Composable
private fun PreviewTurmaTablet() {
    EdutecTheme { TurmaScreen(TurmaComDados, GrafoExemplo, onVoltar = {}, onNovaAtividade = {}, onSimular = {}) }
}

@Preview
@Composable
private fun PreviewTurmaCelular() {
    EdutecTheme { TurmaScreen(TurmaComDados, GrafoExemplo, onVoltar = {}, onNovaAtividade = {}, onSimular = {}) }
}

@Preview
@Composable
private fun PreviewTurmaSemRespostasTablet() {
    val semRespostas = GrafoTurma(DadosTurmaExemplo.grafo.conceitos.map { ConceitoGrafo(it.nome, it.prerequisitos) }, emptyList())
    EdutecTheme {
        TurmaScreen(
            TurmaComDados.copy(alunos = 0), EstadoGrafo.Pronto(semRespostas),
            onVoltar = {}, onNovaAtividade = {}, onSimular = {}, simulando = true,
        )
    }
}

private val TurmaNova = Turma(
    nome = "Programação I", codigo = "Turma A", alunos = 0,
    consolidado = 0, desenvolvimento = 0, revisar = 0, rodape = "Nenhuma atividade ainda",
)

@Preview
@Composable
private fun PreviewTurmaVaziaTablet() {
    EdutecTheme { TurmaScreen(TurmaNova, EstadoGrafo.Carregando, onVoltar = {}, onNovaAtividade = {}) }
}

@Preview
@Composable
private fun PreviewTurmaVaziaCelular() {
    EdutecTheme { TurmaScreen(TurmaNova, EstadoGrafo.Carregando, onVoltar = {}, onNovaAtividade = {}) }
}
