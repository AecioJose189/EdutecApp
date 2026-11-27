// Modo simulado (VITE_API_SIMULADA=true): devolve os exemplos de docs/contrato-api-mvp.md (e os de
// docs/contrato-api-fase2.md) sem tocar no Supabase. Valores especiais disparam os erros do contrato:
//   convite  XXXXXX                     → convite_invalido
//   token    expirado | encerrada        → qr_expirado | sessao_encerrada
//   token    fora                        → nao_matriculado com a turma; com entrar_na_turma: true, entra e abre
//   token    fora-antigo                 → nao_matriculado sem a turma (como o servidor do MVP)
//   token    deslogado                  → nao_autenticado (sai e volta para o login)
//   token    enviada                    → tentativa com ja_enviada = true
//   token    rapida                     → prazo de 45 s (para ver o cronômetro acabar)
//   token    semprazo                   → prazo_em = null
// Fase 2, revisão do aluno:
//   tentativa exemplo-liberado          → revisão com o gabarito (correta, feedback, rubrica)
//   tentativa exemplo-sem-gabarito      → revisão sem o gabarito (os campos não vêm)
//   tentativa outra-pessoa (ou outra)   → nao_matriculado
//   enviadas pelo QR nesta aba          → aparecem em minhas_atividades, sem o gabarito (sessão aberta)
import {
  ErroApi,
  type AcoesAlunoApi,
  type Acao,
  type AtividadeFeita,
  type ConceitoAluno,
  type Questao,
  type QuestaoRevisao,
  type RevisaoTentativa,
  type Tentativa,
  type Turma,
} from './tipos'

const TURMA_IA: Turma = {
  id: '00000000-0000-4000-8000-000000000001',
  nome: 'Inteligência Artificial',
  codigo: 'Turma A',
  professor_nome: 'Júlio Lucena',
}

// Guardado no sessionStorage para que recarregar a página se comporte como o servidor (idempotente).
function lerGuardado<T>(chave: string, padrao: T): T {
  try {
    const bruto = sessionStorage.getItem(chave)
    return bruto ? (JSON.parse(bruto) as T) : padrao
  } catch {
    return padrao
  }
}
function guardar(chave: string, valor: unknown) {
  try {
    sessionStorage.setItem(chave, JSON.stringify(valor))
  } catch {
    // sem armazenamento: a simulação só não lembra entre recarregamentos
  }
}

/** Resposta guardada do aluno: questao_id → alternativa_id ou texto. */
type RespostasGuardadas = Record<string, { alternativa_id?: string; texto?: string }>

// Começa sem turmas: o caminho da demo é estado vazio → link de convite → turma.
const turmasDoAluno = new Map<string, Turma>(lerGuardado<[string, Turma][]>('sim.turmas', []))
const enviadas = new Set<string>(lerGuardado<string[]>('sim.enviadas', []))
// Respostas e hora do envio das tentativas feitas pelo QR nesta aba (para minhas_atividades e a revisão).
const respostasEnviadas: Record<string, { enviada_em: string; respostas: RespostasGuardadas }> = lerGuardado(
  'sim.respostas',
  {},
)

/** Só para os testes: volta ao estado inicial (sem turmas e sem envios). */
export function reiniciarSimulacao() {
  turmasDoAluno.clear()
  enviadas.clear()
  for (const k of Object.keys(respostasEnviadas)) delete respostasEnviadas[k]
}

// ---------- Banco de questões, com o gabarito (fica só aqui, como ficaria no servidor) ----------

interface AlternativaGabarito {
  id: string
  texto: string
  correta: boolean
  feedback: string
}
interface QuestaoGabarito {
  id: string
  tipo: 'multipla_escolha' | 'aberta'
  enunciado: string
  conceitos: string[]
  alternativas: AlternativaGabarito[] | null
  rubrica?: string
}

