package com.edutec.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Navegação simples por estado: Minhas turmas → Turma → Nova atividade (Origem/Formato → Gerando → Revisão).
 * A tecla Esc (ou o botão voltar de cada tela) volta uma tela ou uma etapa. Navegação e geração ficam em
 * ViewModels do escopo da janela.
 */
@Composable
fun EdutecApp() {
    val avisar = LocalAvisar.current
    val navegacao = viewModel { NavegacaoViewModel() }
    val geracao = viewModel { NovaAtividadeViewModel() }
    val dadosTurma = viewModel { TurmaViewModel() }

    val turma = navegacao.turmaAberta
    val conceitosIniciais = navegacao.novaAtividade

    when {
        turma == null -> {
            val estadoTurmas = navegacao.turmas
            MinhasTurmasScreen(
                turmas = (estadoTurmas as? EstadoTurmas.Pronto)?.turmas.orEmpty(),
                onTurma = navegacao::abrirTurma,
                onCriarTurma = navegacao::criarTurma,
                carregando = estadoTurmas == EstadoTurmas.Carregando,
                erro = (estadoTurmas as? EstadoTurmas.Erro)?.mensagem,
                onTentarDeNovo = { navegacao.recarregarTurmas() },
            )
        }

        conceitosIniciais == null -> {
            TeclaVoltar { navegacao.fecharTurma() }
            LaunchedEffect(turma.id) { dadosTurma.abrir(turma.id) }
            // Lê o estado sempre (fora do if), para a tela recompor quando o grafo chegar.
            val grafo by dadosTurma.grafo.collectAsState()
            val grafoAtual = grafo
            TurmaScreen(
                turma = turma,
                // Enquanto o ViewModel ainda guarda o grafo de outra turma, mostra "carregando".
                grafo = if (dadosTurma.turmaId == turma.id) grafoAtual else EstadoGrafo.Carregando,
                onTentarDeNovo = dadosTurma::recarregar,
                onSimular = {
                    dadosTurma.simular(turma.id) { erro ->
                        avisar(erro ?: "Simulação concluída: 25 alunos responderam")
                        // Número de alunos e resumo do cartão em Minhas turmas.
                        if (erro == null) navegacao.recarregarTurmas(silencioso = true)
                    }
                },
                simulando = dadosTurma.simulando,
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
            TeclaVoltar { if (estado == EstadoGeracao.Ocioso) fechar() else geracao.voltar() }
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
                onPublicar = { geracao.publicar(turma.id) },
            )
            // Publicada: volta para a Turma com os dados atualizados.
            LaunchedEffect(estado) {
                val publicado = estado
                if (publicado is EstadoGeracao.Publicado) {
                    avisar("Atividade publicada para a turma")
                    geracao.voltar()
                    navegacao.fecharNovaAtividade()
                    navegacao.recarregarTurmas(silencioso = true)
                    dadosTurma.abrir(publicado.turmaId, forcar = true)
                }
            }
        }
    }
}
