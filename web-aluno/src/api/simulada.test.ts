// O modo simulado segue docs/contrato-api-fase2.md à risca: o site é testado contra ele até o servidor estar no ar.
import { beforeEach, describe, expect, it } from 'vitest'
import { chamarSimulada, reiniciarSimulacao, semEsperaNaSimulacao } from './simulada'
import { ErroApi } from './tipos'

semEsperaNaSimulacao()
beforeEach(reiniciarSimulacao)

const entrarNaTurmaIA = () => chamarSimulada('aceitar_convite', { codigo: 'K7QF2M' })

describe('enviar_respostas (fase 2)', () => {
  it('devolve quais questões errou, na ordem, sem a alternativa correta', async () => {
    const t = await chamarSimulada('entrar_sessao', { token: 'teste' })
    const r = await chamarSimulada('enviar_respostas', {
      tentativa_id: t.tentativa_id,
      respostas: [
        { questao_id: 'q1', alternativa_id: 'b' },
        { questao_id: 'q2', alternativa_id: 'a' },
        { questao_id: 'q5', texto: 'texto' },
      ],
    })
    expect(r.acertos).toBe(1)
    expect(r.total_multipla_escolha).toBe(4)
    expect(r.abertas_aguardando_correcao).toBe(1)
    // q3 e q4 em branco contam como erradas; a aberta vem null.
    expect(r.questoes).toEqual([
      { questao_id: 'q1', acertou: true },
      { questao_id: 'q2', acertou: false },
      { questao_id: 'q3', acertou: false },
      { questao_id: 'q4', acertou: false },
      { questao_id: 'q5', acertou: null },
    ])
    expect(JSON.stringify(r)).not.toMatch(/correta|feedback|rubrica/)
  })
})

describe('minhas_atividades', () => {
  it('lista só as das turmas do aluno, da mais recente para a mais antiga', async () => {
    expect((await chamarSimulada('minhas_atividades', {})).atividades).toEqual([])
    await entrarNaTurmaIA()
    const t = await chamarSimulada('entrar_sessao', { token: 'teste' })
    await chamarSimulada('enviar_respostas', { tentativa_id: t.tentativa_id, respostas: [] })

    const { atividades } = await chamarSimulada('minhas_atividades', {})
    expect(atividades.map((a) => a.atividade.numero)).toEqual([3, 2, 1])
    expect(atividades.map((a) => a.gabarito_liberado)).toEqual([false, false, true])
    expect(atividades[1]).toMatchObject({
      tentativa_id: 'exemplo-sem-gabarito',
      turma: { nome: 'Inteligência Artificial', codigo: 'Turma A' },
      acertos: 1,
      total_multipla_escolha: 2,
    })
  })

  it('filtra pela turma', async () => {
    await entrarNaTurmaIA()
    expect((await chamarSimulada('minhas_atividades', { turma_id: 'outra' })).atividades).toEqual([])
  })
})

describe('revisao_tentativa', () => {
  it('sem o gabarito, correta, feedback e rubrica não vêm (nem como null)', async () => {
    const r = await chamarSimulada('revisao_tentativa', { tentativa_id: 'exemplo-sem-gabarito' })
    expect(r.gabarito_liberado).toBe(false)
    for (const q of r.questoes) {
      expect(q).not.toHaveProperty('rubrica')
      expect(typeof q.acertou === 'boolean' || q.acertou === null).toBe(true)
      if (q.tipo === 'multipla_escolha') {
        for (const a of q.alternativas) expect(Object.keys(a).sort()).toEqual(['id', 'texto'])
      }
    }
    // Em branco: minha_resposta vazia e acertou false.
    expect(r.questoes[1]).toMatchObject({ questao_id: 'q4', minha_resposta: {}, acertou: false })
  })

  it('com o gabarito, traz a correta, o feedback de cada alternativa e a rubrica', async () => {
    const r = await chamarSimulada('revisao_tentativa', { tentativa_id: 'exemplo-liberado' })
    expect(r.gabarito_liberado).toBe(true)
    expect(r.atividade).toEqual({ id: 'atividade-1', numero: 1, titulo: 'Jogos de soma zero e Minimax' })
    const [q1, q2, aberta] = r.questoes
    // Ordem que o aluno viu (embaralhada); o rótulo vem da posição, o id continua o original.
    expect(q1.tipo === 'multipla_escolha' && q1.alternativas.map((a) => a.id)).toEqual(['c', 'a', 'b', 'd'])
    expect(q1.tipo === 'multipla_escolha' && q1.alternativas.every((a) => a.feedback && 'correta' in a)).toBe(true)
    expect(q2.acertou).toBe(false)
    expect(aberta).toMatchObject({ tipo: 'aberta', acertou: null })
    expect(aberta.tipo === 'aberta' && aberta.rubrica).toBeTruthy()
  })

  it('tentativa de outra pessoa → nao_matriculado', async () => {
    await expect(chamarSimulada('revisao_tentativa', { tentativa_id: 'outra-pessoa' })).rejects.toBeInstanceOf(ErroApi)
    await expect(chamarSimulada('revisao_tentativa', { tentativa_id: 'outra-pessoa' })).rejects.toHaveProperty(
      'codigo',
      'nao_matriculado',
    )
  })
})
