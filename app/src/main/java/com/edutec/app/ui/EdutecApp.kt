package com.edutec.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.Turma

/**
 * Navegação simples por estado: Minhas turmas → Turma → Nova atividade (Origem/Formato → Gerando → Revisão).
 * O botão "voltar" do Android volta uma tela ou uma etapa.
 */
@Composable
fun EdutecApp() {
    val turmas = remember { mutableStateListOf<Turma>().apply { addAll(DadosExemplo.turmas) } }
    var turmaAberta by remember { mutableStateOf<Turma?>(null) }
    // null = tela de nova atividade fechada; caso contrário, os conceitos já marcados ao abrir.
    var novaAtividade by remember { mutableStateOf<Set<String>?>(null) }

    val turma = turmaAberta
    val conceitosIniciais = novaAtividade

    when {
        turma == null -> MinhasTurmasScreen(turmas = turmas, onTurma = { turmaAberta = it })

        conceitosIniciais == null -> {
            BackHandler { turmaAberta = null }
            TurmaScreen(
                turma = turma,
                onVoltar = { turmaAberta = null },
                onNovaAtividade = { novaAtividade = it },
            )
        }

        else -> {
            val geracao: NovaAtividadeViewModel = viewModel()
            val estado by geracao.estado.collectAsState()
            val fechar = {
                geracao.voltar() // cancela uma geração em andamento
                novaAtividade = null
            }
            BackHandler { if (estado == EstadoGeracao.Ocioso) fechar() else geracao.voltar() }
            NovaAtividadeScreen(
                turma = turma,
                conceitosIniciais = conceitosIniciais,
                onFechar = fechar,
                estado = estado,
                onGerar = geracao::gerar,
                onTentarDeNovo = geracao::tentarDeNovo,
                onVoltar = geracao::voltar,
            )
        }
    }
}
