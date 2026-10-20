package com.edutec.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.edutec.app.data.DadosExemplo
import com.edutec.app.data.Turma

/**
 * Navegação simples por estado: Minhas turmas → Turma → Nova atividade.
 * O botão "voltar" do Android volta uma tela.
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
            BackHandler { novaAtividade = null }
            NovaAtividadeScreen(
                turma = turma,
                conceitosIniciais = conceitosIniciais,
                onFechar = { novaAtividade = null },
            )
        }
    }
}
