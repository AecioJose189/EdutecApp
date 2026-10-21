@file:OptIn(ExperimentalLayoutApi::class)

package com.edutec.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.Conceito
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.DadosTurmaExemplo
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.PedidoGeracao
import com.edutec.app.data.Turma
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.BordaTracejada
import com.edutec.app.ui.theme.BotaoSugestao
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.LaranjaFundo
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.RoxoFundo
import com.edutec.app.ui.theme.RoxoSecundario
import com.edutec.app.ui.theme.RoxoTexto
import com.edutec.app.ui.theme.RoxoTitulo
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val CantoCartao = RoundedCornerShape(24.dp)
private val Espaco = 14.dp
private const val LimitePdfBytes = 20L * 1024 * 1024
// A Edge Function gera tudo numa chamada só (limite de 150 s): 5 por padrão, no máximo 10.
private const val QuantidadePadrao = 5
private const val QuantidadeMaxima = 10

private enum class Origem { Grafo, Pdf }

/** [valor] é o que a Edge Function gerar-atividade espera. */
private enum class Formato(val titulo: String, val descricao: String, val adjetivo: String, val valor: String) {
    MultiplaEscolha("Múltipla escolha", "Correção e feedback imediatos", "de múltipla escolha", "multipla_escolha"),
    Aberta("Aberta", "Corrigida pela IA com rubrica", "abertas", "aberta"),
    Mista("Mista", "Metade de cada tipo", "mistas", "mista"),
}

private val Dificuldades = listOf("Fácil", "Média", "Difícil")
private val ValorDificuldade = mapOf("Fácil" to "facil", "Média" to "media", "Difícil" to "dificil")
private val Tempos = listOf("Sem limite", "15 min", "30 min", "45 min", "60 min")

/** PDF escolhido pelo professor e o estado do envio (cópia para o armazenamento do app). */
private data class MaterialPdf(
    val uri: Uri,
    val nome: String,
    val tamanho: Long,
    val arquivoLocal: File? = null,
) {
    val enviado get() = arquivoLocal != null
}

