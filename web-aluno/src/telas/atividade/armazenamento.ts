// Guardado só no próprio celular, para não perder nada ao recarregar a página no meio da atividade.
// As respostas são apagadas depois do envio; fica só o resultado ("acertou X de Y").
import type { ResultadoEnvio, Tentativa } from '../../api/tipos'

export type RespostaLocal = { alternativa_id: string } | { texto: string }
export type RespostasLocais = Record<string, RespostaLocal>

function ler<T>(armazem: Storage, chave: string): T | null {
  try {
    const bruto = armazem.getItem(chave)
    return bruto ? (JSON.parse(bruto) as T) : null
  } catch {
    return null
  }
}
function gravar(armazem: Storage, chave: string, valor: unknown) {
  try {
    if (valor === null) armazem.removeItem(chave)
    else armazem.setItem(chave, JSON.stringify(valor))
  } catch {
    // sem armazenamento (modo privado etc.): segue só na memória
  }
}

// O token do QR muda a cada ~20 s; recarregar a página não deve dar "QR expirado".
export const tentativaDoToken = (token: string) => ler<Tentativa>(sessionStorage, `lumini.tentativa.${token}`)
export const guardarTentativa = (token: string, t: Tentativa) => gravar(sessionStorage, `lumini.tentativa.${token}`, t)

export const respostasSalvas = (tentativaId: string) =>
  ler<RespostasLocais>(localStorage, `lumini.respostas.${tentativaId}`) ?? {}
export const salvarRespostas = (tentativaId: string, r: RespostasLocais | null) =>
  gravar(localStorage, `lumini.respostas.${tentativaId}`, r)

export const resultadoSalvo = (tentativaId: string) =>
  ler<ResultadoEnvio>(localStorage, `lumini.resultado.${tentativaId}`)
export const salvarResultado = (tentativaId: string, r: ResultadoEnvio) =>
  gravar(localStorage, `lumini.resultado.${tentativaId}`, r)

export function respondida(r: RespostaLocal | undefined): boolean {
  if (!r) return false
  return 'alternativa_id' in r ? true : r.texto.trim().length > 0
}
