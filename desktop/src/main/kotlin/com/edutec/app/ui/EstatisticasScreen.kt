@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AlunosPorEstado
import com.edutec.app.data.AtividadeRef
import com.edutec.app.data.ConceitoEvolucao
import com.edutec.app.data.ConfiancaTurma
import com.edutec.app.data.ContagemConfianca
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.EstatisticaConceito
import com.edutec.app.data.EstatisticasTurma
import com.edutec.app.data.PontoEvolucao
import com.edutec.app.data.ResumoTurma
import com.edutec.app.data.avisosPoucosDados
import com.edutec.app.data.destaques
import com.edutec.app.data.doConceito
import com.edutec.app.data.estadoDoServidor
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.BordaControle
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.Espaco8
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.EstadoConsolidado
import com.edutec.app.ui.theme.EstadoDesenvolvimento
import com.edutec.app.ui.theme.EstadoRevisar
import com.edutec.app.ui.theme.EstadoSemDados
import com.edutec.app.ui.theme.EstiloTitulo3
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.TealMedio
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import com.edutec.app.ui.theme.comNumeros
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

private fun pct(v: Double?): String = v?.let { "${(it * 100).roundToInt()}%" } ?: "—"

private const val DICA_CERTEZA = "Errou tendo certeza: sinal de que aprendeu errado, vale reexplicar."
private const val DICA_CHUTE = "Acertou chutando: domínio ainda frágil."

/**
 * Aba Estatísticas (UC13): resumo, tabela por conceito (piores primeiro), evolução por atividade e
 * confiança. Com pouca amostra os números aparecem com o aviso "poucos dados" e o motivo.
 */
@Composable
fun AbaEstatisticas(
    estatisticas: Carga<EstatisticasTurma>,
    incluirSimulacao: Boolean,
    onIncluirSimulacao: (Boolean) -> Unit,
    onAbrirConceito: (String) -> Unit,
    onTentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = FormaLg, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        when (estatisticas) {
            Carga.Carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primaria)
            }
            is Carga.Erro -> Column(
                Modifier.fillMaxSize().padding(Espaco8), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espaco3, Alignment.CenterVertically),
            ) {
                Text(estatisticas.mensagem, color = Erro, fontSize = 15.sp)
                TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo", color = Primaria) }
            }
            is Carga.Pronto -> ConteudoEstatisticas(estatisticas.valor, incluirSimulacao, onIncluirSimulacao, onAbrirConceito)
        }
    }
}

@Composable
private fun ConteudoEstatisticas(
    e: EstatisticasTurma,
    incluirSimulacao: Boolean,
    onIncluirSimulacao: (Boolean) -> Unit,
    onAbrirConceito: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Espaco6),
        verticalArrangement = Arrangement.spacedBy(Espaco6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estatísticas da turma", color = Tinta, fontFamily = Sora, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Domínio estimado: chance estimada de o aluno saber o conceito; não é nota." +
                        if (e.incluirSimulacao) " Inclui os alunos fictícios da demonstração." else " Só alunos reais.",
                    color = TintaSuave, fontSize = 13.sp,
                )
            }
            if (e.temSimulacao) {
                Text("Incluir simulação", color = Tinta, fontSize = 14.sp)
                Spacer(Modifier.width(Espaco2))
                Switch(
                    checked = incluirSimulacao, onCheckedChange = onIncluirSimulacao,
                    colors = SwitchDefaults.colors(checkedTrackColor = Primaria),
                    modifier = Modifier.maoAoPassar().semantics { contentDescription = "Incluir simulação" },
                )
            }
        }

        val avisos = avisosPoucosDados(e)
        if (avisos.isNotEmpty()) AvisoPoucosDados(avisos)

        Resumo(e.resumo, e.confianca)

        Secao("Por conceito", "Os piores primeiro. Clique num conceito para abri-lo no mapa.") {
            TabelaConceitos(e.conceitos, e.confianca, onAbrirConceito)
        }

        Secao(
            "Evolução por atividade",
            "Taxa de acerto da turma em cada atividade encerrada e o domínio estimado médio logo depois dela." +
                if (e.incluirSimulacao) " A simulação não tem sessões: só entra no domínio estimado." else "",
        ) {
            if (e.evolucao.isEmpty()) {
                Text("Nenhuma atividade encerrada ainda. Encerre a sessão em sala para ver a evolução.", color = TintaSuave, fontSize = 14.sp)
            } else {
                GraficoEvolucao(e.evolucao, Modifier.fillMaxWidth().height(240.dp))
                Spacer(Modifier.height(Espaco4))
                e.evolucao.forEach { DestaquesDaAtividade(it) }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, subtitulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
        Text(titulo, color = Tinta, style = EstiloTitulo3)
        Text(subtitulo, color = TintaSuave, fontSize = 13.sp)
        conteudo()
    }
}

