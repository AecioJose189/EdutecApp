package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.Aluno
import com.edutec.app.data.HistoricoConceito
import com.edutec.app.data.RespostaHistorico
import com.edutec.app.data.estadoDominioLocal
import com.edutec.app.data.rotuloConfianca
import com.edutec.app.data.textoTreinoConceito
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.EstadoRevisar
import com.edutec.app.ui.theme.EstiloTitulo3
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import com.edutec.app.ui.theme.comNumeros
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

private fun pct(v: Double?): String = v?.let { "${(it * 100).roundToInt()}%" } ?: "—"

/** Faixa acima do mapa quando ele mostra um aluno (UC11): nome e "Voltar à turma toda". */
@Composable
fun FaixaMapaDoAluno(aluno: Aluno, onVoltar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = FormaLg, color = MentaSuave, border = BorderStroke(1.dp, Primaria), modifier = modifier) {
        Row(Modifier.padding(horizontal = Espaco4, vertical = Espaco2), verticalAlignment = Alignment.CenterVertically) {
            FotoUsuario(aluno.nome, null, tamanho = 28.dp)
            Spacer(Modifier.width(Espaco3))
            Column(Modifier.weight(1f)) {
                Text("Mapa de ${aluno.nome}", color = Tinta, fontFamily = Sora, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Domínio estimado em cada conceito. Clique num conceito para ver o histórico.", color = Tinta, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onVoltar, border = BorderStroke(1.dp, Primaria), shape = FormaMd,
                contentPadding = PaddingValues(horizontal = Espaco4, vertical = Espaco2), modifier = Modifier.maoAoPassar()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primaria, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Espaco2))
                Text("Voltar à turma toda", color = Primaria, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * UC12: histórico de um aluno num conceito. No topo os erros com certeza e os acertos no chute; depois
 * a evolução do domínio estimado resposta a resposta (gráfico) e cada resposta em ordem.
 */
@Composable
fun PainelHistorico(
    aluno: Aluno,
    conceito: String,
    historico: Carga<HistoricoConceito>?,
    onTentarDeNovo: () -> Unit,
    onExplicarDominio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = FormaLg, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
            Text("${aluno.nome} · histórico", color = TintaSuave, fontSize = 14.sp)
            Text(conceito, color = Tinta, fontFamily = Sora, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            when (historico) {
                null, Carga.Carregando -> Box(Modifier.fillMaxWidth().padding(Espaco6), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Primaria)
                }
                is Carga.Erro -> {
                    Text(historico.mensagem, color = Erro, fontSize = 14.sp)
                    TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo", color = Primaria) }
                }
                is Carga.Pronto -> ConteudoHistorico(historico.valor, onExplicarDominio)
            }
        }
    }
}

@Composable
private fun ConteudoHistorico(h: HistoricoConceito, onExplicarDominio: () -> Unit) {
    if (h.respostas.isEmpty()) {
        ChipEstado(null, texto = "Sem dados · nenhuma resposta neste conceito")
        Text("O domínio estimado aparece quando o aluno responder a questões deste conceito.", color = TintaSuave, fontSize = 14.sp)
        if (h.treino.treinos > 0) LinhaTreino(h)
        return
    }
    val ultimo = h.respostas.last().dominio
    ChipEstado(estadoDominioLocal(ultimo), texto = "${estadoDominioLocal(ultimo).rotulo} · domínio estimado ${pct(ultimo)}")
    Text("Chance estimada de saber o conceito, atualizada a cada resposta; não é nota.", color = TintaSuave, fontSize = 12.sp)
    TextButton(onClick = onExplicarDominio, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.maoAoPassar()) {
        Text("O que é domínio estimado?", color = Primaria, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco3)) {
        Contador("${h.errosComCerteza}", "erros com certeza", "aprendeu errado? vale reexplicar", Modifier.weight(1f))
        Contador("${h.acertosNoChute}", "acertos no chute", "domínio ainda frágil", Modifier.weight(1f))
    }
    LinhaTreino(h)
    Text("Domínio estimado a cada resposta", color = Tinta, style = EstiloTitulo3.copy(fontSize = 15.sp))
    GraficoDominio(h, Modifier.fillMaxWidth().height(120.dp))
    HorizontalDivider(color = Borda)
    h.respostas.forEachIndexed { i, r -> LinhaResposta(i + 1, r) }
}

/** Fase 4: treino com flashcards fora de sala, que não entra no domínio estimado. */
@Composable
private fun LinhaTreino(h: HistoricoConceito) {
    Column {
        Text(textoTreinoConceito(h.treino), color = Tinta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Flashcards fora de sala; não altera o domínio estimado.", color = TintaSuave, fontSize = 12.sp)
    }
}

@Composable
private fun Contador(numero: String, legenda: String, dica: String, modifier: Modifier) {
    Surface(shape = FormaMd, color = Fundo, modifier = modifier) {
        Column(Modifier.padding(Espaco3)) {
            Text(numero, color = Tinta, style = EstiloTitulo3.comNumeros().copy(fontWeight = FontWeight.Bold))
            Text(legenda, color = Tinta, fontSize = 12.sp)
            Text(dica, color = TintaSuave, fontSize = 11.sp)
        }
    }
}

/** Linha do domínio estimado: do ponto de partida (L0) até depois da última resposta; ✓/✗ em cada ponto. */
@Composable
fun GraficoDominio(h: HistoricoConceito, modifier: Modifier = Modifier) {
    val corLinha = Primaria
    val corEixo = Borda
    val corErro = EstadoRevisar
    val valores = listOf(h.dominioInicial) + h.respostas.map { it.dominio }
    Canvas(modifier.semantics {
        contentDescription = "Domínio estimado de ${pct(h.dominioInicial)} até ${pct(valores.last())} em ${h.respostas.size} respostas"
    }) {
        val m = 8.dp.toPx()
        fun x(i: Int) = m + (size.width - 2 * m) * i / (valores.size - 1).coerceAtLeast(1)
        fun y(v: Double) = m + (size.height - 2 * m) * (1 - v.toFloat())
        listOf(0.5, 0.8).forEach { lim ->  // limiares dos estados
            drawLine(corEixo, Offset(m, y(lim)), Offset(size.width - m, y(lim)), 1.dp.toPx())
        }
        valores.zipWithNext().forEachIndexed { i, (a, b) ->
            drawLine(corLinha, Offset(x(i), y(a)), Offset(x(i + 1), y(b)), 2.5.dp.toPx())
        }
        valores.forEachIndexed { i, v ->
            val acertou = if (i == 0) null else h.respostas[i - 1].acertou
            val r = 4.dp.toPx()
            when (acertou) {
                null -> drawCircle(corEixo, r, Offset(x(i), y(v)))
                true -> drawCircle(corLinha, r, Offset(x(i), y(v)))
                // Erro: quadrado em revisar (forma diferente além da cor).
                false -> drawRect(corErro, Offset(x(i) - r, y(v) - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r))
            }
        }
    }
    Text("Círculo: acertou · quadrado: errou · linhas em 50% e 80% (limites dos estados)", color = TintaSuave, fontSize = 11.sp)
}

@Composable
private fun LinhaResposta(n: Int, r: RespostaHistorico) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (r.acertou) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (r.acertou) "acertou" else "errou",
            tint = if (r.acertou) Primaria else EstadoRevisar,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Espaco2))
        Column(Modifier.weight(1f)) {
            Text(
                "$n. " + (r.atividade?.let { tituloAtividade(it) } ?: "Simulação") + " · " + if (r.acertou) "acertou" else "errou",
                color = Tinta, fontSize = 13.sp,
            )
            rotuloConfianca(r.confianca)?.let { Text("Confiança: $it", color = TintaSuave, fontSize = 12.sp) }
        }
        Text(pct(r.dominio), color = Tinta, style = EstiloTitulo3.comNumeros().copy(fontSize = 13.sp))
    }
    Spacer(Modifier.height(Espaco1))
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val HistoricoExemplo = HistoricoConceito(
    conceito = "Regra da cadeia", dominioInicial = 0.3,
    respostas = listOf(
        RespostaHistorico("2026-10-21T12:00:00Z", com.edutec.app.data.AtividadeRef("a1", 1, "Derivadas"), false, "certeza", 0.13),
        RespostaHistorico("2026-10-21T12:00:01Z", com.edutec.app.data.AtividadeRef("a1", 1, "Derivadas"), true, "chute", 0.48),
        RespostaHistorico("2026-10-28T12:00:00Z", com.edutec.app.data.AtividadeRef("a2", 2, "Regra da cadeia"), true, "acho", 0.83),
        RespostaHistorico("2026-10-28T12:00:01Z", com.edutec.app.data.AtividadeRef("a2", 2, "Regra da cadeia"), true, null, 0.96),
    ),
    errosComCerteza = 1, acertosNoChute = 1,
    treino = com.edutec.app.data.TreinoConceito(treinos = 6, acertos = 4),
)

@Preview
@Composable
private fun PreviewHistorico() {
    EdutecTheme {
        Column(Modifier.fillMaxSize().padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco4)) {
            FaixaMapaDoAluno(Aluno("Ana Souza", com.edutec.app.data.EstadoConceito.Revisar, "x"), {}, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco4)) {
                PainelHistorico(Aluno("Ana Souza", com.edutec.app.data.EstadoConceito.Revisar, "x"), "Regra da cadeia",
                    Carga.Pronto(HistoricoExemplo), {}, {}, Modifier.width(340.dp).height(640.dp))
                PainelHistorico(Aluno("Ana Souza", com.edutec.app.data.EstadoConceito.Revisar, "x"), "Integral",
                    Carga.Pronto(HistoricoConceito("Integral")), {}, {}, Modifier.width(340.dp).height(320.dp))
            }
        }
    }
}
