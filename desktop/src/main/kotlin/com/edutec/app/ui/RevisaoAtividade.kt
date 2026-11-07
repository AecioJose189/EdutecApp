@file:OptIn(ExperimentalLayoutApi::class)

package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AlternativaGerada
import com.edutec.app.data.AtividadeGerada
import com.edutec.app.data.ConceitoGerado
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.QuestaoGerada
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.FormaPilula
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.Menta
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.SobrePrimaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.jetbrains.compose.ui.tooling.preview.Preview

private val CantoCartao = FormaLg
private val Espaco = Espaco4

/**
 * Etapa 4: revisão das questões geradas (só leitura nesta versão).
 * Tablet: conceitos no topo e lista + detalhe das questões. Celular: tudo numa coluna.
 */
@Composable
fun RevisaoConteudo(
    estado: EstadoGeracao.Concluido,
    expandido: Boolean,
    /** "Gerar novas questões" quando o resultado foi reaproveitado (envia forcar_nova = true). */
    onGerarNovas: () -> Unit,
    modifier: Modifier = Modifier,
    /** Etapa 5: "Publicar para a turma". */
    onPublicar: () -> Unit = {},
) {
    val atividade = estado.resultado
    if (expandido) {
        var selecionada by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Espaco)) {
            AvisoRevisao(estado, onGerarNovas)
            CartaoConceitos(atividade.conceitos, emUmaLinha = true)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Espaco)) {
                Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.width(380.dp).fillMaxHeight()) {
                    LazyColumn(
                        contentPadding = PaddingValues(Espaco4),
                        verticalArrangement = Arrangement.spacedBy(Espaco3),
                    ) {
                        item {
                            Text(
                                "Questões (${atividade.questoes.size})",
                                color = Tinta, fontFamily = Sora, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = Espaco2, top = Espaco2, bottom = Espaco1),
                            )
                        }
                        itemsIndexed(atividade.questoes) { i, q ->
                            ItemQuestao(i, q, selecionada = i == selecionada, onClick = { selecionada = i })
                        }
                    }
                }
                atividade.questoes.getOrNull(selecionada)?.let { q ->
                    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.verticalScroll(rememberScrollState()).padding(Espaco6)) {
                            DetalheQuestao(selecionada, q)
                        }
                    }
                }
            }
            BarraPublicar(estado, onPublicar)
        }
    } else {
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(Espaco)) {
            item { AvisoRevisao(estado, onGerarNovas) }
            item { CartaoConceitos(atividade.conceitos, emUmaLinha = false) }
            itemsIndexed(atividade.questoes) { i, q ->
                Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(Espaco6)) { DetalheQuestao(i, q) }
                }
            }
            item { BarraPublicar(estado, onPublicar) }
        }
    }
}

@Composable
private fun AvisoRevisao(estado: EstadoGeracao.Concluido, onGerarNovas: () -> Unit) {
    val atividade = estado.resultado
    Surface(shape = FormaLg, color = MentaSuave, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Espaco4, vertical = Espaco4), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
            Text(
                buildString {
                    if (atividade.reaproveitado) {
                        append("${atividade.questoes.size} questões e ${atividade.conceitos.size} conceitos")
                    } else {
                        append("A IA gerou ${atividade.questoes.size} questões e ${atividade.conceitos.size} conceitos")
                    }
                    append(" a partir de \"${estado.pedido.nomeArquivo}\"")
                    if (!atividade.reaproveitado) atividade.duracaoMs?.let { append(" em ${(it + 500) / 1000} s") }
                    append(".")
                },
                color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Revisão somente leitura nesta versão. Nada vai para os alunos sem a sua aprovação.",
                color = TintaSuave, fontSize = 13.sp,
            )
        }
    }
    if (atividade.reaproveitado) AvisoReaproveitado(atividade.geradoEm, onGerarNovas)
}

