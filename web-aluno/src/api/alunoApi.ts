import { FunctionsFetchError, FunctionsHttpError } from '@supabase/supabase-js'
import { apiSimulada } from './config'
import { chamarSimulada } from './simulada'
import { supabase } from './supabase'
import { ErroApi, type Acao, type AcoesAlunoApi } from './tipos'

// Avisado quando o servidor diz que o login não vale mais (o SessaoProvider manda para /entrar).
let aoExpirarSessao: (() => void) | null = null
export function quandoSessaoExpirar(f: (() => void) | null) {
  aoExpirarSessao = f
}

/**
 * Único ponto de acesso à Edge Function `aluno-api` (o supabase-js manda o JWT do aluno logado).
 * No modo simulado devolve os exemplos do contrato. Erros sempre viram `ErroApi`.
 */
export async function chamarAlunoApi<A extends Acao>(
  acao: A,
  params: AcoesAlunoApi[A]['entrada'],
): Promise<AcoesAlunoApi[A]['saida']> {
  try {
    if (apiSimulada) return await chamarSimulada(acao, params)
    try {
      return await chamarReal(acao, params)
    } catch (e) {
      // Token vencido: tenta renovar o login uma vez antes de mandar o aluno entrar de novo.
      if (!(e instanceof ErroApi && e.codigo === 'nao_autenticado')) throw e
      const { error } = await supabase().auth.refreshSession()
      if (error) throw e
      return await chamarReal(acao, params)
    }
  } catch (e) {
    if (e instanceof ErroApi && e.codigo === 'nao_autenticado') aoExpirarSessao?.()
    throw e
  }
}

async function chamarReal<A extends Acao>(
  acao: A,
  params: AcoesAlunoApi[A]['entrada'],
): Promise<AcoesAlunoApi[A]['saida']> {
  const { data, error } = await supabase().functions.invoke('aluno-api', { body: { acao, ...params } })
  if (!error) return data as AcoesAlunoApi[A]['saida']

  if (error instanceof FunctionsHttpError) {
    const resposta = error.context as Response
    const corpo = await resposta.json().catch(() => null)
    if (corpo && typeof corpo.erro === 'string') {
      throw new ErroApi(corpo.erro, typeof corpo.mensagem === 'string' ? corpo.mensagem : '')
    }
    // Token ausente ou vencido é barrado pela plataforma (verify_jwt), sem o JSON do contrato.
    if (resposta.status === 401) throw new ErroApi('nao_autenticado', '')
    throw new ErroApi('erro_interno', '')
  }
  if (error instanceof FunctionsFetchError || !navigator.onLine) {
    throw new ErroApi('sem_conexao', 'Não foi possível falar com o servidor.')
  }
  throw new ErroApi('erro_interno', '')
}
