package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AtividadePendente
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.Turma
import com.edutec.app.data.Usuario
import com.edutec.app.data.rotuloAlunos
import com.edutec.app.data.semDados
import com.edutec.app.data.semestresRecentes
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
import com.edutec.app.ui.theme.EstadoConsolidado
import com.edutec.app.ui.theme.EstadoDesenvolvimento
import com.edutec.app.ui.theme.EstadoRevisar
import com.edutec.app.ui.theme.EstiloApoio
import com.edutec.app.ui.theme.EstiloNumero
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.FormaPilula
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.Menta
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.RaioLg
import com.edutec.app.ui.theme.SobrePrimaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import com.edutec.app.ui.theme.comNumeros
import org.jetbrains.compose.ui.tooling.preview.Preview

private val CantoCartao = FormaLg
private val EspacoGrade = Espaco4

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
    /** Professor logado (nome e foto no topo, com "Sair"). */
    usuario: Usuario? = null,
    onSair: () -> Unit = {},
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
        val margem = if (largura >= 600.dp) Espaco6 else Espaco4

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = margem, vertical = Espaco6),
            verticalArrangement = Arrangement.spacedBy(EspacoGrade),
        ) {
            BarraSuperior(
                semestres = semestres,
                semestre = semestre,
                onSemestreSelecionado = { semestre = it; avisar("Semestre $it selecionado") },
                usuario = usuario,
                onSair = onSair,
            )
            Spacer(Modifier.height(Espaco2))
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
    usuario: Usuario?,
    onSair: () -> Unit,
) {
    var menuAberto by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LUMINI",
            color = Tinta,
            fontFamily = Sora, fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.weight(1f))

        Box {
            Surface(
                onClick = { menuAberto = true },
                shape = FormaPilula,
                color = MentaSuave,
            ) {
                Row(
                    modifier = Modifier.padding(start = Espaco4, end = Espaco3, top = Espaco3, bottom = Espaco3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Semestre $semestre",
                        color = Tinta,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(Espaco2))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Trocar semestre",
                        tint = Tinta,
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

        Spacer(Modifier.width(Espaco4))

        if (usuario != null) MenuUsuario(usuario, onSair)
    }
}

/** Nome e foto do professor logado; o menu mostra o e-mail e "Sair". */
@Composable
private fun MenuUsuario(usuario: Usuario, onSair: () -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { aberto = true },
            shape = FormaPilula,
            color = MentaSuave,
            modifier = Modifier.maoAoPassar(),
        ) {
            Row(Modifier.padding(start = Espaco2, end = Espaco4, top = Espaco2, bottom = Espaco2), verticalAlignment = Alignment.CenterVertically) {
                FotoUsuario(usuario.nome, usuario.fotoUrl, tamanho = 36.dp)
                Spacer(Modifier.width(Espaco3))
                Text(usuario.nome, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            usuario.email?.let {
                Text(it, color = TintaSuave, fontSize = 13.sp, modifier = Modifier.padding(horizontal = Espaco4, vertical = Espaco2))
            }
            DropdownMenuItem(text = { Text("Sair") }, onClick = { aberto = false; onSair() })
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
            color = Tinta,
            fontFamily = Sora, fontSize = 30.sp,
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
        Column(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
            titulo()
            if (mostrarLegenda) Legenda()
        }
    }
}

@Composable
private fun Legenda() {
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco4)) {
        EstadoConceito.entries.reversed().forEach { ItemLegenda(it) }
    }
}

