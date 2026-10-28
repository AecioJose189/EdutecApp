import { FunctionsFetchError, FunctionsHttpError } from '@supabase/supabase-js'
import { apiSimulada } from './config'
import { chamarSimulada } from './simulada'
import { supabase } from './supabase'
import { ErroApi, type Acao, type AcoesAlunoApi } from './tipos'

/**
 * Único ponto de acesso à Edge Function `aluno-api` (o supabase-js manda o JWT do aluno logado).
 * No modo simulado devolve os exemplos do contrato. Erros sempre viram `ErroApi`.
 */
export async function chamarAlunoApi<A extends Acao>(
  acao: A,
  params: AcoesAlunoApi[A]['entrada'],
): Promise<AcoesAlunoApi[A]['saida']> {
  if (apiSimulada) return chamarSimulada(acao, params)

  const { data, error } = await supabase().functions.invoke('aluno-api', { body: { acao, ...params } })
  if (!error) return data as AcoesAlunoApi[A]['saida']

  if (error instanceof FunctionsHttpError) {
    const corpo = await (error.context as Response).json().catch(() => null)
    if (corpo && typeof corpo.erro === 'string') {
      throw new ErroApi(corpo.erro, typeof corpo.mensagem === 'string' ? corpo.mensagem : '')
    }
    throw new ErroApi('erro_interno', '')
  }
  if (error instanceof FunctionsFetchError || !navigator.onLine) {
    throw new ErroApi('sem_conexao', 'Não foi possível falar com o servidor.')
  }
  throw new ErroApi('erro_interno', '')
}