/** RN12: o mesmo PDF com os mesmos parâmetros não é processado de novo, a menos que o professor peça. */
@Composable
private fun AvisoReaproveitado(geradoEm: String?, onGerarNovas: () -> Unit) {
    Surface(
        shape = FormaLg, color = Superficie, border = BorderStroke(1.dp, Borda),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.padding(start = Espaco4, end = Espaco3, top = Espaco2, bottom = Espaco2),
            horizontalArrangement = Arrangement.spacedBy(Espaco3),
            verticalArrangement = Arrangement.spacedBy(Espaco1),
        ) {
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = TintaSuave, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Espaco2))
                Text(
                    "Este material já tinha sido processado em ${formatarData(geradoEm)} — resultado " +
                        "reaproveitado, sem nova chamada à IA",
                    color = TintaSuave, fontSize = 13.sp,
                )
            }
            TextButton(onClick = onGerarNovas, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text("Gerar novas questões", color = Primaria, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "2026-10-21T02:28:23.983Z" → "20/10/2026 às 23:28" (fuso do aparelho). */
private fun formatarData(iso: String?): String {
    if (iso == null) return "uma geração anterior"
    return runCatching {
        val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val data = entrada.parse(iso.take(19))!!
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(data)
    }.getOrDefault("uma geração anterior")
}

// ---------------------------------------------------------------------------
// Publicar (etapa 5)
// ---------------------------------------------------------------------------

/**
 * Configurações mínimas de publicação, vindas do formulário (refazer e tempo).
 * TODO: prazo de entrega e data de liberação para os alunos.
 */
@Composable
private fun BarraPublicar(estado: EstadoGeracao.Concluido, onPublicar: () -> Unit) {
    val pedido = estado.pedido
    val configuracao = buildString {
        append(if (pedido.permitirRefazer) "Permitir refazer" else "Tentativa única")
        append(" · ")
        append(pedido.tempoMinutos?.let { "$it min por tentativa" } ?: "sem limite de tempo")
    }
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Espaco6, vertical = Espaco4),
            horizontalArrangement = Arrangement.spacedBy(Espaco4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Publicar ${estado.resultado.questoes.size} questões para a turma",
                    color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$configuracao · os conceitos entram no grafo da turma",
                    color = TintaSuave, fontSize = 13.sp,
                )
                estado.erroPublicacao?.let {
                    Text(it, color = Erro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onPublicar,
                enabled = !estado.publicando,
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
            ) {
                if (estado.publicando) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Espaco3))
                    Text("Publicando…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Publicar para a turma", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Conceitos extraídos
// ---------------------------------------------------------------------------

/** [emUmaLinha]: no tablet os conceitos ficam numa linha com rolagem, para sobrar altura às questões. */
@Composable
private fun CartaoConceitos(conceitos: List<ConceitoGerado>, emUmaLinha: Boolean) {
    val legenda: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = TintaSuave, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Espaco2))
            Text(
                "? = sem dados: o domínio estimado aparece depois que os alunos responderem.",
                color = TintaSuave, fontSize = 12.sp,
            )
        }
    }
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (emUmaLinha) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
            if (emUmaLinha) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Conceitos extraídos", color = Tinta, fontFamily = Sora, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(Espaco4))
                    legenda()
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Espaco3),
                ) {
                    conceitos.forEach { CartaoConceito(it) }
                }
            } else {
                Text("Conceitos extraídos", color = Tinta, fontFamily = Sora, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Espaco3),
                    verticalArrangement = Arrangement.spacedBy(Espaco3),
                ) {
                    conceitos.forEach { CartaoConceito(it) }
                }
                legenda()
            }
        }
    }
}

