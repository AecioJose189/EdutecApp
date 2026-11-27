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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.AtividadePublicada
import com.edutec.app.data.EstadoAtividade
import com.edutec.app.data.Membro
import com.edutec.app.ui.theme.Borda
import com.edutec.app.ui.theme.BordaControle
import com.edutec.app.ui.theme.FormaPilula
import com.edutec.app.ui.theme.Fundo
import com.edutec.app.ui.theme.SobrePrimaria
import com.edutec.app.ui.theme.Erro
import com.edutec.app.ui.theme.Espaco1
import com.edutec.app.ui.theme.Espaco2
import com.edutec.app.ui.theme.Espaco3
import com.edutec.app.ui.theme.Espaco4
import com.edutec.app.ui.theme.Espaco6
import com.edutec.app.ui.theme.Espaco8
import com.edutec.app.ui.theme.EspessuraBorda
import com.edutec.app.ui.theme.FormaLg
import com.edutec.app.ui.theme.FormaMd
import com.edutec.app.ui.theme.MentaSuave
import com.edutec.app.ui.theme.Primaria
import com.edutec.app.ui.theme.Sora
import com.edutec.app.ui.theme.Superficie
import com.edutec.app.ui.theme.Tinta
import com.edutec.app.ui.theme.TintaSuave
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Aba Atividades: as publicadas, cada uma com "Abrir sessão em sala"
// ---------------------------------------------------------------------------

/** Atividade gerada e ainda não publicada (só existe com o app aberto). */
data class RascunhoAtividade(val titulo: String, val questoes: Int)

@Composable
fun AbaAtividades(
    atividades: Carga<List<AtividadePublicada>>,
    onAbrirSessao: (AtividadePublicada) -> Unit,
    onNovaAtividade: () -> Unit,
    onTentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
    rascunho: RascunhoAtividade? = null,
    onContinuarRascunho: () -> Unit = {},
    /** Conferindo se a turma tem alunos antes de abrir a sessão. */
    abrindoSessao: Boolean = false,
) {
    CartaoAba("Atividades", modifier) {
        when (atividades) {
            Carga.Carregando -> CarregandoAba()
            is Carga.Erro -> ErroAba(atividades.mensagem, onTentarDeNovo)
            is Carga.Pronto -> if (atividades.valor.isEmpty() && rascunho == null) {
                VazioAba(
                    "Nenhuma atividade publicada ainda.",
                    "Gere uma atividade a partir de um PDF, revise e publique para abrir a sessão em sala.",
                ) {
                    Button(onClick = onNovaAtividade, colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                        modifier = Modifier.maoAoPassar()) { Text("Nova atividade") }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Espaco3)) {
                    if (rascunho != null) {
                        item(key = "rascunho") {
                            ItemRascunho(atividades.valor.size + 1, rascunho, onContinuarRascunho)
                        }
                    }
                    items(atividades.valor.asReversed(), key = { it.id }) { ItemAtividade(it, onAbrirSessao, abrindoSessao) }
                }
            }
        }
    }
}

/** "Atividade 2 · Probabilidade" (sem número, se o servidor ainda não manda). */
private fun tituloNumerado(numero: Int, titulo: String) = if (numero > 0) "Atividade $numero · $titulo" else titulo

