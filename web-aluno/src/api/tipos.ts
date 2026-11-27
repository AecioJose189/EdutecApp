// Espelho de docs/contrato-api-mvp.md e docs/contrato-api-fase2.md. Campos novos podem aparecer; renomear ou remover, não.

export interface Turma {
  id: string
  nome: string
  codigo: string
  professor_nome?: string
}

export interface Alternativa {
  id: string
  texto: string
}

export interface Questao {
  id: string
  tipo: 'multipla_escolha' | 'aberta'
  enunciado: string
  conceitos: string[]
  alternativas: Alternativa[] | null
}

export interface Atividade {
  id: string
  titulo: string
  turma: Turma
  questoes: Questao[]
}

export interface Tentativa {
  tentativa_id: string
  iniciada_em: string
  prazo_em: string | null
  ja_enviada: boolean
  atividade: Atividade
}

export type Resposta = { questao_id: string; alternativa_id: string } | { questao_id: string; texto: string }

/** Uma questão no resultado do envio. `acertou: null` = aberta, aguardando correção. */
export interface QuestaoCorrigida {
  questao_id: string
  acertou: boolean | null
}

export interface ResultadoEnvio {
  acertos: number
  total_multipla_escolha: number
  abertas_aguardando_correcao: number
  /** Fase 2 (docs/contrato-api-fase2.md), na ordem das questões. Opcional: o servidor do MVP não manda. */
  questoes?: QuestaoCorrigida[]
}

// ---------- Fase 2: revisão do aluno (docs/contrato-api-fase2.md) ----------

/** `numero` = ordem da atividade na turma (1, 2, 3…), a mesma que o professor vê. */
export interface AtividadeNumerada {
  id: string
  numero: number
  titulo: string
}

/** Item de `minhas_atividades`: uma tentativa já enviada. */
export interface AtividadeFeita {
  tentativa_id: string
  atividade: AtividadeNumerada
  turma: Turma
  enviada_em: string
  acertos: number
  total_multipla_escolha: number
  gabarito_liberado: boolean
}

/**
 * Sem o gabarito liberado vem só `{ id, texto }`: `correta` e `feedback` não existem (não vêm como null).
 * A lista vem na ordem que o aluno viu: rotular A, B, C… pela posição, nunca pelo id.
 */
export interface AlternativaRevisao {
  id: string
  texto: string
  correta?: boolean
  feedback?: string
}

/** `{}` quando o aluno deixou em branco. */
export type MinhaResposta = { alternativa_id: string } | { texto: string } | Record<string, never>

interface QuestaoRevisaoBase {
  questao_id: string
  enunciado: string
  conceitos: string[]
  minha_resposta: MinhaResposta
}

export interface QuestaoRevisaoMultipla extends QuestaoRevisaoBase {
  tipo: 'multipla_escolha'
  acertou: boolean
  alternativas: AlternativaRevisao[]
}

export interface QuestaoRevisaoAberta extends QuestaoRevisaoBase {
  tipo: 'aberta'
  acertou: null
  /** Só com o gabarito liberado. */
  rubrica?: string
}

export type QuestaoRevisao = QuestaoRevisaoMultipla | QuestaoRevisaoAberta

export interface RevisaoTentativa {
  atividade: AtividadeNumerada
  gabarito_liberado: boolean
  acertos: number
  total_multipla_escolha: number
  questoes: QuestaoRevisao[]
}

export type EstadoConceito = 'consolidado' | 'em_desenvolvimento' | 'revisar' | 'sem_dados'

export interface ConceitoAluno {
  nome: string
  prerequisitos: string[]
  dominio: number | null
  estado: EstadoConceito
  n_respostas: number
}

/** Ações da Edge Function `aluno-api`: entrada → saída. */
export interface AcoesAlunoApi {
  minhas_turmas: { entrada: Record<string, never>; saida: { turmas: Turma[] } }
  aceitar_convite: { entrada: { codigo: string }; saida: { turma: Turma; ja_era_membro: boolean } }
  /**
   * `entrar_na_turma: true` (fase 2, PROVISÓRIO até a função do Júlio estar no ar): quem leu o QR sem estar na
   * turma entra nela e já recebe a tentativa. Sem o campo, o servidor responde `nao_matriculado` com `turma`.
   */
  entrar_sessao: { entrada: { token: string; entrar_na_turma?: boolean }; saida: Tentativa }
  enviar_respostas: { entrada: { tentativa_id: string; respostas: Resposta[] }; saida: ResultadoEnvio }
  meu_grafo: { entrada: { turma_id: string }; saida: { conceitos: ConceitoAluno[] } }
  /** Fase 2. Sem `turma_id`, todas as turmas do aluno; da mais recente para a mais antiga. */
  minhas_atividades: { entrada: { turma_id?: string }; saida: { atividades: AtividadeFeita[] } }
  /** Fase 2. Só tentativa enviada do próprio aluno; qualquer outra → `nao_matriculado`. */
  revisao_tentativa: { entrada: { tentativa_id: string }; saida: RevisaoTentativa }
}

export type Acao = keyof AcoesAlunoApi

export type CodigoErro =
  | 'nao_autenticado'
  | 'convite_invalido'
  | 'qr_expirado'
  | 'sessao_encerrada'
  | 'nao_matriculado'
  | 'tentativa_ja_enviada'
  | 'tempo_esgotado'
  | 'erro_interno'
  | 'sem_conexao'

/** Erro vindo do servidor (`{ erro, mensagem, ...detalhes }`) ou da rede. */
export class ErroApi extends Error {
  constructor(
    public codigo: CodigoErro | string,
    mensagem: string,
    /** Campos extras do corpo do erro (ex.: `turma` no `nao_matriculado` de `entrar_sessao`). */
    public detalhes: Record<string, unknown> = {},
  ) {
    super(mensagem)
  }
}