const BANCO: Record<string, QuestaoGabarito> = {
  q1: {
    id: 'q1',
    tipo: 'multipla_escolha',
    enunciado: 'Num jogo de soma zero com dois jogadores, se o ganho de MAX é $u$, qual é o ganho de MIN?',
    conceitos: ['Jogos de Soma Zero'],
    alternativas: [
      { id: 'a', texto: '$u$', correta: false, feedback: 'Se os dois ganhassem $u$, a soma seria $2u$, e não zero.' },
      { id: 'b', texto: '$-u$', correta: true, feedback: 'Isso: o que um ganha o outro perde, e a soma dá zero.' },
      { id: 'c', texto: '$1/u$', correta: false, feedback: 'Soma zero fala da soma dos ganhos, não do produto.' },
      {
        id: 'd',
        texto: 'Depende do estado do jogo',
        correta: false,
        feedback: 'O valor muda com o estado, mas a relação entre os dois ganhos é sempre a mesma.',
      },
    ],
  },
  q2: {
    id: 'q2',
    tipo: 'multipla_escolha',
    enunciado: 'Qual é a complexidade de tempo do Minimax com fator de ramificação $b$ e profundidade $m$?',
    conceitos: ['Algoritmo Minimax'],
    alternativas: [
      {
        id: 'a',
        texto: '$O(bm)$',
        correta: false,
        feedback: '$O(bm)$ é o espaço da busca em profundidade, não o tempo.',
      },
      {
        id: 'b',
        texto: '$O(b^m)$',
        correta: true,
        feedback: 'Certo: o Minimax visita a árvore toda, com $b^m$ folhas.',
      },
      { id: 'c', texto: '$O(m^b)$', correta: false, feedback: 'A base e o expoente estão trocados.' },
      {
        id: 'd',
        texto: '$O(b^{m/2})$',
        correta: false,
        feedback: 'Esse é o melhor caso da poda alfa-beta, não do Minimax puro.',
      },
    ],
  },
  q3: {
    id: 'q3',
    tipo: 'multipla_escolha',
    enunciado: 'Com a ordenação perfeita dos movimentos, a poda alfa-beta examina cerca de quantos nós?',
    conceitos: ['Poda Alfa-Beta'],
    alternativas: [
      {
        id: 'a',
        texto: '$O(b^{m/2})$',
        correta: true,
        feedback: 'Isso: com a poda, dá para ir ao dobro da profundidade.',
      },
      { id: 'b', texto: '$O(b^m)$', correta: false, feedback: 'Esse é o caso sem poda (ou com a pior ordenação).' },
      {
        id: 'c',
        texto: '$O(\\log m)$',
        correta: false,
        feedback: 'A poda corta ramos, mas a busca continua exponencial.',
      },
    ],
  },
  q4: {
    id: 'q4',
    tipo: 'multipla_escolha',
    enunciado: 'Por que programas de xadrez cortam a busca e usam uma função de avaliação?',
    conceitos: ['Decisões em Tempo Real'],
    alternativas: [
      {
        id: 'a',
        texto: 'Porque a árvore completa é grande demais para o tempo disponível',
        correta: true,
        feedback: 'Certo: sem o corte, nenhuma jogada sairia a tempo.',
      },
      {
        id: 'b',
        texto: 'Porque o xadrez não é um jogo de soma zero',
        correta: false,
        feedback: 'O xadrez é, sim, um jogo de soma zero.',
      },
      {
        id: 'c',
        texto: 'Porque a função de avaliação é sempre exata',
        correta: false,
        feedback: 'Ela é só uma estimativa; por isso o corte pode errar.',
      },
    ],
  },
  q5: {
    id: 'q5',
    tipo: 'aberta',
    enunciado: 'Explique com suas palavras o que é a busca de quiescência e qual problema ela evita.',
    conceitos: ['Quiescência'],
    alternativas: null,
    rubrica:
      'Espera-se que a resposta diga que a busca continua além do limite só nas posições "agitadas" ' +
      '(capturas, xeques) e que isso evita o efeito horizonte: avaliar a posição no meio de uma troca.',
  },
}

/** Questão como o aluno vê ao responder: sem `correta`, `feedback` nem `rubrica`. */
function semGabarito(q: QuestaoGabarito): Questao {
  return {
    id: q.id,
    tipo: q.tipo,
    enunciado: q.enunciado,
    conceitos: q.conceitos,
    alternativas: q.alternativas && q.alternativas.map(({ id, texto }) => ({ id, texto })),
  }
}

