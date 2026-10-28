// Espelho de docs/contrato-api-mvp.md. Campos novos podem aparecer; renomear ou remover, não.

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

export interface ResultadoEnvio {
  acertos: number
  total_multipla_escolha: number
  abertas_aguardando_correcao: number
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
  entrar_sessao: { entrada: { token: string }; saida: Tentativa }
  enviar_respostas: { entrada: { tentativa_id: string; respostas: Resposta[] }; saida: ResultadoEnvio }
  meu_grafo: { entrada: { turma_id: string }; saida: { conceitos: ConceitoAluno[] } }
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

/** Erro vindo do servidor (`{ erro, mensagem }`) ou da rede. */
export class ErroApi extends Error {
  constructor(
    public codigo: CodigoErro | string,
    mensagem: string,
  ) {
    super(mensagem)
  }
}