@Composable
private fun AvisoPoucosDados(motivos: List<String>) {
    Surface(shape = FormaMd, color = MentaSuave, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco4), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeloPoucosDados()
                Spacer(Modifier.width(Espaco3))
                Text("Os números abaixo valem, mas ainda se baseiam em pouca amostra:", color = Tinta, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            motivos.forEach { Text("• $it", color = Tinta, fontSize = 13.sp) }
        }
    }
}

// ---------------------------------------------------------------------------
// Resumo
// ---------------------------------------------------------------------------

@Composable
private fun Resumo(r: ResumoTurma, confianca: ConfiancaTurma) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espaco3), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
        Numero("${r.alunos}", if (r.alunos == 1) "aluno" else "alunos")
        Numero("${r.atividadesEncerradas}",
            (if (r.atividadesEncerradas == 1) "atividade encerrada" else "atividades encerradas") +
                " (de ${r.atividades} ${if (r.atividades == 1) "publicada" else "publicadas"})")
        ComDica("Cada questão conta uma vez para cada conceito que ela avalia.") {
            Numero("${r.respostas}", "respostas registradas", info = true)
        }
        Numero(pct(r.media), "domínio estimado médio · mediana ${pct(r.mediana)}")
        Surface(shape = FormaMd, color = Fundo) {
            Column(Modifier.widthIn(min = 150.dp).padding(Espaco3), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
                ChipEstado(estadoDoServidor(r.estadoModa))
                Text("estado mais frequente", color = TintaSuave, fontSize = 12.sp)
            }
        }
        ComDica(DICA_CERTEZA) { Numero("${confianca.turma.errosComCerteza}", "erros com certeza", info = true) }
        ComDica(DICA_CHUTE) { Numero("${confianca.turma.acertosNoChute}", "acertos no chute", info = true) }
    }
}

@Composable
private fun Numero(valor: String, legenda: String, info: Boolean = false) {
    Surface(shape = FormaMd, color = Fundo) {
        Column(Modifier.widthIn(min = 150.dp).padding(Espaco3)) {
            Text(valor, color = Tinta, style = EstiloTitulo3.comNumeros().copy(fontWeight = FontWeight.Bold))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(legenda, color = TintaSuave, fontSize = 12.sp)
                if (info) IconeInfo()
            }
        }
    }
}

/** Explicação curta ao passar o mouse. */
@Composable
private fun ComDica(dica: String, conteudo: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(shape = FormaMd, color = Tinta, modifier = Modifier.widthIn(max = 280.dp)) {
                Text(dica, color = Superficie, fontSize = 13.sp, modifier = Modifier.padding(Espaco3))
            }
        },
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    ) { conteudo() }
}

// ---------------------------------------------------------------------------
// Tabela por conceito
// ---------------------------------------------------------------------------

private val ColConceito = 220.dp
private val ColNumero = 84.dp
private val ColModa = 170.dp
private val ColBarra = 220.dp
private val ColConfianca = 104.dp

