package com.edutec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AtividadePendente
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.Turma
import com.edutec.app.data.rotuloAlunos
import com.edutec.app.data.semDados
import com.edutec.app.data.semestresRecentes
import com.edutec.app.ui.theme.AmareloFundo
import com.edutec.app.ui.theme.AmareloIcone
import com.edutec.app.ui.theme.AmareloTexto
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.AzulNumero
import com.edutec.app.ui.theme.AzulTexto
import com.edutec.app.ui.theme.BordaTracejada
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EmDesenvolvimento
import com.edutec.app.ui.theme.EtiquetaAzulFundo
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaFundo
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.LaranjaFundo
import com.edutec.app.ui.theme.LaranjaNumero
import com.edutec.app.ui.theme.LaranjaTexto
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho

private val CantoCartao = RoundedCornerShape(24.dp)
private val EspacoGrade = 14.dp

@Composable
fun MinhasTurmasScreen(
    modifier: Modifier = Modifier,
    /** Turmas do professor vindas do servidor (os exemplos ficam só nos @Preview). */
    turmas: List<Turma>,
    onTurma: (Turma) -> Unit = {},
    /** Cria a turma; o terceiro parâmetro recebe null quando deu certo ou a mensagem de erro. */
    onCriarTurma: (nome: String, codigo: String, aoTerminar: (String?) -> Unit) -> Unit = { _, _, fim -> fim(null) },
    /** Carregando a lista do servidor (primeira vez ou "Tentar de novo"). */
    carregando: Boolean = false,
    /** Falha ao carregar a lista; mostra a mensagem e o botão "Tentar de novo". */
    erro: String? = null,
    onTentarDeNovo: () -> Unit = {},
    /** Atividades geradas aguardando revisão; ainda não há banco, então o app passa vazia. */
    pendentes: List<AtividadePendente> = emptyList(),
) {
    val semestres = remember { semestresRecentes() }
    var semestre by rememberSaveable { mutableStateOf(semestres.first()) }
    var mostrarNovaTurma by rememberSaveable { mutableStateOf(false) }

    val avisar = LocalAvisar.current

    val conceitosARevisar = turmas.sumOf { it.conceitosARevisar }
    val turmasComRevisao = turmas.count { it.conceitosARevisar > 0 }
    val totalAlunos = turmas.sumOf { it.alunos }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Fundo)
    ) {
        val largura = maxWidth
        val expandido = largura >= 840.dp
        val colunas = when {
            largura >= 840.dp -> 3
            largura >= 600.dp -> 2
            else -> 1
        }
        val margem = if (largura >= 600.dp) 26.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = margem, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(EspacoGrade),
        ) {
            BarraSuperior(
                semestres = semestres,
                semestre = semestre,
                onSemestreSelecionado = { semestre = it; avisar("Semestre $it selecionado") },
                onPerfil = { avisar("Perfil do professor") },
            )
            Spacer(Modifier.height(8.dp))
            Cabecalho(expandido = expandido, mostrarLegenda = turmas.isNotEmpty() && !carregando && erro == null)

            if (carregando) {
                CarregandoTurmas()
            } else if (erro != null) {
                ErroTurmas(erro, onTentarDeNovo)
            } else if (turmas.isEmpty()) {
                EstadoVazioTurmas(onNovaTurma = { mostrarNovaTurma = true })
            } else {
                // Os números do resumo só aparecem quando há algo real para contar (nada inventado).
                if (pendentes.isNotEmpty() || totalAlunos > 0) {
                    Resumo(
                        expandido = expandido,
                        pendentes = pendentes.size,
                        descricaoPendente = pendentes.firstOrNull()
                            ?.let { "${it.disciplina} · ${it.turma}" }.orEmpty(),
                        conceitosARevisar = conceitosARevisar,
                        turmasComRevisao = turmasComRevisao,
                        totalAlunos = totalAlunos,
                        onRevisar = { avisar("Abrindo revisão da atividade") },
                    )
                }
                GradeTurmas(
                    turmas = turmas,
                    colunas = colunas,
                    onTurma = onTurma,
                    onNovaTurma = { mostrarNovaTurma = true },
                )
            }
        }
    }

    if (mostrarNovaTurma) {
        DialogoNovaTurma(
            onCancelar = { mostrarNovaTurma = false },
            onCriar = { nome, codigo, aoTerminar ->
                onCriarTurma(nome, codigo.ifBlank { "Turma única" }) { falha ->
                    aoTerminar(falha)
                    if (falha == null) {
                        mostrarNovaTurma = false
                        avisar("Turma \"$nome\" criada")
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------

@Composable
private fun BarraSuperior(
    semestres: List<String>,
    semestre: String,
    onSemestreSelecionado: (String) -> Unit,
    onPerfil: () -> Unit,
) {
    var menuAberto by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LUMINI",
            color = TextoPrincipal,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.weight(1f))

        Box {
            Surface(
                onClick = { menuAberto = true },
                shape = RoundedCornerShape(50),
                color = Pilula,
            ) {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Semestre $semestre",
                        color = TextoPrincipal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Trocar semestre",
                        tint = TextoPrincipal,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                semestres.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text("Semestre $opcao") },
                        onClick = {
                            menuAberto = false
                            onSemestreSelecionado(opcao)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Surface(
            onClick = onPerfil,
            shape = CircleShape,
            color = Pilula,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("P", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Título + legenda
// ---------------------------------------------------------------------------

@Composable
private fun Cabecalho(expandido: Boolean, mostrarLegenda: Boolean) {
    val titulo: @Composable () -> Unit = {
        Text(
            text = "Minhas turmas",
            color = TextoPrincipal,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    if (expandido) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            titulo()
            Spacer(Modifier.weight(1f))
            if (mostrarLegenda) Legenda()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            titulo()
            if (mostrarLegenda) Legenda()
        }
    }
}

@Composable
private fun Legenda() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ItemLegenda(Consolidado, "Consolidado")
        ItemLegenda(EmDesenvolvimento, "Em desenvolvimento")
        ItemLegenda(Revisar, "Revisar")
    }
}

@Composable
private fun ItemLegenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(cor)
        )
        Spacer(Modifier.width(6.dp))
        Text(texto, color = TextoCorpo, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// Faixa de resumo (atividade pendente + números)
// ---------------------------------------------------------------------------

@Composable
private fun Resumo(
    expandido: Boolean,
    pendentes: Int,
    descricaoPendente: String,
    conceitosARevisar: Int,
    turmasComRevisao: Int,
    totalAlunos: Int,
    onRevisar: () -> Unit,
) {
    val textoConceitos = "${if (conceitosARevisar == 1) "conceito" else "conceitos"} a revisar em " +
        "$turmasComRevisao ${if (turmasComRevisao == 1) "turma" else "turmas"}"

    val cartaoConceitos: @Composable (Modifier) -> Unit = { mod ->
        CartaoNumero(
            numero = conceitosARevisar.toString(),
            legenda = textoConceitos,
            fundo = LaranjaFundo,
            corNumero = LaranjaNumero,
            corLegenda = LaranjaTexto,
            modifier = mod,
        )
    }
    val cartaoAlunos: @Composable (Modifier) -> Unit = { mod ->
        CartaoNumero(
            numero = totalAlunos.toString(),
            legenda = "alunos neste semestre",
            fundo = AzulFundo,
            corNumero = AzulNumero,
            corLegenda = AzulTexto,
            modifier = mod,
        )
    }

    if (expandido) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(EspacoGrade),
        ) {
            if (pendentes > 0) {
                CartaoAtividadePendente(
                    pendentes, descricaoPendente, onRevisar,
                    Modifier.weight(2f).fillMaxHeight(),
                )
            }
            cartaoConceitos(Modifier.weight(1f).fillMaxHeight())
            cartaoAlunos(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(EspacoGrade)) {
            if (pendentes > 0) {
                CartaoAtividadePendente(pendentes, descricaoPendente, onRevisar, Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(EspacoGrade),
            ) {
                cartaoConceitos(Modifier.weight(1f).fillMaxHeight())
                cartaoAlunos(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CartaoAtividadePendente(
    quantidade: Int,
    descricao: String,
    onRevisar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = if (quantidade == 1) "1 atividade aguarda sua revisão"
    else "$quantidade atividades aguardam sua revisão"

    Surface(shape = CantoCartao, color = AmareloFundo, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmareloIcone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = TextoPrincipal,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(descricao, color = AmareloTexto, fontSize = 14.sp)
            }
            TextButton(onClick = onRevisar) {
                Text("Revisar →", color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CartaoNumero(
    numero: String,
    legenda: String,
    fundo: Color,
    corNumero: Color,
    corLegenda: Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CantoCartao, color = fundo, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(numero, color = corNumero, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(legenda, color = corLegenda, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Grade de turmas
// ---------------------------------------------------------------------------

@Composable
private fun GradeTurmas(
    turmas: List<Turma>,
    colunas: Int,
    onTurma: (Turma) -> Unit,
    onNovaTurma: () -> Unit,
) {
    // null representa o cartão "Nova turma" no fim da grade.
    val itens: List<Turma?> = turmas + null

    Column(verticalArrangement = Arrangement.spacedBy(EspacoGrade)) {
        itens.chunked(colunas).forEach { linha ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(EspacoGrade),
            ) {
                linha.forEach { turma ->
                    val mod = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                    if (turma != null) {
                        CartaoTurma(turma, onClick = { onTurma(turma) }, modifier = mod)
                    } else {
                        CartaoNovaTurma(onClick = onNovaTurma, modifier = mod)
                    }
                }
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CartaoTurma(turma: Turma, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CantoCartao, color = Cartao, modifier = modifier.maoAoPassar()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(turma.nome, color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${turma.codigo} · ${turma.rotuloAlunos}",
                        color = TextoSecundario,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                EtiquetaTurma(turma)
            }

            Spacer(Modifier.height(16.dp))
            BarraProgresso(turma.consolidado, turma.desenvolvimento, turma.revisar)
            Spacer(Modifier.height(16.dp))

            val semDados = turma.consolidado + turma.desenvolvimento + turma.revisar == 0
            Text(
                text = if (semDados) "Sem dados de aprendizagem ainda"
                else "${turma.consolidado}% consolidado · ${turma.desenvolvimento}% em desenvolvimento · " +
                    "${turma.revisar}% revisar",
                color = TextoCorpo,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.weight(1f))
            Text(turma.rodape, color = TextoSecundario, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EtiquetaTurma(turma: Turma) {
    val (texto, fundo, cor) = when {
        turma.semDados -> Triple("Nova turma", EtiquetaAzulFundo, EtiquetaAzulTexto)
        turma.conceitosARevisar > 0 -> Triple(
            if (turma.conceitosARevisar == 1) "1 conceito a revisar"
            else "${turma.conceitosARevisar} conceitos a revisar",
            EtiquetaVermelhaFundo,
            EtiquetaVermelhaTexto,
        )
        turma.status != null -> Triple(turma.status, EtiquetaAzulFundo, EtiquetaAzulTexto)
        else -> Triple("Sem alertas", EtiquetaAzulFundo, EtiquetaAzulTexto)
    }

    Surface(shape = RoundedCornerShape(50), color = fundo) {
        Text(
            text = texto,
            color = cor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BarraProgresso(consolidado: Int, desenvolvimento: Int, revisar: Int) {
    val segmentos = listOf(
        consolidado to Consolidado,
        desenvolvimento to EmDesenvolvimento,
        revisar to Revisar,
    ).filter { it.first > 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            // Fundo branco faz o espaço entre os segmentos parecer um corte.
            .background(if (segmentos.isEmpty()) Trilho else Cartao),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segmentos.forEach { (valor, cor) ->
            Box(
                Modifier
                    .weight(valor.toFloat())
                    .fillMaxHeight()
                    .background(cor)
            )
        }
    }
}

@Composable
private fun CartaoNovaTurma(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 180.dp)
            .clip(CantoCartao)
            .clickable(onClick = onClick)
            .maoAoPassar()
            .drawBehind {
                val espessura = 2.dp.toPx()
                drawRoundRect(
                    color = BordaTracejada,
                    topLeft = Offset(espessura / 2, espessura / 2),
                    size = Size(size.width - espessura, size.height - espessura),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = espessura,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AzulFundo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Consolidado,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Nova turma", color = Consolidado, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Estado vazio (nenhuma turma)
// ---------------------------------------------------------------------------

@Composable
private fun EstadoVazioTurmas(onNovaTurma: () -> Unit) {
    Surface(shape = CantoCartao, color = Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(AzulFundo),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = Consolidado, modifier = Modifier.size(30.dp)) }
            Text(
                "Nenhuma turma ainda",
                color = TextoPrincipal, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "Crie a primeira turma para enviar materiais em PDF, gerar atividades com a IA e, quando os " +
                    "alunos responderem, acompanhar o domínio estimado de cada conceito.",
                color = TextoCorpo, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onNovaTurma,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nova turma", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carregando / erro ao buscar as turmas no servidor
// ---------------------------------------------------------------------------

@Composable
private fun CarregandoTurmas() {
    Surface(shape = CantoCartao, color = Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = Consolidado)
            Text("Carregando suas turmas…", color = TextoCorpo, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErroTurmas(mensagem: String, onTentarDeNovo: () -> Unit) {
    Surface(shape = CantoCartao, color = Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(EtiquetaVermelhaFundo),
                contentAlignment = Alignment.Center,
            ) { Text("!", color = EtiquetaVermelhaTexto, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Não foi possível carregar as turmas",
                color = TextoPrincipal, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                mensagem, color = TextoCorpo, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Button(
                onClick = onTentarDeNovo,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) { Text("Tentar de novo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogo de nova turma
// ---------------------------------------------------------------------------

@Composable
private fun DialogoNovaTurma(
    onCancelar: () -> Unit,
    onCriar: (nome: String, codigo: String, aoTerminar: (String?) -> Unit) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var criando by remember { mutableStateOf(false) }
    var falha by remember { mutableStateOf<String?>(null) }
    val focoNome = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoNome.requestFocus() }

    val podeCriar = nome.isNotBlank() && !criando
    fun criar() {
        if (!podeCriar) return
        criando = true
        falha = null
        onCriar(nome.trim(), codigo.trim()) { erro ->
            criando = false
            falha = erro
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cartao,
        title = { Text("Nova turma", fontWeight = FontWeight.Bold) },
        text = {
            // Enter em qualquer campo confirma (Esc fecha o diálogo).
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.onPreviewKeyEvent { evento ->
                    val enter = evento.key == Key.Enter || evento.key == Key.NumPadEnter
                    if (enter && evento.type == KeyEventType.KeyDown) criar()
                    enter
                },
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Disciplina") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focoNome),
                )
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Turma (ex.: Turma C)") },
                    singleLine = true,
                )
                if (criando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Consolidado)
                        Spacer(Modifier.width(10.dp))
                        Text("Criando a turma…", color = TextoCorpo, fontSize = 14.sp)
                    }
                }
                falha?.let { Text(it, color = EtiquetaVermelhaTexto, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::criar, enabled = podeCriar) {
                Text(if (falha != null) "Tentar de novo" else "Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
    )
}

// ---------------------------------------------------------------------------
// Previews (aparecem no painel "Split/Design" do Android Studio)
// ---------------------------------------------------------------------------

@Composable
private fun turmasExemplo() = remember { DadosExemplo.turmas.toMutableStateList() }

@Preview
@Composable
private fun PreviewTablet() {
    EdutecTheme { MinhasTurmasScreen(turmas = turmasExemplo(), pendentes = DadosExemplo.atividadesPendentes) }
}

@Preview
@Composable
private fun PreviewCelular() {
    EdutecTheme { MinhasTurmasScreen(turmas = turmasExemplo(), pendentes = DadosExemplo.atividadesPendentes) }
}

@Preview
@Composable
private fun PreviewVazioTablet() {
    EdutecTheme { MinhasTurmasScreen(turmas = remember { mutableStateListOf() }) }
}

@Preview
@Composable
private fun PreviewVazioCelular() {
    EdutecTheme { MinhasTurmasScreen(turmas = remember { mutableStateListOf() }) }
}

@Preview
@Composable
private fun PreviewCarregandoTablet() {
    EdutecTheme { MinhasTurmasScreen(turmas = emptyList(), carregando = true) }
}

@Preview
@Composable
private fun PreviewErroCelular() {
    EdutecTheme {
        MinhasTurmasScreen(turmas = emptyList(), erro = "Sem conexão com o servidor. Verifique a internet e tente de novo.")
    }
}