@Composable
private fun ItemAtividade(atividade: AtividadePublicada, onAbrirSessao: (AtividadePublicada) -> Unit, abrindoSessao: Boolean) {
    val estado = atividade.estado
    val emAndamento = estado == EstadoAtividade.SessaoAberta
    LinhaAtividade(
        titulo = tituloNumerado(atividade.numero, atividade.titulo),
        detalhe = buildString {
            append("publicada em ${diaMes(atividade.publicadaEm)}")
            append(" · ${atividade.enviadas} ${if (atividade.enviadas == 1) "resposta" else "respostas"}")
            append(" · ${atividade.questoes} questões")
            append(" · ${atividade.tempoMinutos?.let { "$it min" } ?: "sem limite de tempo"}")
            atividade.ultimaEncerradaEm?.takeIf { estado == EstadoAtividade.Encerrada }?.let {
                append(" · encerrada em ${diaMes(it)}")
            }
        },
        estado = estado,
        destaque = emAndamento,
    ) {
        val texto = when (estado) {
            EstadoAtividade.SessaoAberta -> "Voltar à sessão"
            EstadoAtividade.Encerrada -> "Abrir outra sessão"
            else -> "Abrir sessão em sala"
        }
        if (estado == EstadoAtividade.Encerrada) {
            OutlinedButton(
                onClick = { onAbrirSessao(atividade) }, enabled = !abrindoSessao, shape = FormaMd,
                border = BorderStroke(1.dp, Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco3),
                modifier = Modifier.maoAoPassar(),
            ) { Text(texto, color = Primaria, fontWeight = FontWeight.SemiBold) }
        } else {
            Button(
                onClick = { onAbrirSessao(atividade) }, enabled = !abrindoSessao, shape = FormaMd,
                colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco3),
                modifier = Modifier.maoAoPassar(),
            ) { Text(texto, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ItemRascunho(numero: Int, rascunho: RascunhoAtividade, onContinuar: () -> Unit) {
    LinhaAtividade(
        titulo = tituloNumerado(numero, rascunho.titulo),
        detalhe = "${rascunho.questoes} questões geradas · ainda não publicada: os alunos não veem",
        estado = EstadoAtividade.Rascunho,
        destaque = false,
    ) {
        Button(
            onClick = onContinuar, shape = FormaMd,
            colors = ButtonDefaults.buttonColors(containerColor = Primaria),
            contentPadding = PaddingValues(horizontal = Espaco6, vertical = Espaco3),
            modifier = Modifier.maoAoPassar(),
        ) { Text("Revisar e publicar", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun LinhaAtividade(
    titulo: String,
    detalhe: String,
    estado: EstadoAtividade,
    destaque: Boolean,
    botao: @Composable () -> Unit,
) {
    Surface(
        shape = FormaLg,
        color = if (destaque) MentaSuave else Superficie,
        border = BorderStroke(if (destaque) 2.dp else 1.dp, if (destaque) Primaria else Borda),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Espaco6, vertical = Espaco4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espaco1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titulo, color = Tinta, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Espaco3))
                    SeloAtividade(estado)
                }
                Text(detalhe, color = TintaSuave, fontSize = 14.sp)
            }
            Spacer(Modifier.width(Espaco4))
            botao()
        }
    }
}

/** Selo de estado da atividade: texto sempre (não só cor). */
@Composable
fun SeloAtividade(estado: EstadoAtividade, modifier: Modifier = Modifier) {
    val (fundo, texto, borda) = when (estado) {
        EstadoAtividade.Rascunho -> Triple(Fundo, TintaSuave, BordaControle)
        EstadoAtividade.Publicada -> Triple(MentaSuave, Tinta, Primaria)
        EstadoAtividade.SessaoAberta -> Triple(Primaria, SobrePrimaria, Primaria)
        EstadoAtividade.Encerrada -> Triple(Fundo, TintaSuave, Borda)
    }
    Surface(shape = FormaPilula, color = fundo, border = BorderStroke(1.dp, borda), modifier = modifier) {
        Text(
            (if (estado == EstadoAtividade.SessaoAberta) "● " else "") + estado.rotulo,
            color = texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Espaco3, vertical = 2.dp),
        )
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
        TextButton(onClick = onConvidar, modifier = Modifier.maoAoPassar()) { Text("Convidar alunos", color = Primaria) }
    }) {
        when (membros) {
            Carga.Carregando -> CarregandoAba()
            is Carga.Erro -> ErroAba(membros.mensagem, onTentarDeNovo)
            is Carga.Pronto -> if (membros.valor.isEmpty()) {
                VazioAba("Nenhum aluno ainda.", "Os alunos aparecem aqui quando aceitam o convite da turma.") {
                    Button(onClick = onConvidar, colors = ButtonDefaults.buttonColors(containerColor = Primaria),
                        modifier = Modifier.maoAoPassar()) { Text("Convidar alunos") }
                }
            } else {
                LazyColumn {
                    items(membros.valor, key = { it.email ?: it.nome + it.entrouEm }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Espaco3), verticalAlignment = Alignment.CenterVertically) {
                            FotoUsuario(m.nome, null, tamanho = 38.dp)
                            Spacer(Modifier.width(Espaco4))
                            Column(Modifier.weight(1f)) {
                                Text(m.nome, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                m.email?.let { Text(it, color = TintaSuave, fontSize = 13.sp) }
                            }
                            Text("entrou em ${dataCurta(m.entrouEm)}", color = TintaSuave, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Borda)
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
    Surface(shape = FormaLg, color = Superficie, border = BorderStroke(EspessuraBorda, Borda), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = Espaco6, vertical = Espaco6), verticalArrangement = Arrangement.spacedBy(Espaco4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, color = Tinta, fontFamily = Sora, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                acao()
            }
            conteudo()
        }
    }
}

@Composable
private fun CarregandoAba() {
    Box(Modifier.fillMaxWidth().padding(Espaco8), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Primaria)
    }
}

@Composable
private fun ErroAba(mensagem: String, onTentarDeNovo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Espaco6), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espaco2)) {
        Text(mensagem, color = Erro, fontSize = 15.sp)
        TextButton(onClick = onTentarDeNovo, modifier = Modifier.maoAoPassar()) { Text("Tentar de novo") }
    }
}

@Composable
private fun VazioAba(titulo: String, texto: String, acao: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Espaco8), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espaco3)) {
        Text(titulo, color = Tinta, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, color = TintaSuave, fontSize = 14.sp)
        acao()
    }
}

private val FormatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FormatoDiaMes = DateTimeFormatter.ofPattern("dd/MM")

/** "28/10" no fuso do computador. */
fun diaMes(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(FormatoDiaMes) }.getOrDefault(iso.take(10))

/** "28/10/2026" no fuso do computador (o servidor manda ISO 8601). */
fun dataCurta(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(FormatoData) }.getOrDefault(iso.take(10))