@Composable
private fun TabelaConceitos(conceitos: List<EstatisticaConceito>, confianca: ConfiancaTurma, onAbrir: (String) -> Unit) {
    if (conceitos.isEmpty()) {
        Text("Nenhum conceito ainda. Publique uma atividade.", color = TintaSuave, fontSize = 14.sp)
        return
    }
    Column {
        Row(Modifier.padding(horizontal = Espaco2, vertical = Espaco2), verticalAlignment = Alignment.CenterVertically) {
            Cabecalho("Conceito", ColConceito)
            Cabecalho("Média", ColNumero)
            Cabecalho("Mediana", ColNumero)
            Cabecalho("Mais frequente", ColModa)
            Cabecalho("Respostas", ColNumero)
            Cabecalho("Alunos por estado", ColBarra)
            ComDica(DICA_CERTEZA) { Cabecalho("Erros com certeza", ColConfianca, info = true) }
            ComDica(DICA_CHUTE) { Cabecalho("Acertos no chute", ColConfianca, info = true) }
        }
        HorizontalDivider(color = Borda)
        conceitos.forEach { c ->
            val conf = confianca.doConceito(c.conceito)
            Row(
                Modifier.fillMaxWidth().clickable { onAbrir(c.conceito) }.maoAoPassar()
                    .semantics { contentDescription = "${c.conceito}: abrir no mapa" }
                    .padding(horizontal = Espaco2, vertical = Espaco3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.conceito, color = Primaria, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(ColConceito).padding(end = Espaco3))
                Celula(pct(c.media), ColNumero)
                Celula(pct(c.mediana), ColNumero)
                Box(Modifier.width(ColModa)) {
                    val moda = estadoDoServidor(c.estadoModa)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeloEstado(moda, tamanho = 18.dp)
                        Spacer(Modifier.width(Espaco2))
                        Text(moda?.rotulo ?: "Sem dados", color = Tinta, fontSize = 13.sp)
                    }
                }
                Celula("${c.respostas}", ColNumero)
                BarraEstados(c.alunosPorEstado, Modifier.width(ColBarra).padding(end = Espaco4))
                Celula("${conf.errosComCerteza}", ColConfianca)
                Celula("${conf.acertosNoChute}", ColConfianca)
            }
            HorizontalDivider(color = Borda)
        }
    }
}

@Composable
private fun Cabecalho(texto: String, largura: androidx.compose.ui.unit.Dp, info: Boolean = false) {
    Row(Modifier.width(largura), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = TintaSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
        if (info) IconeInfo()
    }
}

/** Sinal de que há explicação ao passar o mouse. */
@Composable
private fun IconeInfo() {
    Icon(Icons.Outlined.Info, contentDescription = null, tint = TintaSuave, modifier = Modifier.padding(start = 2.dp).size(13.dp))
}

@Composable
private fun Celula(texto: String, largura: androidx.compose.ui.unit.Dp) {
    Text(texto, color = Tinta, style = EstiloTitulo3.comNumeros().copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.width(largura))
}

