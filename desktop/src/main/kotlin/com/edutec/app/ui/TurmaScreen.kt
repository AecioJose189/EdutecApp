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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.Aluno
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.ConceitoGrafo
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.DadosTurmaExemplo
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.GrafoTurma
import com.edutec.app.data.Membro
import com.edutec.app.data.Turma
import com.edutec.app.data.rotuloAlunos
import com.edutec.app.data.semAtividade
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.BordaControle
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco12
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.Espaco8
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.EstiloTitulo3
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.FormaPilula
import com.edutec.app.ui.theme.FormaSm
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.Menta
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.SobrePrimaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.TealMedio
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import com.edutec.app.ui.theme.Transparente
import com.edutec.app.ui.theme.comNumeros
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.jetbrains.compose.ui.tooling.preview.Preview

private val CantoCartao = FormaLg
private val Espaco = Espaco4
private val Abas = listOf("Grafo", "Atividades", "Materiais", "Estatísticas", "Turma")
private val AbasProntas = setOf("Grafo", "Atividades", "Turma")

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
    /** "Simular respostas da turma", no menu Demonstração; null esconde o menu. */
    onSimular: (() -> Unit)? = null,
    simulando: Boolean = false,
    /** Aba aberta (fica no ViewModel para a volta da projeção cair na mesma aba). */
    aba: String = Abas.first(),
    onAba: (String) -> Unit = {},
    atividades: Carga<List<AtividadePublicada>> = Carga.Carregando,
    onAbrirSessao: (AtividadePublicada) -> Unit = {},
    membros: Carga<List<Membro>> = Carga.Carregando,
    onRecarregarAba: () -> Unit = {},
    onConvidar: () -> Unit = {},
    /** UC05: junta origem em destino; [aoTerminar] recebe true se deu certo. */
    onJuntarConceitos: (origem: String, destino: String, aoTerminar: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onMarcarDistintos: (String, String) -> Unit = { _, _ -> },
    juntando: Boolean = false,
) {
    val avisar = LocalAvisar.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Fundo)
    ) {
        val expandido = maxWidth >= 1000.dp
        val margem = if (maxWidth >= 600.dp) Espaco6 else Espaco4

        val cabecalho: @Composable () -> Unit = {
            Cabecalho(
                turma = turma,
                expandido = expandido,
                aba = aba,
                onAba = {
                    if (it in AbasProntas) onAba(it) else avisar("Aba \"$it\" ainda não implementada")
                },
                onVoltar = onVoltar,
                onNovaAtividade = { onNovaAtividade(emptySet()) },
                onConvidar = onConvidar,
                onSimular = onSimular,
                simulando = simulando,
            )
        }
        val colunaTablet = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = margem, vertical = Espaco3)
        val colunaCelular = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = margem, vertical = Espaco3)

        val dados = (grafo as? EstadoGrafo.Pronto)?.grafo
        if (aba != Abas.first()) {
            Column(colunaTablet, verticalArrangement = Arrangement.spacedBy(Espaco)) {
                cabecalho()
                val mod = Modifier.fillMaxWidth().weight(1f)
                if (aba == "Atividades") {
                    AbaAtividades(atividades, onAbrirSessao, { onNovaAtividade(emptySet()) }, onRecarregarAba, mod)
                } else {
                    AbaMembros(membros, onConvidar, onRecarregarAba, mod)
                }
            }
        } else if (turma.semAtividade || dados == null) {
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
            ConteudoGrafo(
                turma, dados, expandido, colunaTablet, colunaCelular, cabecalho, onNovaAtividade, simulando,
                onJuntarConceitos, onMarcarDistintos, juntando,
            )
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
    simulando: Boolean,
    onJuntarConceitos: (origem: String, destino: String, aoTerminar: (Boolean) -> Unit) -> Unit,
    onMarcarDistintos: (String, String) -> Unit,
    juntando: Boolean,
) {
    val avisar = LocalAvisar.current
    // Conceito selecionado: o tocado no grafo, ou o mais fraco da turma.
    var selecionado by rememberSaveable(turma.id) { mutableStateOf<String?>(null) }
    val conceito = selecionado?.let(dados::conceito) ?: dados.conceitoInicial()
    // Diálogo "Juntar com outro conceito…": conceito de partida e, vindo do aviso, o parecido já escolhido.
    var juntarDe by rememberSaveable(turma.id) { mutableStateOf<String?>(null) }
    var juntarCom by rememberSaveable(turma.id) { mutableStateOf<String?>(null) }

    juntarDe?.let(dados::conceito)?.let { de ->
        DialogoJuntarConceitos(
            conceito = de,
            grafo = dados,
            juntando = juntando,
            escolhidoInicial = juntarCom,
            onJuntar = { origem, destino ->
                onJuntarConceitos(origem, destino) { ok ->
                    if (ok) { selecionado = destino; juntarDe = null; juntarCom = null }
                }
            },
            onDistintos = { a, b -> onMarcarDistintos(a, b) },
            onFechar = { juntarDe = null; juntarCom = null },
        )
    }
    val aviso: @Composable () -> Unit = {
        AvisoDuplicatas(
            dados.possiveisDuplicatas,
            onRevisar = {
                val par = dados.possiveisDuplicatas.first()
                selecionado = par.conceitoA
                juntarDe = par.conceitoA
                juntarCom = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val cartaoGrafo: @Composable (Modifier) -> Unit = { mod ->
        CartaoGrafo(dados, conceito?.nome, onSelecionar = { selecionado = it }, modifier = mod)
    }
    val demonstracao: @Composable () -> Unit = {
        if (simulando) FaixaSimulando(Modifier.fillMaxWidth())
    }
    val painel: @Composable (Modifier) -> Unit = { mod ->
        if (conceito != null) {
            PainelConceito(
                conceito = conceito,
                grafo = dados,
                onReforco = { onNovaAtividade(setOf(conceito.nome)) },
                onVerAlunos = { avisar("Lista de alunos com dificuldade (próxima versão)") },
                onJuntar = { juntarDe = conceito.nome; juntarCom = null },
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
                    aviso()
                    cartaoGrafo(Modifier.fillMaxWidth().weight(1f))
                    demonstracao()
                }
                painel(Modifier.width(320.dp).fillMaxHeight())
            }
        }
    } else {
        Column(colunaCelular, verticalArrangement = Arrangement.spacedBy(Espaco)) {
            cabecalho()
            aviso()
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
    onConvidar: () -> Unit,
    onSimular: (() -> Unit)?,
    simulando: Boolean,
) {
    val acoes: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco3)) {
            onSimular?.let { MenuDemonstracao(it, simulando) }
            OutlinedButton(
                onClick = onConvidar,
                shape = FormaMd,
                border = BorderStroke(1.dp, Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
                modifier = Modifier.maoAoPassar(),
            ) {
                Text("Convidar alunos", color = Primaria, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNovaAtividade,
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
                modifier = Modifier.maoAoPassar(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Espaco2))
                Text("Nova atividade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Abas na mesma linha do título só em janela bem larga; senão, numa linha própria.
    BoxWithConstraints {
        val umaLinha = expandido && maxWidth >= 1440.dp
        Column(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotaoTopo(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onVoltar)
                Spacer(Modifier.width(Espaco4))
                Column(Modifier.weight(1f, fill = !umaLinha)) {
                    Text(turma.nome, color = Tinta, fontFamily = Sora, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${turma.codigo} · ${turma.rotuloAlunos}", color = TintaSuave, fontSize = 14.sp, maxLines = 1)
                }
                if (umaLinha) {
                    Spacer(Modifier.width(Espaco8))
                    SeletorAbas(aba, onAba)
                    Spacer(Modifier.weight(1f))
                }
                if (expandido) acoes()
            }
            if (expandido && !umaLinha) SeletorAbas(aba, onAba)
            if (!expandido) {
                Box(Modifier.horizontalScroll(rememberScrollState())) { SeletorAbas(aba, onAba) }
                Box(Modifier.fillMaxWidth()) { acoes() }
            }
        }
    }
}

@Composable
private fun SeletorAbas(selecionada: String, onAba: (String) -> Unit) {
    Surface(shape = FormaPilula, color = MentaSuave) {
        Row(Modifier.padding(Espaco1)) {
            Abas.forEach { aba ->
                val ativa = aba == selecionada
                Surface(
                    onClick = { onAba(aba) },
                    shape = FormaPilula,
                    color = if (ativa) Menta else Transparente,
                ) {
                    Text(
                        text = aba,
                        color = if (ativa) Tinta else TintaSuave,
                        fontSize = 15.sp,
                        fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = Espaco4, vertical = Espaco3),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lista de alunos (alunos reais pelo nome; simulação por pseudônimo, RN13)
// ---------------------------------------------------------------------------

/** Pseudônimo de aluno simulado ("Aluno 07"); o grupo 1 é o número. */
private val PseudonimoSimulacao = Regex("""^Aluno (\d+)$""")

@Composable
private fun ListaAlunos(alunos: List<Aluno>, rolavel: Boolean, modifier: Modifier = Modifier) {
    if (alunos.isEmpty()) {
        Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
            Column(Modifier.padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
                Text("Alunos", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Nenhum aluno ainda", color = TintaSuave, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Os alunos aparecem aqui quando entrarem na turma com o código de convite.",
                    color = TintaSuave, fontSize = 13.sp,
                )
            }
        }
        return
    }
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(
            (if (rolavel) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(Espaco4),
            verticalArrangement = Arrangement.spacedBy(Espaco2),
        ) {
            Row(Modifier.padding(start = Espaco1, top = Espaco1), verticalAlignment = Alignment.CenterVertically) {
                Text("Alunos (${alunos.size})", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            val temSimulados = alunos.any { PseudonimoSimulacao.matches(it.nome) }
            Text(
                if (temSimulados) "Pior estado de cada aluno (simulação: pseudônimos)." else "Pior estado de cada aluno.",
                color = TintaSuave, fontSize = 12.sp, modifier = Modifier.padding(start = Espaco1),
            )
            Spacer(Modifier.height(Espaco1))

            // Linha "Turma toda" destacada: o grafo mostra a turma inteira.
            Surface(shape = FormaMd, color = Menta, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = Espaco3, vertical = Espaco3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(FormaSm).background(Primaria),
                        contentAlignment = Alignment.Center,
                    ) { Text("T", color = SobrePrimaria, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(Espaco4))
                    Text("Turma toda", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            alunos.forEach { aluno ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Espaco3, vertical = Espaco2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Pseudônimo da simulação ("Aluno 07") → número; aluno real → foto/iniciais.
                    val numero = PseudonimoSimulacao.find(aluno.nome)?.groupValues?.get(1)
                    if (numero != null) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(MentaSuave),
                            contentAlignment = Alignment.Center,
                        ) { Text(numero, color = TintaSuave, fontSize = 12.sp) }
                    } else {
                        FotoUsuario(aluno.nome, null, tamanho = 32.dp)
                    }
                    Spacer(Modifier.width(Espaco4))
                    Text(aluno.nome, color = Tinta, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SeloEstado(aluno.estado)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grafo da turma (layout automático em camadas)
// ---------------------------------------------------------------------------

private val DiametroMinimo = 40.dp
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
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(Modifier.padding(Espaco6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grafo da turma", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Espaco4))
                // Quebra de linha em vez de rolagem: a legenda inteira sempre aparece.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Espaco4),
                    verticalArrangement = Arrangement.spacedBy(Espaco2),
                ) {
                    EstadoConceito.entries.reversed().forEach { estado ->
                        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                            SeloEstado(estado, tamanho = 16.dp)
                            Spacer(Modifier.width(Espaco2))
                            Text(
                                if (estado == EstadoConceito.Desenvolvimento) "Em desenv." else estado.rotulo,
                                color = TintaSuave, fontSize = 13.sp,
                            )
                        }
                    }
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        SeloEstado(null, tamanho = 16.dp)
                        Spacer(Modifier.width(Espaco2))
                        Text("Sem dados", color = TintaSuave, fontSize = 13.sp)
                    }
                    Text(
                        "Tamanho = nº de respostas", color = TintaSuave, fontSize = 13.sp,
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

    BoxWithConstraints(modifier.padding(top = Espaco3)) {
        val largura = maxWidth
        val altura = maxHeight
        // Centro do círculo de cada nó, em dp. Na vertical, desconta o espaço do rótulo embaixo.
        fun centro(nome: String): Pair<Dp, Dp> {
            val p = layout.getValue(nome)
            val util = (altura - AlturaRotulo - 8.dp).coerceAtLeast(0.dp)
            return largura * p.x to (8.dp + util * p.y)
        }

        // Arestas pré-requisito → conceito, com seta, cortadas na borda de cada nó.
        val cor = TealMedio
        Canvas(Modifier.fillMaxSize()) {
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
            .clip(FormaMd)
            .clickable(role = Role.Button, onClick = onClick)
            .maoAoPassar()
            .semantics(mergeDescendants = true) { contentDescription = descricao }
            .padding(horizontal = Espaco2, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Nó em foco ganha um anel preto em volta.
        Box(
            if (selecionado) Modifier.size(diametro).border(3.dp, Tinta, CircleShape).padding(Espaco2)
            else Modifier.size(diametro),
            contentAlignment = Alignment.Center,
        ) {
            val interno = if (selecionado) diametro - 12.dp else diametro
            SeloEstado(estado.takeUnless { conceito.semDados }, tamanho = interno)
        }
        Spacer(Modifier.height(Espaco1))
        Text(
            conceito.nome,
            color = Tinta,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CarregandoGrafo(modifier: Modifier = Modifier) {
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(Espaco12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco4, Alignment.CenterVertically),
        ) {
            CircularProgressIndicator(color = Primaria)
            Text("Carregando o grafo da turma…", color = TintaSuave, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErroGrafo(mensagem: String, onTentarDeNovo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(Espaco8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco4, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MentaSuave),
                contentAlignment = Alignment.Center,
            ) { Text("!", color = Erro, fontFamily = Sora, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Não foi possível carregar o grafo", color = Tinta, fontFamily = Sora, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(mensagem, color = TintaSuave, fontSize = 15.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onTentarDeNovo,
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
            ) { Text("Tentar de novo", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Demonstração: simular respostas da turma
// ---------------------------------------------------------------------------

/** Menu "Demonstração" (fora de destaque): simular 25 alunos respondendo. */
@Composable
private fun MenuDemonstracao(onSimular: () -> Unit, simulando: Boolean) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { aberto = true }, modifier = Modifier.maoAoPassar()) {
            Text(if (simulando) "Simulando…" else "Demonstração", color = TintaSuave, fontSize = 14.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Simular respostas da turma")
                        Text("25 alunos fictícios (pseudônimos); refazer substitui a anterior", color = TintaSuave, fontSize = 12.sp)
                    }
                },
                enabled = !simulando,
                onClick = { aberto = false; onSimular() },
            )
        }
    }
}

@Composable
private fun FaixaSimulando(modifier: Modifier = Modifier) {
    Surface(shape = FormaLg, color = Superficie, border = BorderStroke(1.dp, BordaControle), modifier = modifier) {
        Column(Modifier.padding(horizontal = Espaco4, vertical = Espaco3), verticalArrangement = Arrangement.spacedBy(Espaco2)) {
            Text("Demonstração: registrando as respostas simuladas e atualizando o domínio estimado…",
                color = TintaSuave, fontSize = 13.sp)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primaria, trackColor = MentaSuave)
        }
    }
}

// ---------------------------------------------------------------------------
// Painel do conceito selecionado
// ---------------------------------------------------------------------------

private fun porcento(valor: Double?): String = valor?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
private fun PainelConceito(
    conceito: ConceitoGrafo,
    grafo: GrafoTurma,
    onReforco: () -> Unit,
    onVerAlunos: () -> Unit,
    onJuntar: () -> Unit,
    preencherAltura: Boolean,
    modifier: Modifier = Modifier,
) {
    val estado = conceito.estado.takeUnless { conceito.semDados }
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(Modifier.padding(Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
            // No tablet o conteúdo rola e os botões ficam fixos no rodapé do painel, separados por uma
            // linha: o que passa da altura fica claramente "para rolar", não escondido atrás dos botões.
            // Nova rolagem a cada conceito: o painel abre sempre no topo, com o nome completo visível.
            val rolagem = remember(conceito.nome) { ScrollState(0) }
            Column(
                modifier = if (preencherAltura) Modifier.weight(1f).verticalScroll(rolagem).padding(bottom = Espaco1) else Modifier,
                verticalArrangement = Arrangement.spacedBy(Espaco3),
            ) {
                Text("Conceito", color = TintaSuave, fontSize = 14.sp)
                Text(conceito.nome, color = Tinta, fontFamily = Sora, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                ChipEstado(
                    estado,
                    texto = if (estado == null) "Sem dados · ainda sem respostas"
                    else "${estado.rotulo} · domínio estimado ${porcento(conceito.media)}",
                )
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, tint = TintaSuave, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(Espaco2))
                    Text(
                        "Chance estimada de a turma saber o conceito; não é nota.",
                        color = TintaSuave, fontSize = 12.sp,
                    )
                }

                // Logo abaixo do estado: é a leitura principal do painel.
                CausaRaiz(conceito, grafo)

                if (estado == null) {
                    Text(
                        "O domínio estimado aparece quando os alunos responderem a questões deste conceito.",
                        color = TintaSuave, fontSize = 14.sp,
                    )
                } else {
                    MiniNumero(
                        porcento(conceito.media),
                        "domínio estimado médio · mediana ${porcento(conceito.mediana)}" +
                            if (turmaDividida(conceito)) " · turma dividida" else "",
                        Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Espaco3)) {
                        MiniNumero("${conceito.respostas}", "respostas", Modifier.weight(1f).fillMaxHeight())
                        MiniNumero(
                            "${conceito.alunosRevisar}/${conceito.alunos}", "alunos com dificuldade",
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (preencherAltura) HorizontalDivider(color = Borda)

            Button(
                onClick = onReforco,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
            ) { Text("Gerar atividade de reforço", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onVerAlunos,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = MentaSuave, contentColor = Primaria),
            ) { Text("Ver alunos com dificuldade", fontWeight = FontWeight.SemiBold) }
            // Ação de manutenção do mapa: discreta, abaixo das ações principais.
            TextButton(onClick = onJuntar, modifier = Modifier.fillMaxWidth().maoAoPassar()) {
                Text("Juntar com outro conceito…", color = TintaSuave, fontSize = 13.sp)
            }
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
    Surface(shape = FormaMd, color = MentaSuave, modifier = Modifier.fillMaxWidth()) {
        Text(texto, color = Tinta, fontSize = 13.sp, modifier = Modifier.padding(Espaco4))
    }
}

@Composable
private fun MiniNumero(numero: String, legenda: String, modifier: Modifier = Modifier) {
    Surface(shape = FormaMd, color = Fundo, modifier = modifier) {
        Column(Modifier.padding(Espaco3)) {
            Text(numero, color = Tinta, style = EstiloTitulo3.comNumeros().copy(fontWeight = FontWeight.Bold))
            Text(legenda, color = TintaSuave, fontSize = 12.sp)
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
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Box(
            (if (rolavel) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(Espaco8),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espaco4),
            ) {
                // "?" = sem dados, o mesmo símbolo dos conceitos ainda sem respostas.
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Superficie)
                        .border(BorderStroke(1.5.dp, BordaControle), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("?", color = TintaSuave, fontFamily = Sora, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                Text(
                    "Ainda não há dados de aprendizagem nesta turma",
                    color = Tinta, fontFamily = Sora, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    "O grafo de conhecimento aparece quando os alunos começarem a responder às atividades.",
                    color = TintaSuave, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
                    passos.forEachIndexed { i, passo ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(MentaSuave),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", color = Tinta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(Espaco3))
                            Text(passo, color = Tinta, fontSize = 15.sp)
                        }
                    }
                }
                Button(
                    onClick = onNovaAtividade,
                    shape = FormaMd,
                    colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                    contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Espaco2))
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