@Composable
private fun CartaoConceito(conceito: ConceitoGerado) {
    Surface(shape = FormaMd, color = Fundo, border = BorderStroke(1.dp, Borda)) {
        Row(Modifier.padding(horizontal = Espaco4, vertical = Espaco3), verticalAlignment = Alignment.CenterVertically) {
            SeloEstado(null)
            Spacer(Modifier.width(Espaco3))
            Column {
                Text(conceito.nome, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (conceito.prerequisitos.isEmpty()) "Sem pré-requisitos"
                    else "Requer: ${conceito.prerequisitos.joinToString(", ")}",
                    color = TintaSuave, fontSize = 13.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Questões
// ---------------------------------------------------------------------------

@Composable
private fun ItemQuestao(indice: Int, questao: QuestaoGerada, selecionada: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = FormaMd,
        color = if (selecionada) Menta else Superficie,
        border = if (selecionada) BorderStroke(2.dp, Primaria) else BorderStroke(1.dp, Borda),
        modifier = Modifier.fillMaxWidth().maoAoPassar(),
    ) {
        Column(Modifier.padding(Espaco4), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Questão ${indice + 1}",
                    color = Tinta,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(Espaco3))
                EtiquetaTipo(questao)
            }
            Text(
                textoSimplesComLatex(questao.enunciado),
                // Sobre menta, texto sempre tinta (tinta-suave fica abaixo de 4,5:1).
                color = if (selecionada) Tinta else TintaSuave, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetalheQuestao(indice: Int, questao: QuestaoGerada) {
    Column(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco3),
            verticalArrangement = Arrangement.spacedBy(Espaco2),
        ) {
            Text("Questão ${indice + 1}", color = Tinta, fontFamily = Sora, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            EtiquetaTipo(questao)
            questao.paginaOrigem?.let { Text("Página $it", color = TintaSuave, fontSize = 14.sp) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espaco2),
            verticalArrangement = Arrangement.spacedBy(Espaco2),
        ) {
            questao.conceitos.forEach { ChipConceitoGerado(it) }
        }
        Text(textoComLatex(questao.enunciado), color = Tinta, fontSize = 17.sp, lineHeight = 25.sp)

        if (questao.multiplaEscolha) {
            questao.alternativas.orEmpty().forEachIndexed { i, a -> LinhaAlternativa(i, a) }
        } else {
            questao.rubrica?.let { Rubrica(it) }
        }
    }
}

@Composable
private fun EtiquetaTipo(questao: QuestaoGerada) {
    val (texto, fundo, cor) = if (questao.multiplaEscolha) Triple("Múltipla escolha", Menta, Tinta)
    else Triple("Aberta", MentaSuave, Primaria)
    Surface(shape = FormaPilula, color = fundo) {
        Text(
            texto, color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Espaco3, vertical = Espaco1),
        )
    }
}

@Composable
private fun ChipConceitoGerado(nome: String) {
    Surface(shape = FormaPilula, color = Superficie, border = BorderStroke(1.dp, Borda)) {
        Text(
            nome, color = TintaSuave, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = Espaco3, vertical = Espaco2),
        )
    }
}

@Composable
private fun LinhaAlternativa(indice: Int, alternativa: AlternativaGerada) {
    val letra = ('A' + indice).toString()
    Surface(
        shape = FormaMd,
        color = if (alternativa.correta) Menta else Superficie,
        border = if (alternativa.correta) BorderStroke(2.dp, Primaria) else BorderStroke(1.dp, Borda),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Alternativa $letra${if (alternativa.correta) ", correta" else ""}"
        },
    ) {
        Row(Modifier.padding(Espaco4)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (alternativa.correta) Primaria else MentaSuave),
                contentAlignment = Alignment.Center,
            ) {
                if (alternativa.correta) Icon(Icons.Filled.Check, null, tint = SobrePrimaria, modifier = Modifier.size(18.dp))
                else Text(letra, color = TintaSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(Espaco3))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
                if (alternativa.correta) {
                    Text("$letra · Correta", color = Primaria, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    textoComLatex(alternativa.texto),
                    color = Tinta,
                    fontSize = 15.sp,
                    fontWeight = if (alternativa.correta) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(textoComLatex(alternativa.feedback), color = if (alternativa.correta) Tinta else TintaSuave, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Rubrica(rubrica: String) {
    Surface(shape = FormaLg, color = MentaSuave, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Espaco4), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
            Text("Rubrica de correção", color = Primaria, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(textoComLatex(rubrica), color = Tinta, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Previews — trecho de amostras/resultado-v1.json
// ---------------------------------------------------------------------------

private val AtividadeExemplo = AtividadeGerada(
    conceitos = listOf(
        ConceitoGerado("Jogos de Soma Zero"),
        ConceitoGerado("Algoritmo Minimax", listOf("Jogos de Soma Zero")),
        ConceitoGerado("Poda Alfa-Beta", listOf("Algoritmo Minimax")),
        ConceitoGerado("Decisões Imperfeitas em Tempo Real", listOf("Algoritmo Minimax")),
        ConceitoGerado("Quiescência", listOf("Decisões Imperfeitas em Tempo Real")),
    ),
    questoes = listOf(
        QuestaoGerada(
            tipo = "multipla_escolha",
            enunciado = "A poda alfa-beta é amplamente empregada para mitigar a explosão combinatória na busca " +
                "Minimax. Qual é o efeito teórico dessa técnica no tamanho da árvore de busca a ser examinada?",
            conceitos = listOf("Poda Alfa-Beta"),
            paginaOrigem = 3,
            alternativas = listOf(
                AlternativaGerada(
                    "Elimina a dependência exponencial da busca, tornando a complexidade estritamente polinomial.", false,
                    "Incorreto. A poda alfa-beta não elimina a natureza exponencial do problema; ela apenas reduz o expoente.",
                ),
                AlternativaGerada(
                    "Permite reduzir o expoente da busca aproximadamente pela metade, sem alterar a decisão ótima final.", true,
                    "Correto. O material destaca que a poda alfa-beta não elimina o expoente, mas pode reduzi-lo pela " +
                        "metade mantendo a mesma decisão do Minimax.",
                ),
                AlternativaGerada(
                    "Permite avaliar apenas os nós com valor alfa negativo, descartando todos os nós com valor beta positivo.", false,
                    "Incorreto. Alfa e beta representam limites inferiores e superiores das escolhas possíveis ao longo do ramo.",
                ),
                AlternativaGerada(
                    "Substitui a árvore de busca completa por uma tabela de dispersão contendo todas as jogadas finais.", false,
                    "Incorreto. A poda alfa-beta ainda percorre a árvore em profundidade, cortando subárvores irrelevantes.",
                ),
            ),
        ),
        QuestaoGerada(
            tipo = "aberta",
            enunciado = "Como o processo de tomada de decisão ótima se modifica quando o jogo envolve mais de dois " +
                "participantes (por exemplo, três jogadores A, B e C), conforme apresentado no material?",
            conceitos = listOf("Jogos de Soma Zero", "Algoritmo Minimax"),
            paginaOrigem = 3,
            rubrica = "Resposta esperada: 1) O valor escalar único de utilidade em cada nó é substituído por um vetor " +
                "\$\\langle v_A, v_B, v_C \\rangle\$, onde cada componente representa a utilidade para o respectivo " +
                "jogador; 2) Na sua vez, o jogador escolhe o sucessor que maximiza o seu próprio componente.",
        ),
    ),
    duracaoMs = 31_114,
)

@Preview
@Composable
private fun PreviewRevisaoTablet() {
    EdutecTheme {
        NovaAtividadeScreen(
            DadosExemplo.turmas.first(), onFechar = {},
            estado = EstadoGeracao.Concluido(PedidoExemplo, AtividadeExemplo),
        )
    }
}

@Preview
@Composable
private fun PreviewRevisaoCelular() {
    EdutecTheme {
        NovaAtividadeScreen(
            DadosExemplo.turmas.first(), onFechar = {},
            estado = EstadoGeracao.Concluido(
                PedidoExemplo,
                AtividadeExemplo.copy(reaproveitado = true, geradoEm = "2026-10-21T02:28:23.983Z"),
            ),
        )
    }
}