/** null = aberta (aguardando correção); múltipla escolha em branco = false. */
function acertou(q: QuestaoGabarito, r: RespostasGuardadas[string] | undefined): boolean | null {
  if (q.tipo === 'aberta') return null
  return !!r?.alternativa_id && !!q.alternativas?.some((a) => a.id === r.alternativa_id && a.correta)
}

// ---------- Atividades ----------

interface AtividadeSimulada {
  id: string
  numero: number
  titulo: string
  /** Ids do banco; `ordem` = ordem das alternativas que o aluno viu (o rótulo A, B… vem da posição). */
  questoes: { id: string; ordem?: string[] }[]
}

/** A atividade do QR (/s/<token>): a 3ª da turma. */
const ATIVIDADE_DA_SALA: AtividadeSimulada = {
  id: 'atividade-3',
  numero: 3,
  titulo: 'Busca competitiva em jogos',
  questoes: ['q1', 'q2', 'q3', 'q4', 'q5'].map((id) => ({ id })),
}

interface TentativaEnviada {
  atividade: AtividadeSimulada
  enviada_em: string
  liberado: boolean
  respostas: RespostasGuardadas
}

/**
 * Duas tentativas já enviadas na Inteligência Artificial, uma em cada caso do contrato. Aparecem em
 * "Minhas atividades" depois de entrar na turma (/convite/K7QF2M); a revisão abre direto pelo id.
 */
const EXEMPLOS: Record<string, TentativaEnviada> = {
  'exemplo-liberado': {
    atividade: {
      id: 'atividade-1',
      numero: 1,
      titulo: 'Jogos de soma zero e Minimax',
      // Alternativas da q1 embaralhadas (fase 2b): o aluno viu c, a, b, d nas posições A, B, C, D.
      questoes: [{ id: 'q1', ordem: ['c', 'a', 'b', 'd'] }, { id: 'q2' }, { id: 'q5' }],
    },
    enviada_em: '2026-11-04T21:40:00.000Z',
    liberado: true,
    respostas: {
      q1: { alternativa_id: 'b' },
      q2: { alternativa_id: 'a' },
      q5: { texto: 'É quando o programa continua olhando as capturas para não parar no meio de uma troca.' },
    },
  },
  'exemplo-sem-gabarito': {
    atividade: {
      id: 'atividade-2',
      numero: 2,
      titulo: 'Poda alfa-beta e decisões em tempo real',
      questoes: [{ id: 'q3' }, { id: 'q4' }, { id: 'q5' }],
    },
    enviada_em: '2026-11-11T21:35:00.000Z',
    liberado: false,
    // q4 em branco (conta como errada); q5 aberta.
    respostas: { q3: { alternativa_id: 'a' }, q5: { texto: 'Busca extra nas posições instáveis.' } },
  },
}

/** Tentativas enviadas, de exemplo ou feitas nesta aba, como o servidor as guardaria. */
function tentativasEnviadas(): (TentativaEnviada & { tentativa_id: string; turma: Turma })[] {
  const lista = Object.entries(EXEMPLOS).map(([tentativa_id, e]) => ({ tentativa_id, turma: TURMA_IA, ...e }))
  for (const [tentativa_id, e] of Object.entries(respostasEnviadas)) {
    // Feitas pelo QR nesta aba: a sessão continua aberta, então o gabarito ainda não foi liberado.
    lista.push({ tentativa_id, turma: TURMA_IA, atividade: ATIVIDADE_DA_SALA, liberado: false, ...e })
  }
  return lista
}

function placar(atividade: AtividadeSimulada, respostas: RespostasGuardadas) {
  const multiplas = atividade.questoes.map((q) => BANCO[q.id]).filter((q) => q.tipo === 'multipla_escolha')
  return {
    acertos: multiplas.filter((q) => acertou(q, respostas[q.id])).length,
    total_multipla_escolha: multiplas.length,
  }
}

