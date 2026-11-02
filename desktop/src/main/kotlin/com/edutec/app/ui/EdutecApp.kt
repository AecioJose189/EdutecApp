package com.edutec.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edutec.app.data.Usuario

/**
 * Entrada (login Google) e navegação simples por estado: Minhas turmas → Turma (Grafo, Atividades,
 * Turma) → Nova atividade (Origem/Formato → Gerando → Revisão) ou Projeção da sessão em sala.
 * A tecla Esc (ou o botão voltar de cada tela) volta uma tela ou uma etapa. Tudo fica em ViewModels
 * da janela: se a sessão expirar (401), aparece a entrada e, depois do login, a mesma tela de antes.
 */
@Composable
fun EdutecApp() {
    val sessao = viewModel { SessaoViewModel() }
    when (val estado = sessao.estado) {
        EstadoSessao.Iniciando -> CarregandoSessao()
        is EstadoSessao.Deslogado -> LoginScreen(
            aviso = estado.aviso,
            entrando = sessao.entrando,
            urlLogin = sessao.urlLogin,
            erro = sessao.erro,
            onEntrar = sessao::entrar,
            onCancelar = sessao::cancelarLogin,
        )
        is EstadoSessao.Logado -> AppDoProfessor(estado.usuario, onSair = sessao::sair)
    }
}

@Composable
private fun AppDoProfessor(usuario: Usuario, onSair: () -> Unit) {
    val avisar = LocalAvisar.current
    val navegacao = viewModel { NavegacaoViewModel() }
    val geracao = viewModel { NovaAtividadeViewModel() }
    val dadosTurma = viewModel { TurmaViewModel() }
    val projecao = viewModel { ProjecaoViewModel() }

    LaunchedEffect(usuario.id) { navegacao.entrou(usuario.id) }

    val turma = navegacao.turmaAberta
    val conceitosIniciais = navegacao.novaAtividade
    val atividadeProjetada = navegacao.projecao

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
                usuario = usuario,
                onSair = onSair,
            )
        }

        atividadeProjetada != null -> {
            LaunchedEffect(atividadeProjetada.id) {
                if (projecao.atividade?.id != atividadeProjetada.id) projecao.abrir(atividadeProjetada)
            }
            val sair = {
                projecao.fechar()
                navegacao.fecharProjecao()
                // De volta à turma com a lista de atividades e o grafo atualizados.
                dadosTurma.carregarAba(TurmaViewModel.ABA_ATIVIDADES)
                dadosTurma.abrir(turma.id, forcar = true)
                navegacao.recarregarTurmas(silencioso = true)
            }
            ProjecaoScreen(
                atividade = atividadeProjetada,
                nomeTurma = "${turma.nome} · ${turma.codigo}",
                sessao = projecao.sessao,
                qr = projecao.qr,
                segundosParaTrocar = projecao.segundosParaTrocar,
                participantes = projecao.participantes,
                aoVivo = projecao.aoVivo,
                erro = projecao.erro,
                onEncerrarEntrada = projecao::encerrarEntrada,
                onEncerrarSessao = projecao::encerrarSessao,
                onSair = sair,
            )
        }

        conceitosIniciais == null -> {
            TeclaVoltar { navegacao.fecharTurma() }
            LaunchedEffect(turma.id) { dadosTurma.abrir(turma.id) }
            // Lê o estado sempre (fora do if), para a tela recompor quando o grafo chegar.
            val grafo by dadosTurma.grafo.collectAsState()
            val grafoAtual = grafo
            val aba = dadosTurma.aba
            LaunchedEffect(turma.id, aba) { dadosTurma.carregarAba(aba) }
            TurmaScreen(
                turma = turma,
                // Enquanto o ViewModel ainda guarda o grafo de outra turma, mostra "carregando".
                grafo = if (dadosTurma.turmaId == turma.id) grafoAtual else EstadoGrafo.Carregando,
                onTentarDeNovo = dadosTurma::recarregar,
                onSimular = {
                    dadosTurma.simular(turma.id) { erro ->
                        avisar(erro ?: "Simulação concluída: 25 alunos fictícios responderam")
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
                aba = aba,
                onAba = { dadosTurma.aba = it },
                atividades = dadosTurma.atividades,
                onAbrirSessao = navegacao::abrirProjecao,
                membros = dadosTurma.membros,
                onRecarregarAba = { dadosTurma.carregarAba() },
                onConvidar = { dadosTurma.abrirConvite() },
            )
            dadosTurma.convite?.let { convite ->
                ConviteDialogo(
                    convite = convite,
                    nomeTurma = "${turma.nome} · ${turma.codigo}",
                    onTrocarCodigo = { dadosTurma.abrirConvite(novo = true) },
                    onFechar = dadosTurma::fecharConvite,
                    onTentarDeNovo = { dadosTurma.abrirConvite() },
                )
            }
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
                onGerarMenos = geracao::gerarMenos,
                onVoltar = geracao::voltar,
                onPublicar = { geracao.publicar(turma.id) },
            )
            // Publicada: volta para a Turma, na aba Atividades, com os dados atualizados.
            LaunchedEffect(estado) {
                val publicado = estado
                if (publicado is EstadoGeracao.Publicado) {
                    avisar("Atividade publicada. Abra a sessão em sala na aba Atividades.")
                    geracao.voltar()
                    navegacao.fecharNovaAtividade()
                    navegacao.recarregarTurmas(silencioso = true)
                    dadosTurma.abrir(publicado.turmaId, forcar = true)
                    dadosTurma.aba = TurmaViewModel.ABA_ATIVIDADES
                    dadosTurma.carregarAba(TurmaViewModel.ABA_ATIVIDADES)
                }
            }
        }
    }
}