/** Barra empilhada: quantos alunos em cada estado, com cor E símbolo + número embaixo. */
@Composable
fun BarraEstados(a: AlunosPorEstado, modifier: Modifier = Modifier) {
    val partes = listOf(
        EstadoConceito.Consolidado to a.consolidado,
        EstadoConceito.Desenvolvimento to a.desenvolvimento,
        EstadoConceito.Revisar to a.revisar,
    )
    val cores = mapOf(
        EstadoConceito.Consolidado to EstadoConsolidado,
        EstadoConceito.Desenvolvimento to EstadoDesenvolvimento,
        EstadoConceito.Revisar to EstadoRevisar,
    )
    val semDados = EstadoSemDados
    val descricao = "${a.consolidado} consolidado, ${a.desenvolvimento} em desenvolvimento, ${a.revisar} revisar, ${a.semDados} sem dados"
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = descricao }, verticalArrangement = Arrangement.spacedBy(Espaco1)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val total = a.total.coerceAtLeast(1)
            var x = 0f
            val raio = CornerRadius(3.dp.toPx())
            partes.forEach { (estado, n) ->
                if (n > 0) {
                    val w = size.width * n / total
                    drawRoundRect(cores.getValue(estado), Offset(x, 0f), Size(w - 1.dp.toPx(), size.height), raio)
                    x += w
                }
            }
            if (a.semDados > 0) {
                val w = size.width * a.semDados / total
                val e = 1.dp.toPx()
                drawRoundRect(semDados, Offset(x + e / 2, e / 2), Size(w - e, size.height - e), raio,
                    style = Stroke(e, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco2), verticalAlignment = Alignment.CenterVertically) {
            partes.forEach { (estado, n) -> ContagemEstado(estado, n) }
            ContagemEstado(null, a.semDados)
        }
    }
}

@Composable
private fun ContagemEstado(estado: EstadoConceito?, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeloEstado(estado, tamanho = 14.dp)
        Spacer(Modifier.width(2.dp))
        Text("$n", color = TintaSuave, style = EstiloTitulo3.comNumeros().copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

// ---------------------------------------------------------------------------
// Evolução por atividade
// ---------------------------------------------------------------------------

/**
 * Duas linhas, de 0 a 100%: domínio estimado médio logo depois (círculo cheio, primaria) e taxa de acerto
 * (quadrado, teal-medio, tracejada). Os valores ficam escritos nos pontos; a legenda diz qual é qual.
 */
@Composable
fun GraficoEvolucao(pontos: List<PontoEvolucao>, modifier: Modifier = Modifier) {
    val medidor = rememberTextMeasurer()
    val corDominio = Primaria
    val corAcerto = TealMedio
    val corEixo = Borda
    val corTexto = TintaSuave
    val corTinta = Tinta
    val estilo = TextStyle(fontSize = 11.sp, fontFamily = Sora)
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco6), verticalAlignment = Alignment.CenterVertically) {
            LegendaSerie("Domínio estimado médio logo depois", corDominio, quadrado = false)
            LegendaSerie("Taxa de acerto na atividade", corAcerto, quadrado = true)
        }
        Spacer(Modifier.height(Espaco2))
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics {
            contentDescription = pontos.joinToString("; ") {
                "Atividade ${it.atividade.numero}: acerto ${pct(it.taxaAcerto)}, domínio estimado ${pct(it.media)}"
            }
        }) {
            val esquerda = 48.dp.toPx()
            val baixo = 28.dp.toPx()
            val topo = 16.dp.toPx()
            val altura = size.height - baixo - topo
            val largura = size.width - esquerda - 24.dp.toPx()
            fun y(v: Double) = topo + altura * (1 - v.toFloat())
            fun x(i: Int) = esquerda + if (pontos.size == 1) largura / 2 else largura * i / (pontos.size - 1)
            listOf(0.0, 0.5, 1.0).forEach { v ->
                drawLine(corEixo, Offset(esquerda, y(v)), Offset(esquerda + largura, y(v)), 1.dp.toPx())
                val t = medidor.measure("${(v * 100).roundToInt()}%", estilo.copy(color = corTexto))
                drawText(t, topLeft = Offset(0f, y(v) - t.size.height / 2))
            }
            pontos.forEachIndexed { i, p ->
                val t = medidor.measure("Atividade ${p.atividade.numero}", estilo.copy(color = corTexto))
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, size.height - t.size.height))
            }
            // Rótulo de cada ponto do lado oposto ao da outra série (acima se este valor for o maior).
            fun serie(valores: List<Double?>, outra: List<Double?>, cor: Color, quadrado: Boolean, acimaNoEmpate: Boolean) {
                val pts = valores.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) to it } }
                pts.zipWithNext().forEach { (a, b) ->
                    drawLine(cor, a.first, b.first, 2.5.dp.toPx(),
                        pathEffect = if (quadrado) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
                }
                val r = 5.dp.toPx()
                valores.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val o = Offset(x(i), y(v))
                    if (quadrado) drawRect(cor, Offset(o.x - r, o.y - r), Size(2 * r, 2 * r)) else drawCircle(cor, r, o)
                    val t = medidor.measure("${(v * 100).roundToInt()}%", estilo.copy(color = corTinta, fontWeight = FontWeight.Bold))
                    val o2 = outra.getOrNull(i)
                    val acima = if (o2 == null || o2 == v) acimaNoEmpate else v > o2
                    val dy = if (acima) -r - 3.dp.toPx() - t.size.height else r + 3.dp.toPx()
                    val tx = (o.x - t.size.width / 2).coerceIn(esquerda, size.width - t.size.width)
                    drawText(t, topLeft = Offset(tx, (o.y + dy).coerceIn(0f, size.height - baixo - t.size.height)))
                }
            }
            val acertos = pontos.map { it.taxaAcerto }
            val dominios = pontos.map { it.media }
            serie(acertos, dominios, corAcerto, quadrado = true, acimaNoEmpate = false)
            serie(dominios, acertos, corDominio, quadrado = false, acimaNoEmpate = true)
        }
    }
}

