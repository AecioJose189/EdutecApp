package com.edutec.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.Turma
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
    turmas: MutableList<Turma> = remember { mutableStateListOf<Turma>().apply { addAll(DadosExemplo.turmas) } },
    onTurma: (Turma) -> Unit = {},
) {
    val context = LocalContext.current
    var semestre by remember { mutableStateOf(DadosExemplo.semestres.first()) }
    var mostrarNovaTurma by remember { mutableStateOf(false) }
    val pendentes = DadosExemplo.atividadesPendentes

    fun avisar(mensagem: String) = Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()

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
                semestre = semestre,
                onSemestreSelecionado = { semestre = it; avisar("Semestre $it selecionado") },
                onPerfil = { avisar("Perfil do professor") },
            )
            Spacer(Modifier.height(8.dp))
            Cabecalho(expandido = expandido)

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

            GradeTurmas(
                turmas = turmas,
                colunas = colunas,
                onTurma = onTurma,
                onNovaTurma = { mostrarNovaTurma = true },
            )
        }
    }

    if (mostrarNovaTurma) {
        DialogoNovaTurma(
            onCancelar = { mostrarNovaTurma = false },
            onCriar = { nome, codigo ->
                turmas.add(
                    Turma(
                        nome = nome,
                        codigo = codigo.ifBlank { "TURMA" }.uppercase(),
                        alunos = 0,
                        consolidado = 0, desenvolvimento = 0, revisar = 0,
                        rodape = "Nenhuma atividade ainda",
                    )
                )
                mostrarNovaTurma = false
                avisar("Turma \"$nome\" criada")
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------

@Composable
private fun BarraSuperior(
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
            text = "EDUTEC",
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
                        text = "Semestre [$semestre]",
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
                DadosExemplo.semestres.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text("Semestre [$opcao]") },
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
private fun Cabecalho(expandido: Boolean) {
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
            Legenda()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            titulo()
            Legenda()
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
    Surface(onClick = onClick, shape = CantoCartao, color = Cartao, modifier = modifier) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(turma.nome, color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "[${turma.codigo}] · ${turma.alunos} alunos",
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
// Diálogo de nova turma
// ---------------------------------------------------------------------------

@Composable
private fun DialogoNovaTurma(onCancelar: () -> Unit, onCriar: (nome: String, codigo: String) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cartao,
        title = { Text("Nova turma", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Disciplina") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Turma (ex.: Turma C)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCriar(nome.trim(), codigo.trim()) }, enabled = nome.isNotBlank()) {
                Text("Criar")
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

@Preview(name = "Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PreviewTablet() {
    EdutecTheme { MinhasTurmasScreen() }
}

@Preview(name = "Celular", widthDp = 400, heightDp = 1400, showBackground = true)
@Composable
private fun PreviewCelular() {
    EdutecTheme { MinhasTurmasScreen() }
}
