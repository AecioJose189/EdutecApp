package com.edutec.app.data

/**
 * Painel "Próximos passos" no topo da turma (testes de novembro: não ficava claro que, depois de
 * publicar, ainda é preciso abrir a sessão em sala). A etapa atual é a primeira ainda não feita.
 */
enum class Passo(val titulo: String) {
    Convidar("Convidar alunos"),
    Criar("Criar atividade"),
    Publicar("Publicar"),
    AbrirSessao("Abrir sessão em sala"),
    VerMapa("Ver o mapa do conhecimento"),
}

enum class SituacaoPasso { Concluido, Atual, Pendente }

data class EtapaPasso(val passo: Passo, val situacao: SituacaoPasso, val detalhe: String? = null)

/**
 * @param membros alunos que entraram na turma (null = ainda carregando: conta como nenhum).
 * @param temRascunho há atividade gerada e não publicada para a turma.
 * @param atividades publicadas (null = ainda carregando).
 * @param temRespostas o mapa já tem respostas de alunos (reais ou da demonstração).
 */
fun proximosPassos(
    membros: Int?,
    temRascunho: Boolean,
    atividades: List<AtividadePublicada>?,
    temRespostas: Boolean,
): List<EtapaPasso> {
    val publicadas = atividades.orEmpty()
    val feito = mapOf(
        Passo.Convidar to ((membros ?: 0) > 0),
        Passo.Criar to (temRascunho || publicadas.isNotEmpty()),
        Passo.Publicar to publicadas.isNotEmpty(),
        Passo.AbrirSessao to publicadas.any { it.sessoes > 0 || it.sessaoAberta != null },
        Passo.VerMapa to temRespostas,
    )
    val atual = Passo.entries.firstOrNull { feito[it] != true }
    return Passo.entries.map { passo ->
        EtapaPasso(
            passo,
            when {
                feito[passo] == true -> SituacaoPasso.Concluido
                passo == atual -> SituacaoPasso.Atual
                else -> SituacaoPasso.Pendente
            },
            detalhe = when (passo) {
                Passo.Convidar -> when (val n = membros ?: 0) {
                    0 -> "ninguém entrou ainda"
                    1 -> "1 aluno entrou"
                    else -> "$n alunos entraram"
                }
                else -> null
            },
        )
    }
}

/** Todas as etapas feitas: o painel some. */
fun List<EtapaPasso>.tudoFeito() = all { it.situacao == SituacaoPasso.Concluido }