@Composable
private fun LegendaSerie(texto: String, cor: Color, quadrado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            if (quadrado) drawRect(cor) else drawCircle(cor)
        }
        Spacer(Modifier.width(Espaco2))
        Text(texto, color = Tinta, fontSize = 13.sp)
    }
}

@Composable
private fun DestaquesDaAtividade(p: PontoEvolucao) {
    val d = destaques(p)
    Column(Modifier.fillMaxWidth().padding(vertical = Espaco2), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
        Text(tituloAtividade(p.atividade) + " · ${p.enviadas} ${if (p.enviadas == 1) "aluno enviou" else "alunos enviaram"}",
            color = Tinta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (d.subiram.isEmpty() && d.cairam.isEmpty() && d.emRevisar.isEmpty()) {
            Text(if (p.conceitos.all { it.mediaAnterior == null }) "Primeira medida destes conceitos." else "Sem mudanças grandes.",
                color = TintaSuave, fontSize = 13.sp)
        }
        LinhaDestaque("Mais subiram", d.subiram)
        LinhaDestaque("Caíram", d.cairam)
        LinhaDestaque("Em revisar", d.emRevisar, mostrarVariacao = false)
        HorizontalDivider(color = Borda, modifier = Modifier.padding(top = Espaco2))
    }
}

@Composable
private fun LinhaDestaque(rotulo: String, conceitos: List<ConceitoEvolucao>, mostrarVariacao: Boolean = true) {
    if (conceitos.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espaco3), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
        Text("$rotulo:", color = TintaSuave, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        conceitos.forEach { c ->
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                SeloEstado(estadoDoServidor(c.estado), tamanho = 14.dp)
                Spacer(Modifier.width(Espaco1))
                val v = c.variacao
                Text(
                    c.conceito + " " + pct(c.media) +
                        if (mostrarVariacao && v != null) " (${if (v > 0) "+" else "−"}${(kotlin.math.abs(v) * 100).roundToInt()} pts)" else "",
                    color = Tinta, fontSize = 13.sp,
                )
            }
        }
    }
}

fun tituloAtividade(a: AtividadeRef): String = if (a.numero > 0) "Atividade ${a.numero} · ${a.titulo}" else a.titulo

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

