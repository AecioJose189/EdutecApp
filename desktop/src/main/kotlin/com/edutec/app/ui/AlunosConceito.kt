package com.edutec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AlunoNoConceito
import com.edutec.app.data.AlunosDoConceito
import com.edutec.app.data.EstadoConceito
import com.edutec.app.data.TreinoConceito
import com.edutec.app.data.agrupar
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.EstiloTitulo3
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import com.edutec.app.ui.theme.comNumeros
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

const val TEXTO_NINGUEM_COM_DIFICULDADE = "Nenhum aluno em 'revisar' ou 'em desenvolvimento' neste conceito."

/**
 * "Ver alunos com dificuldade" (fase 5): alunos reais no conceito, os piores primeiro, separados em
 * "Revisar" e "Em desenvolvimento"; consolidados e sem dados ficam em "Ver todos". Clicar num aluno abre
 * o mapa do aluno com o conceito selecionado e o histórico.
 */
@Composable
fun DialogoAlunosConceito(
    conceito: String,
    alunos: Carga<AlunosDoConceito>,
    onAluno: (AlunoNoConceito) -> Unit,
    onTentarDeNovo: () -> Unit,
    onFechar: () -> Unit,
    todosAbertoInicial: Boolean = false,
) {
    TeclaVoltar(onFechar)
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Superficie,
        title = { Text("Alunos em \"$conceito\"") },
        text = {
            Box(Modifier.heightIn(max = 520.dp)) {
                when (alunos) {
                    Carga.Carregando -> Box(Modifier.fillMaxWidth().padding(Espaco4), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primaria)
                    }
                    is Carga.Erro -> Column(verticalArrangement = Arrangement.spacedBy(Espaco2)) {
                        Text(alunos.mensagem, color = Erro, fontSize = 14.sp)
                        TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo", color = Primaria) }
                    }
                    is Carga.Pronto -> ListaAlunosConceito(alunos.valor, onAluno, todosAbertoInicial)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar, modifier = Modifier.maoAoPassar()) { Text("Fechar", color = Primaria) }
        },
    )
}

@Composable
private fun ListaAlunosConceito(dados: AlunosDoConceito, onAluno: (AlunoNoConceito) -> Unit, todosAbertoInicial: Boolean) {
    val grupos = dados.agrupar()
    var todos by rememberSaveable(dados.conceito) { mutableStateOf(todosAbertoInicial) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Espaco3)) {
        Text(
            "Só alunos reais, os de menor domínio estimado primeiro. Clique num aluno para abrir o mapa e o histórico neste conceito.",
            color = TintaSuave, fontSize = 13.sp,
        )
        if (dados.alunos.isEmpty()) {
            Text("A turma ainda não tem alunos.", color = TintaSuave, fontSize = 14.sp)
            return@Column
        }
        if (grupos.ninguemComDificuldade) {
            Text(TEXTO_NINGUEM_COM_DIFICULDADE, color = Tinta, fontSize = 14.sp)
        }
        Grupo(EstadoConceito.Revisar, "Revisar", grupos.revisar, onAluno)
        Grupo(EstadoConceito.Desenvolvimento, "Em desenvolvimento", grupos.desenvolvimento, onAluno)
        if (grupos.demais.isNotEmpty()) {
            TextButton(onClick = { todos = !todos }, modifier = Modifier.maoAoPassar()) {
                Text(
                    if (todos) "Ocultar os demais" else "Ver todos (mais ${grupos.demais.size}: consolidados e sem dados)",
                    color = Primaria, fontWeight = FontWeight.SemiBold,
                )
            }
            if (todos) Grupo(null, "Consolidado e sem dados", grupos.demais, onAluno)
        }
    }
}

