package com.edutec.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Navegação simples por estado: Minhas turmas → Turma → Nova atividade (Origem/Formato → Gerando → Revisão).
 * O botão "voltar" do Android volta uma tela ou uma etapa. Navegação e geração ficam em ViewModels
 * (escopo da Activity), então girar a tela não perde a tela atual, a geração em andamento nem o resultado.
 */
@Composable
fun EdutecApp() {
    val navegacao: NavegacaoViewModel = viewModel()
    val geracao: NovaAtividadeViewModel = viewModel()

    val turma = navegacao.turmaAberta
    val conceitosIniciais = navegacao.novaAtividade

    when {
        turma == null -> MinhasTurmasScreen(turmas = navegacao.turmas, onTurma = navegacao::abrirTurma)

        conceitosIniciais == null -> {
            BackHandler { navegacao.fecharTurma() }
            TurmaScreen(
                turma = turma,
                onVoltar = navegacao::fecharTurma,
                onNovaAtividade = {
                    geracao.voltar() // não mostrar o resultado de uma sessão anterior
                    navegacao.abrirNovaAtividade(it)
                },
            )
        }

        else -> {
            val estado by geracao.estado.collectAsState()
            val fechar = {
                geracao.voltar() // cancela uma geração em andamento
                navegacao.fecharNovaAtividade()
            }
            BackHandler { if (estado == EstadoGeracao.Ocioso) fechar() else geracao.voltar() }
            NovaAtividadeScreen(
                turma = turma,
                conceitosIniciais = conceitosIniciais,
                // Ainda não há banco com o grafo da turma: só dá para gerar a partir de PDF.
                conceitos = emptyList(),
                onFechar = fechar,
                estado = estado,
                onGerar = geracao::gerar,
                onTentarDeNovo = geracao::tentarDeNovo,
                onVoltar = geracao::voltar,
            )
        }
    }
}
