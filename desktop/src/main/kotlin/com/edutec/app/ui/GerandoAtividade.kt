package com.edutec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.PedidoGeracao
import com.edutec.app.ui.theme.Cartao
import com.edutec.app.ui.theme.Consolidado
import com.edutec.app.ui.theme.EdutecTheme
import com.edutec.app.ui.theme.LaranjaFundo
import com.edutec.app.ui.theme.Pilula
import com.edutec.app.ui.theme.Revisar
import com.edutec.app.ui.theme.TextoCorpo
import com.edutec.app.ui.theme.TextoPrincipal
import com.edutec.app.ui.theme.TextoSecundario
import com.edutec.app.ui.theme.Trilho
import java.io.File

private val CantoCartao = RoundedCornerShape(24.dp)

/** Etapa 3: a Edge Function está lendo o PDF e o Gemini está criando as questões. */
@Composable
fun GerandoConteudo(estado: EstadoGeracao.Gerando, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    CartaoCentral(modifier) {
        CircularProgressIndicator(color = Consolidado, trackColor = Trilho, strokeWidth = 5.dp, modifier = Modifier.size(64.dp))
        Text(
            formatarCronometro(estado.segundos),
            color = TextoPrincipal, fontSize = 40.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "${estado.segundos} segundos" },
        )
        Text(
            "A IA está lendo o material e criando as questões — leva cerca de 1 minuto",
            color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Text(descreverPedido(estado.pedido), color = TextoSecundario, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (estado.segundos >= 90) {
            Text(
                "Está demorando mais que o normal. Você pode esperar mais um pouco ou cancelar.",
                color = TextoCorpo, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(4.dp))
        BotaoSecundario("Cancelar", onCancelar)
    }
}

/** Erro na etapa 3: o que aconteceu + o que não foi perdido + o que fazer. */
@Composable
fun ErroGeracaoConteudo(
    estado: EstadoGeracao.Erro,
    onTentarDeNovo: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titulo, explicacao) = when (estado.tipo) {
        TipoErro.Cota -> "A IA atingiu o limite de uso por agora" to
            "O plano gratuito do Gemini aceita poucos pedidos por minuto. Espere cerca de 1 minuto e tente de novo."
        TipoErro.Indisponivel -> "A IA está sobrecarregada agora" to
            "O serviço do Gemini está com muita demanda. Isso costuma passar em instantes: tente de novo."
        TipoErro.TempoEsgotado -> "A IA demorou demais para responder" to
            "Às vezes o serviço fica lento. Tente de novo; se continuar, peça menos questões ou use um PDF menor."
        TipoErro.SemConexao -> "Sem conexão com o servidor" to
            "Confira a internet do aparelho e tente de novo."
        TipoErro.Outro -> "Não foi possível gerar a atividade" to
            "O servidor recusou o pedido ou a IA devolveu uma resposta com problema. Tente de novo."
    }

    CartaoCentral(modifier) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(LaranjaFundo),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = Revisar, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        Text(
            titulo,
            color = TextoPrincipal, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Text(explicacao, color = TextoCorpo, fontSize = 15.sp, textAlign = TextAlign.Center)
        estado.detalhe?.let {
            Text("Detalhe: $it", color = TextoSecundario, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Consolidado, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Nada foi perdido: suas escolhas e o PDF \"${estado.pedido.nomeArquivo}\" continuam aqui.",
                color = Consolidado, fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onTentarDeNovo,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Consolidado),
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Tentar de novo", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        BotaoSecundario("Voltar", onVoltar)
    }
}

@Composable
private fun CartaoCentral(modifier: Modifier, conteudo: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CantoCartao, color = Cartao,
            modifier = Modifier.padding(vertical = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { conteudo() }
        }
    }
}

@Composable
private fun BotaoSecundario(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Pilula, contentColor = TextoPrincipal),
    ) { Text(texto, fontWeight = FontWeight.SemiBold) }
}

private fun formatarCronometro(segundos: Int) = "%d:%02d".format(segundos / 60, segundos % 60)

internal fun descreverPedido(pedido: PedidoGeracao): String {
    val formato = when (pedido.formato) {
        "multipla_escolha" -> "de múltipla escolha"
        "aberta" -> "abertas"
        else -> "mistas"
    }
    val dificuldade = when (pedido.dificuldade) {
        "facil" -> "fácil"
        "dificil" -> "difícil"
        else -> "média"
    }
    return "${pedido.quantidade} questões $formato · $dificuldade · ${pedido.nomeArquivo}"
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

internal val PedidoExemplo = PedidoGeracao(
    arquivo = File("teste.pdf"), nomeArquivo = "Busca competitiva.pdf",
    quantidade = 5, formato = "mista", dificuldade = "media",
)

@Preview
@Composable
private fun PreviewGerandoTablet() {
    EdutecTheme {
        NovaAtividadeScreen(
            DadosExemplo.turmas.first(), onFechar = {},
            estado = EstadoGeracao.Gerando(PedidoExemplo, segundos = 42),
        )
    }
}

@Preview
@Composable
private fun PreviewErroCelular() {
    EdutecTheme {
        NovaAtividadeScreen(
            DadosExemplo.turmas.first(), onFechar = {},
            estado = EstadoGeracao.Erro(PedidoExemplo, TipoErro.Cota),
        )
    }
}