function revisao(tentativaId: string): RevisaoTentativa {
  const t = tentativasEnviadas().find((x) => x.tentativa_id === tentativaId)
  if (!t) throw new ErroApi('nao_matriculado', 'Esta tentativa não é sua ou ainda não foi enviada.')
  const { atividade, liberado, respostas } = t
  const questoes = atividade.questoes.map(({ id, ordem }): QuestaoRevisao => {
    const q = BANCO[id]
    const r = respostas[id]
    const comum = {
      questao_id: q.id,
      enunciado: q.enunciado,
      conceitos: q.conceitos,
      minha_resposta: r?.alternativa_id
        ? { alternativa_id: r.alternativa_id }
        : r?.texto
          ? { texto: r.texto }
          : ({} as Record<string, never>),
    }
    if (q.tipo === 'aberta') {
      // Sem o gabarito o campo some (não vem null).
      return { ...comum, tipo: 'aberta', acertou: null, ...(liberado && q.rubrica ? { rubrica: q.rubrica } : {}) }
    }
    const alternativas = (ordem ?? q.alternativas!.map((a) => a.id)).map((aid) => {
      const a = q.alternativas!.find((x) => x.id === aid)!
      return liberado
        ? { id: a.id, texto: a.texto, correta: a.correta, feedback: a.feedback }
        : { id: a.id, texto: a.texto }
    })
    return { ...comum, tipo: 'multipla_escolha', acertou: acertou(q, r) === true, alternativas }
  })
  return {
    atividade: { id: atividade.id, numero: atividade.numero, titulo: atividade.titulo },
    gabarito_liberado: liberado,
    ...placar(atividade, respostas),
    questoes,
  }
}

function tentativa(token: string): Tentativa {
  const agora = Date.now()
  const prazo =
    token === 'semprazo' ? null : new Date(agora + (token === 'rapida' ? 45_000 : 30 * 60_000)).toISOString()
  const id = `tentativa-${token}`
  return {
    tentativa_id: id,
    iniciada_em: new Date(agora).toISOString(),
    prazo_em: prazo,
    ja_enviada: token === 'enviada' || enviadas.has(id),
    atividade: {
      id: ATIVIDADE_DA_SALA.id,
      titulo: ATIVIDADE_DA_SALA.titulo,
      turma: { id: TURMA_IA.id, nome: TURMA_IA.nome, codigo: TURMA_IA.codigo },
      questoes: ATIVIDADE_DA_SALA.questoes.map((q) => semGabarito(BANCO[q.id])),
    },
  }
}

const GRAFO: ConceitoAluno[] = [
  { nome: 'Jogos de Soma Zero', prerequisitos: [], dominio: 0.91, estado: 'consolidado', n_respostas: 6 },
  {
    nome: 'Algoritmo Minimax',
    prerequisitos: ['Jogos de Soma Zero'],
    dominio: 0.82,
    estado: 'consolidado',
    n_respostas: 4,
  },
  {
    nome: 'Poda Alfa-Beta',
    prerequisitos: ['Algoritmo Minimax'],
    dominio: 0.64,
    estado: 'em_desenvolvimento',
    n_respostas: 3,
  },
  {
    nome: 'Decisões em Tempo Real',
    prerequisitos: ['Algoritmo Minimax'],
    dominio: 0.31,
    estado: 'revisar',
    n_respostas: 3,
  },
  {
    nome: 'Quiescência',
    prerequisitos: ['Decisões em Tempo Real'],
    dominio: null,
    estado: 'sem_dados',
    n_respostas: 0,
  },
]

let esperaMs = 400
/** Só para os testes: sem a espera que imita a rede. */
export function semEsperaNaSimulacao() {
  esperaMs = 0
}
const espera = () => new Promise((r) => setTimeout(r, esperaMs))

