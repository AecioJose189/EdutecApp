// Modo simulado (VITE_API_SIMULADA=true): devolve os exemplos de docs/contrato-api-mvp.md sem tocar no
// Supabase. Valores especiais disparam os erros do contrato, para testar todas as telas:
//   convite  XXXXXX                     → convite_invalido
//   token    expirado | encerrada | fora → qr_expirado | sessao_encerrada | nao_matriculado
//   token    deslogado                  → nao_autenticado (sai e volta para o login)
//   token    enviada                    → tentativa com ja_enviada = true
//   token    rapida                     → prazo de 45 s (para ver o cronômetro acabar)
//   token    semprazo                   → prazo_em = null
import { ErroApi, type AcoesAlunoApi, type Acao, type ConceitoAluno, type Tentativa, type Turma } from './tipos'

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

// Começa sem turmas: o caminho da demo é estado vazio → link de convite → turma.
const turmasDoAluno = new Map<string, Turma>(lerGuardado<[string, Turma][]>('sim.turmas', []))
const enviadas = new Set<string>(lerGuardado<string[]>('sim.enviadas', []))

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
      id: 'atividade-1',
      titulo: 'Busca competitiva em jogos',
      turma: { id: TURMA_IA.id, nome: TURMA_IA.nome, codigo: TURMA_IA.codigo },
      questoes: [
        {
          id: 'q1',
          tipo: 'multipla_escolha',
          enunciado: 'Num jogo de soma zero com dois jogadores, se o ganho de MAX é $u$, qual é o ganho de MIN?',
          conceitos: ['Jogos de Soma Zero'],
          alternativas: [
            { id: 'a', texto: '$u$' },
            { id: 'b', texto: '$-u$' },
            { id: 'c', texto: '$1/u$' },
            { id: 'd', texto: 'Depende do estado do jogo' },
          ],
        },
        {
          id: 'q2',
          tipo: 'multipla_escolha',
          enunciado: 'Qual é a complexidade de tempo do Minimax com fator de ramificação $b$ e profundidade $m$?',
          conceitos: ['Algoritmo Minimax'],
          alternativas: [
            { id: 'a', texto: '$O(bm)$' },
            { id: 'b', texto: '$O(b^m)$' },
            { id: 'c', texto: '$O(m^b)$' },
            { id: 'd', texto: '$O(b^{m/2})$' },
          ],
        },
        {
          id: 'q3',
          tipo: 'multipla_escolha',
          enunciado: 'Com a ordenação perfeita dos movimentos, a poda alfa-beta examina cerca de quantos nós?',
          conceitos: ['Poda Alfa-Beta'],
          alternativas: [
            { id: 'a', texto: '$O(b^{m/2})$' },
            { id: 'b', texto: '$O(b^m)$' },
            { id: 'c', texto: '$O(\\log m)$' },
          ],
        },
        {
          id: 'q4',
          tipo: 'multipla_escolha',
          enunciado: 'Por que programas de xadrez cortam a busca e usam uma função de avaliação?',
          conceitos: ['Decisões em Tempo Real'],
          alternativas: [
            { id: 'a', texto: 'Porque a árvore completa é grande demais para o tempo disponível' },
            { id: 'b', texto: 'Porque o xadrez não é um jogo de soma zero' },
            { id: 'c', texto: 'Porque a função de avaliação é sempre exata' },
          ],
        },
        {
          id: 'q5',
          tipo: 'aberta',
          enunciado: 'Explique com suas palavras o que é a busca de quiescência e qual problema ela evita.',
          conceitos: ['Quiescência'],
          alternativas: null,
        },
      ],
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

const espera = (ms: number) => new Promise((r) => setTimeout(r, ms))

export async function chamarSimulada<A extends Acao>(
  acao: A,
  params: AcoesAlunoApi[A]['entrada'],
): Promise<AcoesAlunoApi[A]['saida']> {
  await espera(400)
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
      if (token === 'fora') throw new ErroApi('nao_matriculado', 'Sua conta não faz parte da turma desta atividade.')
      return tentativa(token) as Saida
    }

    case 'enviar_respostas': {
      const id = String(p.tentativa_id)
      if (enviadas.has(id) || id === 'tentativa-enviada') {
        throw new ErroApi('tentativa_ja_enviada', 'Você já enviou as respostas desta atividade.')
      }
      enviadas.add(id)
      guardar('sim.enviadas', [...enviadas])
      // Gabarito da simulação: q1 b, q2 b, q3 a, q4 a. Fica só aqui, como ficaria no servidor.
      const gabarito: Record<string, string> = { q1: 'b', q2: 'b', q3: 'a', q4: 'a' }
      const respostas = p.respostas as { questao_id: string; alternativa_id?: string; texto?: string }[]
      const acertos = respostas.filter((r) => r.alternativa_id && gabarito[r.questao_id] === r.alternativa_id).length
      const abertas = respostas.filter((r) => r.texto?.trim()).length
      return { acertos, total_multipla_escolha: 4, abertas_aguardando_correcao: abertas } as Saida
    }

    case 'meu_grafo':
      if (!turmasDoAluno.has(String(p.turma_id))) {
        throw new ErroApi('nao_matriculado', 'Sua conta não faz parte desta turma.')
      }
      return { conceitos: String(p.turma_id) === TURMA_IA.id ? GRAFO : [] } as Saida
  }
  throw new ErroApi('erro_interno', 'Ação desconhecida.')
}