@Composable
private fun ItemLegenda(estado: EstadoConceito) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeloEstado(estado, tamanho = 18.dp)
        Spacer(Modifier.width(Espaco2))
        Text(estado.rotulo, color = TintaSuave, fontSize = 14.sp)
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
            estado = EstadoConceito.Revisar.takeIf { conceitosARevisar > 0 },
            modifier = mod,
        )
    }
    val cartaoAlunos: @Composable (Modifier) -> Unit = { mod ->
        CartaoNumero(
            numero = totalAlunos.toString(),
            legenda = "alunos neste semestre",
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

    Surface(shape = CantoCartao, color = MentaSuave, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Espaco6, vertical = Espaco4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(FormaMd)
                    .background(Primaria),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = SobrePrimaria,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Espaco4))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(descricao, color = Tinta, fontSize = 14.sp)
            }
            TextButton(onClick = onRevisar) {
                Text("Revisar →", color = Primaria, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CartaoNumero(
    numero: String,
    legenda: String,
    /** Estado de aprendizagem do número (ex.: conceitos a revisar), mostrado com o símbolo ao lado. */
    estado: EstadoConceito? = null,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = Espaco6, vertical = Espaco4),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(numero, color = Tinta, style = EstiloNumero)
                if (estado != null) {
                    Spacer(Modifier.width(Espaco2))
                    SeloEstado(estado, tamanho = 24.dp)
                }
            }
            Text(legenda, color = TintaSuave, fontSize = 14.sp)
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
    Surface(onClick = onClick, shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier.maoAoPassar()) {
        Column(Modifier.padding(horizontal = Espaco6, vertical = Espaco6)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(turma.nome, color = Tinta, fontFamily = Sora, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${turma.codigo} · ${turma.rotuloAlunos}",
                        color = TintaSuave,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(Espaco2))
                EtiquetaTurma(turma)
            }

            Spacer(Modifier.height(Espaco4))
            BarraProgresso(turma.consolidado, turma.desenvolvimento, turma.revisar)
            Spacer(Modifier.height(Espaco4))

            val semDados = turma.consolidado + turma.desenvolvimento + turma.revisar == 0
            Text(
                text = if (semDados) "Sem dados de aprendizagem ainda"
                else "✓ ${turma.consolidado}% consolidado · ~ ${turma.desenvolvimento}% em desenvolvimento · " +
                    "! ${turma.revisar}% revisar",
                color = TintaSuave,
                style = EstiloApoio.comNumeros(),
            )

            Spacer(Modifier.height(Espaco6))
            Spacer(Modifier.weight(1f))
            Text(turma.rodape, color = TintaSuave, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EtiquetaTurma(turma: Turma) {
    val (texto, fundo, cor) = when {
        turma.semDados -> Triple("Nova turma", Menta, Tinta)
        turma.conceitosARevisar > 0 -> {
            ChipEstado(
                EstadoConceito.Revisar,
                texto = if (turma.conceitosARevisar == 1) "1 conceito a revisar"
                else "${turma.conceitosARevisar} conceitos a revisar",
            )
            return
        }
        turma.status != null -> Triple(turma.status, Menta, Tinta)
        else -> Triple("Sem alertas", Menta, Tinta)
    }

    Surface(shape = FormaPilula, color = fundo) {
        Text(
            text = texto,
            color = cor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Espaco3, vertical = Espaco2),
        )
    }
}

@Composable
private fun BarraProgresso(consolidado: Int, desenvolvimento: Int, revisar: Int) {
    val segmentos = listOf(
        consolidado to EstadoConsolidado,
        desenvolvimento to EstadoDesenvolvimento,
        revisar to EstadoRevisar,
    ).filter { it.first > 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(FormaPilula)
            // Fundo branco faz o espaço entre os segmentos parecer um corte.
            .background(if (segmentos.isEmpty()) Borda else Superficie),
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
    val contorno = BordaControle
    Box(
        modifier = modifier
            .heightIn(min = 180.dp)
            .clip(CantoCartao)
            .clickable(onClick = onClick)
            .maoAoPassar()
            .drawBehind {
                val espessura = 2.dp.toPx()
                drawRoundRect(
                    color = contorno,
                    topLeft = Offset(espessura / 2, espessura / 2),
                    size = Size(size.width - espessura, size.height - espessura),
                    cornerRadius = CornerRadius(RaioLg.toPx()),
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
                    .clip(FormaMd)
                    .background(MentaSuave),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Primaria,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(Espaco3))
            Text("Nova turma", color = Primaria, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Estado vazio (nenhuma turma)
// ---------------------------------------------------------------------------

@Composable
private fun EstadoVazioTurmas(onNovaTurma: () -> Unit) {
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espaco8, vertical = Espaco12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco4),
        ) {
            Box(
                Modifier.size(64.dp).clip(FormaLg).background(MentaSuave),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = Primaria, modifier = Modifier.size(30.dp)) }
            Text(
                "Nenhuma turma ainda",
                color = Tinta, fontFamily = Sora, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "Crie a primeira turma para enviar materiais em PDF, gerar atividades com a IA e, quando os " +
                    "alunos responderem, acompanhar o domínio estimado de cada conceito.",
                color = TintaSuave, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(Espaco1))
            Button(
                onClick = onNovaTurma,
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Espaco2))
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
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espaco8, vertical = Espaco12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco4),
        ) {
            CircularProgressIndicator(color = Primaria)
            Text("Carregando suas turmas…", color = TintaSuave, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErroTurmas(mensagem: String, onTentarDeNovo: () -> Unit) {
    Surface(shape = CantoCartao, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espaco8, vertical = Espaco12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco4),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MentaSuave),
                contentAlignment = Alignment.Center,
            ) { Text("!", color = Erro, fontFamily = Sora, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Não foi possível carregar as turmas",
                color = Tinta, fontFamily = Sora, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                mensagem, color = TintaSuave, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Button(
                onClick = onTentarDeNovo,
                shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco4),
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
        containerColor = Superficie,
        title = { Text("Nova turma", fontWeight = FontWeight.Bold) },
        text = {
            // Enter em qualquer campo confirma (Esc fecha o diálogo).
            Column(
                verticalArrangement = Arrangement.spacedBy(Espaco3),
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
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Primaria)
                        Spacer(Modifier.width(Espaco3))
                        Text("Criando a turma…", color = TintaSuave, fontSize = 14.sp)
                    }
                }
                falha?.let { Text(it, color = Erro, fontSize = 14.sp) }
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