export async function chamarSimulada<A extends Acao>(
  acao: A,
  params: AcoesAlunoApi[A]['entrada'],
): Promise<AcoesAlunoApi[A]['saida']> {
  await espera()
  const p = params as Record<string, unknown>
  type Saida = AcoesAlunoApi[A]['saida']

  switch (acao) {
    case 'minhas_turmas':
      return { turmas: [...turmasDoAluno.values()] } as Saida

    case 'aceitar_convite': {
      const codigo = String(p.codigo).toUpperCase()
      if (codigo === 'XXXXXX')
        throw new ErroApi('convite_invalido', 'Este link de convite não existe ou foi desativado.')
      const turma: Turma =
        codigo === 'K7QF2M'
          ? TURMA_IA
          : { id: `turma-${codigo}`, nome: 'Estruturas de Dados', codigo: 'Turma B', professor_nome: 'Júlio Lucena' }
      const ja = turmasDoAluno.has(turma.id)
      turmasDoAluno.set(turma.id, turma)
      guardar('sim.turmas', [...turmasDoAluno])
      return { turma, ja_era_membro: ja } as Saida
    }

    case 'entrar_sessao': {
      const token = String(p.token)
      // Só na primeira vez: depois de entrar de novo, a atividade abre (como no servidor de verdade).
      if (token === 'deslogado' && !lerGuardado('sim.deslogou', false)) {
        guardar('sim.deslogou', true)
        throw new ErroApi('nao_autenticado', 'Sua sessão expirou. Entre com o Google de novo.')
      }
      if (token === 'expirado') throw new ErroApi('qr_expirado', 'Este código QR já expirou.')
      if (token === 'encerrada') throw new ErroApi('sessao_encerrada', 'O professor já encerrou esta atividade.')
      if (token === 'fora-antigo') {
        throw new ErroApi('nao_matriculado', 'Sua conta não faz parte da turma desta atividade.')
      }
      if (token === 'fora' && !turmasDoAluno.has(TURMA_IA.id)) {
        if (!p.entrar_na_turma) {
          throw new ErroApi('nao_matriculado', 'Sua conta não faz parte da turma desta atividade.', {
            turma: { id: TURMA_IA.id, nome: TURMA_IA.nome, codigo: TURMA_IA.codigo },
          })
        }
        turmasDoAluno.set(TURMA_IA.id, TURMA_IA)
        guardar('sim.turmas', [...turmasDoAluno])
      }
      return tentativa(token) as Saida
    }

    case 'enviar_respostas': {
      const id = String(p.tentativa_id)
      if (enviadas.has(id) || id === 'tentativa-enviada') {
        throw new ErroApi('tentativa_ja_enviada', 'Você já enviou as respostas desta atividade.')
      }
      const respostas: RespostasGuardadas = {}
      for (const r of p.respostas as { questao_id: string; alternativa_id?: string; texto?: string }[]) {
        respostas[r.questao_id] = r.alternativa_id ? { alternativa_id: r.alternativa_id } : { texto: r.texto }
      }
      enviadas.add(id)
      guardar('sim.enviadas', [...enviadas])
      respostasEnviadas[id] = { enviada_em: new Date().toISOString(), respostas }
      guardar('sim.respostas', respostasEnviadas)
      const daSala = ATIVIDADE_DA_SALA.questoes.map((q) => BANCO[q.id])
      return {
        ...placar(ATIVIDADE_DA_SALA, respostas),
        abertas_aguardando_correcao: daSala.filter((q) => q.tipo === 'aberta' && respostas[q.id]?.texto?.trim()).length,
        // Fase 2: na ordem das questões, sem a alternativa correta (colegas ainda podem estar respondendo).
        questoes: daSala.map((q) => ({ questao_id: q.id, acertou: acertou(q, respostas[q.id]) })),
      } as Saida
    }

    case 'meu_grafo':
      if (!turmasDoAluno.has(String(p.turma_id))) {
        throw new ErroApi('nao_matriculado', 'Sua conta não faz parte desta turma.')
      }
      return { conceitos: String(p.turma_id) === TURMA_IA.id ? GRAFO : [] } as Saida

    case 'minhas_atividades': {
      const turmaId = p.turma_id === undefined ? null : String(p.turma_id)
      const atividades = tentativasEnviadas()
        .filter((t) => turmasDoAluno.has(t.turma.id) && (turmaId === null || t.turma.id === turmaId))
        .map(
          (t): AtividadeFeita => ({
            tentativa_id: t.tentativa_id,
            atividade: { id: t.atividade.id, numero: t.atividade.numero, titulo: t.atividade.titulo },
            turma: { id: t.turma.id, nome: t.turma.nome, codigo: t.turma.codigo },
            enviada_em: t.enviada_em,
            ...placar(t.atividade, t.respostas),
            gabarito_liberado: t.liberado,
          }),
        )
        .sort((a, b) => b.enviada_em.localeCompare(a.enviada_em))
      return { atividades } as Saida
    }

    case 'revisao_tentativa':
      return revisao(String(p.tentativa_id)) as Saida
  }
  throw new ErroApi('erro_interno', 'Ação desconhecida.')
}