internal val EstatisticasExemplo = EstatisticasTurma(
    temSimulacao = true,
    resumo = ResumoTurma(alunos = 24, atividades = 3, atividadesEncerradas = 3, respostas = 612, media = 0.63, mediana = 0.66, estadoModa = "em_desenvolvimento"),
    conceitos = listOf(
        EstatisticaConceito("Regra da cadeia", 0.42, 0.40, "revisar", 110, AlunosPorEstado(3, 6, 13, 2)),
        EstatisticaConceito("Derivada implícita", 0.47, 0.45, "revisar", 50, AlunosPorEstado(4, 7, 10, 3)),
        EstatisticaConceito("Otimização", 0.55, 0.52, "em_desenvolvimento", 40, AlunosPorEstado(5, 11, 6, 2)),
        EstatisticaConceito("Derivada", 0.61, 0.64, "em_desenvolvimento", 130, AlunosPorEstado(8, 12, 4, 0)),
        EstatisticaConceito("Limites", 0.84, 0.86, "consolidado", 120, AlunosPorEstado(19, 4, 1, 0)),
        EstatisticaConceito("Integral", null, null, null, 0, AlunosPorEstado(0, 0, 0, 24)),
    ),
    evolucao = listOf(
        PontoEvolucao(AtividadeRef("a1", 1, "Limites"), enviadas = 22, taxaAcerto = 0.58, media = 0.46,
            conceitos = listOf(ConceitoEvolucao("Limites", 0.61, null, "em_desenvolvimento"))),
        PontoEvolucao(AtividadeRef("a2", 2, "Derivadas"), enviadas = 24, taxaAcerto = 0.64, media = 0.55,
            conceitos = listOf(ConceitoEvolucao("Limites", 0.84, 0.61, "consolidado"), ConceitoEvolucao("Derivada", 0.52, null, "em_desenvolvimento"))),
        PontoEvolucao(AtividadeRef("a3", 3, "Regra da cadeia"), enviadas = 23, taxaAcerto = 0.71, media = 0.63,
            conceitos = listOf(ConceitoEvolucao("Derivada", 0.61, 0.52, "em_desenvolvimento"), ConceitoEvolucao("Limites", 0.81, 0.84, "consolidado"),
                ConceitoEvolucao("Regra da cadeia", 0.42, null, "revisar"))),
    ),
    confianca = ConfiancaTurma(
        turma = ContagemConfianca(respostas = 300, comConfianca = 260, errosComCerteza = 21, acertosNoChute = 14),
        conceitos = listOf(ContagemConfianca(conceito = "Regra da cadeia", errosComCerteza = 9, acertosNoChute = 3),
            ContagemConfianca(conceito = "Derivada", errosComCerteza = 4, acertosNoChute = 6)),
    ),
)

@Preview
@Composable
private fun PreviewEstatisticas() {
    EdutecTheme {
        Box(Modifier.fillMaxSize().padding(Espaco6)) {
            AbaEstatisticas(Carga.Pronto(EstatisticasExemplo), false, {}, {}, {}, Modifier.fillMaxSize())
        }
    }
}

@Preview
@Composable
private fun PreviewEstatisticasPoucosDados() {
    val poucos = EstatisticasExemplo.copy(
        temSimulacao = false,
        resumo = ResumoTurma(alunos = 3, atividades = 1, atividadesEncerradas = 1, respostas = 18, media = 0.52, mediana = 0.5, estadoModa = "em_desenvolvimento"),
        evolucao = EstatisticasExemplo.evolucao.take(1),
        confianca = ConfiancaTurma(turma = ContagemConfianca(respostas = 9, comConfianca = 3, errosComCerteza = 1, acertosNoChute = 0)),
    )
    EdutecTheme {
        Box(Modifier.fillMaxSize().padding(Espaco6)) {
            AbaEstatisticas(Carga.Pronto(poucos), false, {}, {}, {}, Modifier.fillMaxSize())
        }
    }
}

@Preview
@Composable
private fun PreviewEvolucao() {
    EdutecTheme {
        Surface(color = Superficie, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
                GraficoEvolucao(EstatisticasExemplo.evolucao, Modifier.fillMaxWidth().height(260.dp))
                EstatisticasExemplo.evolucao.forEach { DestaquesDaAtividade(it) }
            }
        }
    }
}
