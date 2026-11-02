package com.edutec.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.Membro
import com.edutec.app.ui.theme.AzulFundo
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EtiquetaAzulTexto
import com.edutec.app.ui.theme.EtiquetaVermelhaTexto
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Aba Atividades: as publicadas, cada uma com "Abrir sessão em sala"
// ---------------------------------------------------------------------------

@Composable
fun AbaAtividades(
    atividades: Carga<List<AtividadePublicada>>,
    onAbrirSessao: (AtividadePublicada) -> Unit,
    onNovaAtividade: () -> Unit,
    onTentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CartaoAba("Atividades publicadas", modifier) {
        when (atividades) {
            Carga.Carregando -> CarregandoAba()
            is Carga.Erro -> ErroAba(atividades.mensagem, onTentarDeNovo)
            is Carga.Pronto -> if (atividades.valor.isEmpty()) {
                VazioAba(
                    "Nenhuma atividade publicada ainda.",
                    "Gere uma atividade a partir de um PDF, revise e publique para abrir a sessão em sala.",
                ) {
                    Button(onClick = onNovaAtividade, colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                        modifier = Modifier.maoAoPassar()) { Text("Nova atividade") }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(atividades.valor.asReversed(), key = { it.id }) { ItemAtividade(it, onAbrirSessao) }
                }
            }
        }
    }
}

@Composable
private fun ItemAtividade(atividade: AtividadePublicada, onAbrirSessao: (AtividadePublicada) -> Unit) {
    val emAndamento = atividade.sessaoAberta != null
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (emAndamento) AzulFundo else Cartao,
        border = BorderStroke(if (emAndamento) 2.dp else 1.dp, if (emAndamento) Consolidado else Trilho),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(atividade.titulo, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append("${atividade.questoes} questões")
                        append(" · ${atividade.tempoMinutos?.let { "$it min" } ?: "sem limite de tempo"}")
                        append(" · publicada em ${dataCurta(atividade.publicadaEm)}")
                    },
                    color = TextoSecundario, fontSize = 14.sp,
                )
                if (emAndamento) {
                    Text("Sessão em andamento", color = EtiquetaAzulTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { onAbrirSessao(atividade) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.maoAoPassar(),
            ) { Text(if (emAndamento) "Voltar à sessão" else "Abrir sessão em sala", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Aba Turma: alunos matriculados (aceitaram o convite)
// ---------------------------------------------------------------------------

@Composable
fun AbaMembros(
    membros: Carga<List<Membro>>,
    onConvidar: () -> Unit,
    onTentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = (membros as? Carga.Pronto)?.valor?.size?.let { "Alunos da turma ($it)" } ?: "Alunos da turma"
    CartaoAba(titulo, modifier, acao = {
        TextButton(onClick = onConvidar, modifier = Modifier.maoAoPassar()) { Text("Convidar alunos", color = Consolidado) }
    }) {
        when (membros) {
            Carga.Carregando -> CarregandoAba()
            is Carga.Erro -> ErroAba(membros.mensagem, onTentarDeNovo)
            is Carga.Pronto -> if (membros.valor.isEmpty()) {
                VazioAba("Nenhum aluno ainda.", "Os alunos aparecem aqui quando aceitam o convite da turma.") {
                    Button(onClick = onConvidar, colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
                        modifier = Modifier.maoAoPassar()) { Text("Convidar alunos") }
                }
            } else {
                LazyColumn {
                    items(membros.valor, key = { it.email ?: it.nome + it.entrouEm }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FotoUsuario(m.nome, null, tamanho = 38.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.nome, color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                m.email?.let { Text(it, color = TextoSecundario, fontSize = 13.sp) }
                            }
                            Text("entrou em ${dataCurta(m.entrouEm)}", color = TextoSecundario, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Trilho)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Peças comuns
// ---------------------------------------------------------------------------

@Composable
private fun CartaoAba(
    titulo: String,
    modifier: Modifier,
    acao: @Composable () -> Unit = {},
    conteudo: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = Cartao, modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                acao()
            }
            conteudo()
        }
    }
}

@Composable
private fun CarregandoAba() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Consolidado)
    }
}

@Composable
private fun ErroAba(mensagem: String, onTentarDeNovo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mensagem, color = EtiquetaVermelhaTexto, fontSize = 15.sp)
        TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo") }
    }
}

@Composable
private fun VazioAba(titulo: String, texto: String, acao: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titulo, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, color = TextoCorpo, fontSize = 14.sp)
        acao()
    }
}

private val FormatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "28/10/2026" no fuso do computador (o servidor manda ISO 8601). */
fun dataCurta(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(FormatoData) }.getOrDefault(iso.take(10))