@Composable
private fun Grupo(estado: EstadoConceito?, titulo: String, alunos: List<AlunoNoConceito>, onAluno: (AlunoNoConceito) -> Unit) {
    if (alunos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Espaco1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (estado != null) {
                SeloEstado(estado, tamanho = 18.dp)
                Spacer(Modifier.width(Espaco2))
            }
            Text("$titulo (${alunos.size})", color = Tinta, style = EstiloTitulo3.copy(fontSize = 15.sp))
        }
        alunos.forEach { a ->
            LinhaAluno(a, onClick = { onAluno(a) })
            HorizontalDivider(color = Borda)
        }
    }
}

@Composable
private fun LinhaAluno(a: AlunoNoConceito, onClick: () -> Unit) {
    val estado = a.estado
    Surface(
        onClick = onClick, shape = FormaMd, color = Superficie,
        modifier = Modifier.fillMaxWidth().maoAoPassar().semantics {
            contentDescription = "${a.nome}: ${estado?.rotulo ?: "sem dados"}. Abrir o mapa do aluno neste conceito."
        },
    ) {
        Row(Modifier.padding(vertical = Espaco2, horizontal = Espaco1), verticalAlignment = Alignment.CenterVertically) {
            SeloEstado(estado, tamanho = 22.dp)
            Spacer(Modifier.width(Espaco3))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.nome, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Espaco2))
                    Text(
                        a.dominio?.let { "domínio estimado ${(it * 100).roundToInt()}%" } ?: "sem dados",
                        color = if (a.poucosDados) TintaSuave else Tinta,
                        style = EstiloTitulo3.comNumeros().copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco2)) {
                    Text(detalhesAluno(a), color = TintaSuave, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    if (a.poucosDados) SeloPoucosDados()
                }
            }
        }
    }
}

/** "4 respostas · 1 erro com certeza · 0 acertos no chute · treinou 6 vezes (4 acertos)". */
internal fun detalhesAluno(a: AlunoNoConceito): String = buildList {
    add(if (a.respostas == 1) "1 resposta" else "${a.respostas} respostas")
    add(if (a.errosComCerteza == 1) "1 erro com certeza" else "${a.errosComCerteza} erros com certeza")
    add(if (a.acertosNoChute == 1) "1 acerto no chute" else "${a.acertosNoChute} acertos no chute")
    add(if (a.treino.treinos == 0) "não treinou com flashcards"
        else com.edutec.app.data.textoTreinoConceito(a.treino).replaceFirstChar { it.lowercase() })
}.joinToString(" · ")

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

internal val AlunosConceitoExemplo = AlunosDoConceito(
    conceito = "Regra da cadeia",
    alunos = listOf(
        AlunoNoConceito("1", "Bruno Lima", 0.18, "revisar", 6, errosComCerteza = 2, acertosNoChute = 0, treino = TreinoConceito(5, 2)),
        AlunoNoConceito("2", "Carla Mendes", 0.31, "revisar", 2, errosComCerteza = 1),
        AlunoNoConceito("3", "Ana Souza", 0.64, "em_desenvolvimento", 5, acertosNoChute = 2, treino = TreinoConceito(8, 7)),
        AlunoNoConceito("4", "Diego Rocha", 0.91, "consolidado", 7),
        AlunoNoConceito("5", "Elisa Prado"),
    ),
)

@Preview
@Composable
private fun PreviewAlunosConceito() {
    EdutecTheme { DialogoAlunosConceito("Regra da cadeia", Carga.Pronto(AlunosConceitoExemplo), {}, {}, {}) }
}

@Preview
@Composable
private fun PreviewAlunosConceitoTodos() {
    EdutecTheme { DialogoAlunosConceito("Regra da cadeia", Carga.Pronto(AlunosConceitoExemplo), {}, {}, {}, todosAbertoInicial = true) }
}

@Preview
@Composable
private fun PreviewAlunosConceitoNinguem() {
    val ninguem = AlunosConceitoExemplo.copy(alunos = AlunosConceitoExemplo.alunos.drop(3))
    EdutecTheme { DialogoAlunosConceito("Regra da cadeia", Carga.Pronto(ninguem), {}, {}, {}) }
}