@Composable
fun NovaAtividadeScreen(
    turma: Turma,
    onFechar: () -> Unit,
    modifier: Modifier = Modifier,
    conceitosIniciais: Set<String> = emptySet(),
    conceitos: List<Conceito> = DadosTurmaExemplo.conceitos,
    estado: EstadoGeracao = EstadoGeracao.Ocioso,
    onGerar: (PedidoGeracao) -> Unit = {},
    onTentarDeNovo: () -> Unit = {},
    onVoltar: () -> Unit = {},
) {
    val context = LocalContext.current
    fun avisar(mensagem: String) = Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()

    var origem by remember { mutableStateOf(Origem.Grafo) }
    val selecionados = remember { mutableStateListOf<String>().apply { addAll(conceitosIniciais) } }
    var pdf by remember { mutableStateOf<MaterialPdf?>(null) }
    var progressoEnvio by remember { mutableFloatStateOf(0f) }
    var formato by remember { mutableStateOf(Formato.Mista) }
    var quantidade by remember { mutableIntStateOf(QuantidadePadrao) }
    var dificuldade by remember { mutableStateOf("Média") }
    var permitirRefazer by remember { mutableStateOf(true) }
    var tempo by remember { mutableStateOf("30 min") }

    val seletorPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (nome, tamanho) = lerInfoArquivo(context, uri)
        when {
            !nome.endsWith(".pdf", ignoreCase = true) && context.contentResolver.getType(uri) != "application/pdf" ->
                avisar("Escolha um arquivo PDF")
            tamanho > LimitePdfBytes -> avisar("O PDF passa de 20 MB")
            else -> pdf = MaterialPdf(uri, nome, tamanho)
        }
    }

    // "Envia" o PDF copiando-o para o armazenamento interno do app (por enquanto não há servidor).
    val pdfAtual = pdf
    LaunchedEffect(pdfAtual) {
        if (pdfAtual == null || pdfAtual.enviado) return@LaunchedEffect
        progressoEnvio = 0f
        val destino = runCatching {
            withContext(Dispatchers.IO) { copiarParaApp(context, pdfAtual) { progressoEnvio = it } }
        }.getOrNull()
        delay(400) // deixa o fim da barra visível
        if (destino != null) {
            pdf = pdfAtual.copy(arquivoLocal = destino)
        } else {
            pdf = null
            avisar("Não foi possível enviar o PDF")
        }
    }

    val conceitosOrdenados = conceitos.filter { it.nome in selecionados }.map { it.nome }
    // Nesta versão só há material no servidor quando a origem é um PDF.
    val motivoDesabilitado = when {
        origem == Origem.Grafo -> "Para a demonstração, gere a partir de um PDF"
        pdf == null -> "Envie um PDF para gerar a atividade"
        pdf?.enviado != true -> "Aguarde o envio do PDF terminar"
        else -> null
    }
    val resumo = buildString {
        append("$quantidade questões ${formato.adjetivo} sobre ")
        append(
            when (origem) {
                Origem.Grafo -> conceitosOrdenados.joinToString(", ").ifEmpty { "[escolha os conceitos]" }
                Origem.Pdf -> pdf?.let { "o material \"${it.nome}\"" } ?: "[envie um PDF]"
            }
        )
        append(" · ${if (permitirRefazer) "refazer livre" else "tentativa única"}")
        append(" · ${if (tempo == "Sem limite") "sem limite de tempo" else tempo}")
        append(" · ${dificuldade.lowercase()}")
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Fundo)
    ) {
        val expandido = maxWidth >= 900.dp
        val margem = if (maxWidth >= 600.dp) 20.dp else 16.dp

        val cartaoOrigem: @Composable () -> Unit = {
            CartaoOrigem(
                origem = origem,
                onOrigem = { origem = it },
                conceitos = conceitos,
                selecionados = selecionados,
                onAlternar = { nome -> if (nome in selecionados) selecionados.remove(nome) else selecionados.add(nome) },
                onSelecionarRevisar = {
                    conceitos.filter { it.estado == EstadoConceito.Revisar && it.nome !in selecionados }
                        .forEach { selecionados.add(it.nome) }
                },
                pdf = pdf,
                progressoEnvio = progressoEnvio,
                onEscolherPdf = { seletorPdf.launch(arrayOf("application/pdf")) },
                onRemoverPdf = {
                    pdf?.arquivoLocal?.delete()
                    pdf = null
                },
            )
        }
        val cartaoFormato: @Composable () -> Unit = {
            CartaoFormato(
                formato = formato,
                onFormato = { formato = it },
                quantidade = quantidade,
                onQuantidade = { quantidade = it.coerceIn(1, QuantidadeMaxima) },
                dificuldade = dificuldade,
                onDificuldade = { dificuldade = it },
                compacto = !expandido,
            )
        }
        val painelLateral: @Composable (Modifier) -> Unit = { mod ->
            PainelTentativas(
                permitirRefazer = permitirRefazer,
                onPermitirRefazer = { permitirRefazer = it },
                tempo = tempo,
                onTempo = { tempo = it },
                resumo = resumo,
                motivoDesabilitado = motivoDesabilitado,
                onGerar = {
                    val material = pdf
                    val arquivo = material?.arquivoLocal
                    if (origem == Origem.Pdf && material != null && arquivo != null) {
                        onGerar(
                            PedidoGeracao(
                                arquivo = arquivo,
                                nomeArquivo = material.nome,
                                quantidade = quantidade,
                                formato = formato.valor,
                                dificuldade = ValorDificuldade.getValue(dificuldade),
                            )
                        )
                    }
                },
                onManual = { avisar("Criar questões manualmente (próxima etapa)") },
                preencherAltura = expandido,
                modifier = mod,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = margem, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Espaco),
        ) {
            val etapa = when (estado) {
                EstadoGeracao.Ocioso -> 2
                is EstadoGeracao.Gerando, is EstadoGeracao.Erro -> 3
                is EstadoGeracao.Concluido -> 4
            }
            Cabecalho(turma = turma, expandido = expandido, etapa = etapa, onFechar = onFechar)

            // O estado do formulário fica acima deste "when": Voltar/Tentar de novo não perdem as escolhas.
            when (estado) {
                is EstadoGeracao.Gerando -> GerandoConteudo(estado, onCancelar = onVoltar, modifier = Modifier.weight(1f))
                is EstadoGeracao.Erro -> ErroGeracaoConteudo(estado, onTentarDeNovo, onVoltar, Modifier.weight(1f))
                is EstadoGeracao.Concluido -> RevisaoConteudo(
                    estado, expandido,
                    onGerarNovas = { onGerar(estado.pedido.copy(forcarNova = true)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                EstadoGeracao.Ocioso -> if (expandido) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Espaco),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Espaco),
                        ) {
                            cartaoOrigem()
                            cartaoFormato()
                        }
                        painelLateral(Modifier.width(370.dp).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Espaco),
                    ) {
                        cartaoOrigem()
                        cartaoFormato()
                        painelLateral(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cabeçalho + etapas
// ---------------------------------------------------------------------------

@Composable
private fun Cabecalho(turma: Turma, expandido: Boolean, etapa: Int, onFechar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoTopo(Icons.Filled.Close, "Fechar", onFechar)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Nova atividade", color = TextoPrincipal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${turma.nome} · [${turma.codigo}]", color = TextoSecundario, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            if (expandido) Etapas(etapa)
        }
        if (!expandido) Box(Modifier.horizontalScroll(rememberScrollState())) { Etapas(etapa) }
    }
}

@Composable
private fun Etapas(concluidas: Int = 2) {
    val nomes = listOf("Origem", "Formato", "Gerando", "Revisão", "Publicar")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        nomes.forEachIndexed { i, nome ->
            val ativa = i < concluidas
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (ativa) Consolidado else Pilula),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        color = if (ativa) Cartao else TextoSecundario,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    nome,
                    color = if (ativa) TextoPrincipal else TextoSecundario,
                    fontSize = 14.sp,
                    fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// "Gerar a partir de" — conceitos do grafo ou PDF
// ---------------------------------------------------------------------------

@Composable
private fun CartaoOrigem(
    origem: Origem,
    onOrigem: (Origem) -> Unit,
    conceitos: List<Conceito>,
    selecionados: List<String>,
    onAlternar: (String) -> Unit,
    onSelecionarRevisar: () -> Unit,
    pdf: MaterialPdf?,
    progressoEnvio: Float,
    onEscolherPdf: () -> Unit,
    onRemoverPdf: () -> Unit,
) {
    Surface(shape = CantoCartao, color = Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Gerar a partir de",
                    color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                SeletorOrigem(origem, onOrigem)
            }

            when (origem) {
                Origem.Grafo -> ConteudoGrafo(conceitos, selecionados, onAlternar, onSelecionarRevisar)
                Origem.Pdf -> ConteudoPdf(pdf, progressoEnvio, onEscolherPdf, onRemoverPdf)
            }
        }
    }
}

@Composable
private fun SeletorOrigem(origem: Origem, onOrigem: (Origem) -> Unit) {
    // Em telas estreitas os rótulos completos quebram linha.
    val curto = LocalConfiguration.current.screenWidthDp < 600
    Surface(shape = RoundedCornerShape(50), color = Pilula) {
        Row(Modifier.padding(5.dp)) {
            OpcaoOrigem(if (curto) "Grafo" else "Conceitos do grafo", origem == Origem.Grafo, { onOrigem(Origem.Grafo) }) { cor ->
                Icon(Icons.Filled.Share, null, tint = cor, modifier = Modifier.size(18.dp))
            }
            OpcaoOrigem(if (curto) "PDF" else "Material em PDF", origem == Origem.Pdf, { onOrigem(Origem.Pdf) }) { cor ->
                IconeDocumento(cor)
            }
        }
    }
}

@Composable
private fun OpcaoOrigem(texto: String, ativa: Boolean, onClick: () -> Unit, icone: @Composable (Color) -> Unit) {
    val cor = if (ativa) Cartao else TextoCorpo
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (ativa) TextoPrincipal else Color.Transparent) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icone(cor)
            Spacer(Modifier.width(8.dp))
            Text(
                texto, color = cor, fontSize = 15.sp, maxLines = 1,
                fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Folha com canto dobrado (o pacote de ícones "core" não tem ícone de documento). */
@Composable
private fun IconeDocumento(cor: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Box(
        modifier.drawBehind {
            val t = 1.6.dp.toPx()
            val l = size.width * 0.18f
            val r = size.width * 0.82f
            val dobra = size.width * 0.25f
            val caminho = androidx.compose.ui.graphics.Path().apply {
                moveTo(l, t)
                lineTo(r - dobra, t)
                lineTo(r, t + dobra)
                lineTo(r, size.height - t)
                lineTo(l, size.height - t)
                close()
                moveTo(r - dobra, t)
                lineTo(r - dobra, t + dobra)
                lineTo(r, t + dobra)
            }
            drawPath(caminho, cor, style = Stroke(t))
        }
    )
}

@Composable
private fun ConteudoGrafo(
    conceitos: List<Conceito>,
    selecionados: List<String>,
    onAlternar: (String) -> Unit,
    onSelecionarRevisar: () -> Unit,
) {
    val aRevisar = conceitos.count { it.estado == EstadoConceito.Revisar }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (aRevisar > 0) {
            Surface(shape = RoundedCornerShape(16.dp), color = LaranjaFundo, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Sugestão: ") }
                            append("$aRevisar conceitos estão em \"revisar\" para a turma.")
                        },
                        color = EtiquetaVermelhaTexto, fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onSelecionarRevisar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BotaoSugestao),
                    ) { Text("Selecionar esses", fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        EstadoConceito.entries.forEach { estado ->
            val doGrupo = conceitos.filter { it.estado == estado }
            if (doGrupo.isEmpty()) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(estado.cor()))
                Spacer(Modifier.width(8.dp))
                Text(estado.rotulo, color = TextoCorpo, fontSize = 14.sp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                doGrupo.forEach { c ->
                    ChipConceito(c, selecionado = c.nome in selecionados, onClick = { onAlternar(c.nome) })
                }
            }
        }

        Text(
            "${selecionados.size} conceito(s) selecionado(s). Os pré-requisitos ajudam a ordenar as questões.",
            color = TextoCorpo, fontSize = 14.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = TextoSecundario, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Domínio = chance estimada de o aluno saber o conceito, não é nota.", color = TextoSecundario, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChipConceito(conceito: Conceito, selecionado: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selecionado) AzulFundo else Cartao,
        border = if (selecionado) BorderStroke(2.dp, Consolidado) else BorderStroke(1.dp, Trilho),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecionado) {
                Icon(Icons.Filled.Check, null, tint = EtiquetaAzulTexto, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                conceito.nome,
                color = if (selecionado) EtiquetaAzulTexto else TextoPrincipal,
                fontSize = 15.sp,
                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.width(8.dp))
            Text("${conceito.dominio}%", color = TextoSecundario, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConteudoPdf(
    pdf: MaterialPdf?,
    progressoEnvio: Float,
    onEscolherPdf: () -> Unit,
    onRemover: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Envie a apostila, lista ou slides da aula. A IA vai usar o conteúdo do PDF como base para as questões.",
            color = TextoCorpo, fontSize = 14.sp,
        )

        if (pdf == null) {
            // Área tracejada: toque para abrir o seletor de arquivos do sistema.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Fundo)
                    .clickable(onClick = onEscolherPdf)
                    .drawBehind {
                        val e = 2.dp.toPx()
                        drawRoundRect(
                            color = BordaTracejada,
                            topLeft = Offset(e / 2, e / 2),
                            size = Size(size.width - e, size.height - e),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(e, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(AzulFundo),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = Consolidado, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text("Selecionar PDF", color = Consolidado, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Apenas arquivos .pdf · até 20 MB", color = TextoSecundario, fontSize = 13.sp)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Fundo,
                border = BorderStroke(1.dp, Trilho),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(width = 44.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)).background(Revisar),
                            contentAlignment = Alignment.Center,
                        ) { Text("PDF", color = Cartao, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pdf.nome,
                                color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(formatarTamanho(pdf.tamanho), color = TextoSecundario, fontSize = 13.sp)
                        }
                        if (pdf.enviado) {
                            Icon(Icons.Filled.CheckCircle, "Enviado", tint = Consolidado, modifier = Modifier.size(24.dp))
                        }
                    }

                    if (pdf.enviado) {
                        Text("Enviado ao sistema. Pronto para gerar as questões.", color = Consolidado, fontSize = 13.sp)
                    } else {
                        LinearProgressIndicator(
                            progress = { progressoEnvio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = Consolidado,
                            trackColor = Trilho,
                        )
                        Text("Enviando… ${(progressoEnvio * 100).toInt()}%", color = TextoSecundario, fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onEscolherPdf, enabled = pdf.enviado) {
                            Text("Trocar arquivo", color = Consolidado, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onRemover, enabled = pdf.enviado) {
                            Text("Remover", color = Revisar, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formato das questões
// ---------------------------------------------------------------------------

@Composable
private fun CartaoFormato(
    formato: Formato,
    onFormato: (Formato) -> Unit,
    quantidade: Int,
    onQuantidade: (Int) -> Unit,
    dificuldade: String,
    onDificuldade: (String) -> Unit,
    compacto: Boolean,
) {
    Surface(shape = CantoCartao, color = Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Formato das questões", color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.Bold)

            if (compacto) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Formato.entries.forEach { f ->
                        OpcaoFormato(f, f == formato, { onFormato(f) }, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Formato.entries.forEach { f ->
                        OpcaoFormato(f, f == formato, { onFormato(f) }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantidade", color = TextoCorpo, fontSize = 14.sp)
                    Spacer(Modifier.width(14.dp))
                    BotaoContador("−", "Diminuir", enabled = quantidade > 1) { onQuantidade(quantidade - 1) }
                    Text(
                        "$quantidade",
                        color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(52.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    BotaoContador("+", "Aumentar", enabled = quantidade < QuantidadeMaxima) { onQuantidade(quantidade + 1) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dificuldade", color = TextoCorpo, fontSize = 14.sp)
                    Spacer(Modifier.width(14.dp))
                    Surface(shape = RoundedCornerShape(50), color = Pilula) {
                        Row(Modifier.padding(4.dp)) {
                            Dificuldades.forEach { d ->
                                val ativa = d == dificuldade
                                Surface(
                                    onClick = { onDificuldade(d) },
                                    shape = RoundedCornerShape(50),
                                    color = if (ativa) TextoPrincipal else Color.Transparent,
                                ) {
                                    Text(
                                        d,
                                        color = if (ativa) Cartao else TextoCorpo,
                                        fontSize = 15.sp,
                                        fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpcaoFormato(formato: Formato, selecionado: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selecionado) AzulFundo else Cartao,
        border = if (selecionado) BorderStroke(2.dp, Consolidado) else BorderStroke(1.dp, Trilho),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                formato.titulo,
                color = if (selecionado) EtiquetaAzulTexto else TextoPrincipal,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text(formato.descricao, color = if (selecionado) Consolidado else TextoSecundario, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BotaoContador(simbolo: String, descricao: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Pilula,
        modifier = Modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                simbolo,
                color = if (enabled) TextoPrincipal else BordaTracejada,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Painel lateral: tentativas, resumo e ações
// ---------------------------------------------------------------------------

@Composable
private fun PainelTentativas(
    permitirRefazer: Boolean,
    onPermitirRefazer: (Boolean) -> Unit,
    tempo: String,
    onTempo: (String) -> Unit,
    resumo: String,
    /** null = pode gerar; caso contrário, o botão fica desabilitado e o motivo aparece embaixo. */
    motivoDesabilitado: String?,
    onGerar: () -> Unit,
    onManual: () -> Unit,
    preencherAltura: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // No tablet as opções rolam e os botões ficam fixos no rodapé do painel.
            Column(
                modifier = if (preencherAltura) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Tentativas", color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.Bold)

                Surface(shape = RoundedCornerShape(18.dp), color = AzulFundo, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Permitir refazer",
                                color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = permitirRefazer,
                                onCheckedChange = onPermitirRefazer,
                                colors = SwitchDefaults.colors(checkedTrackColor = Consolidado),
                            )
                        }
                        Text(
                            if (permitirRefazer) "Ligado: o aluno vê o feedback e pode refazer quantas vezes quiser, " +
                                "sempre com variantes das questões."
                            else "Desligado: o aluno responde uma única vez e vê o feedback ao final.",
                            color = TextoCorpo, fontSize = 14.sp,
                        )
                    }
                }

                Text("Tempo máximo por tentativa", color = TextoCorpo, fontSize = 14.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Tempos.forEach { t ->
                        val ativo = t == tempo
                        Surface(
                            onClick = { onTempo(t) },
                            shape = RoundedCornerShape(50),
                            color = if (ativo) TextoPrincipal else Cartao,
                            border = if (ativo) null else BorderStroke(1.dp, Trilho),
                        ) {
                            Text(
                                t,
                                color = if (ativo) Cartao else TextoCorpo,
                                fontSize = 14.sp,
                                fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Surface(shape = RoundedCornerShape(18.dp), color = RoxoFundo, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("A IA vai gerar", color = RoxoTitulo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(resumo, color = RoxoTexto, fontSize = 15.sp)
                        Text(
                            "Prazo e liberação são definidos ao publicar, depois da revisão.",
                            color = RoxoSecundario, fontSize = 12.sp,
                        )
                    }
                }
            }

            Button(
                onClick = onGerar,
                enabled = motivoDesabilitado == null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.Star, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Gerar atividade", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            motivoDesabilitado?.let { motivo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = TextoSecundario, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(motivo, color = TextoSecundario, fontSize = 13.sp)
                }
            }
            Button(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pilula, contentColor = TextoPrincipal),
            ) { Text("Criar questões manualmente", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Arquivo: nome, tamanho e cópia para o app
// ---------------------------------------------------------------------------

private fun lerInfoArquivo(context: Context, uri: Uri): Pair<String, Long> {
    var nome = "material.pdf"
    var tamanho = -1L
    context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            if (!c.isNull(0)) nome = c.getString(0)
            if (!c.isNull(1)) tamanho = c.getLong(1)
        }
    }
    return nome to tamanho
}

/** Copia o PDF para files/materiais/ do app, reportando o progresso de 0 a 1. */
private fun copiarParaApp(context: Context, pdf: MaterialPdf, onProgresso: (Float) -> Unit): File {
    val pasta = File(context.filesDir, "materiais").apply { mkdirs() }
    val destino = File(pasta, "${System.currentTimeMillis()}_${pdf.nome.replace(Regex("[^\\w.\\- ]"), "_")}")
    val entrada = context.contentResolver.openInputStream(pdf.uri) ?: error("Arquivo indisponível")
    entrada.use { input ->
        destino.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var copiados = 0L
            while (true) {
                val lidos = input.read(buffer)
                if (lidos < 0) break
                output.write(buffer, 0, lidos)
                copiados += lidos
                if (pdf.tamanho > 0) onProgresso((copiados.toFloat() / pdf.tamanho).coerceAtMost(1f))
            }
        }
    }
    onProgresso(1f)
    return destino
}

private fun formatarTamanho(bytes: Long): String = when {
    bytes < 0 -> "Tamanho desconhecido"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Nova atividade · Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PreviewNovaAtividadeTablet() {
    EdutecTheme {
        NovaAtividadeScreen(
            DadosExemplo.turmas.first(), onFechar = {},
            conceitosIniciais = setOf("Regra da cadeia", "Derivada"),
        )
    }
}

@Preview(name = "Nova atividade · Celular", widthDp = 400, heightDp = 1800, showBackground = true)
@Composable
private fun PreviewNovaAtividadeCelular() {
    EdutecTheme { NovaAtividadeScreen(DadosExemplo.turmas.first(), onFechar = {}) }
}
