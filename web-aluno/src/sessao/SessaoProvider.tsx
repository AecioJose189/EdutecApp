import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { quandoSessaoExpirar } from '../api/alunoApi'
import { apiSimulada } from '../api/config'
import { supabase } from '../api/supabase'

export interface Usuario {
  nome: string
  email?: string
}

interface Sessao {
  /** `undefined` enquanto ainda não sabemos se há alguém logado. */
  usuario: Usuario | null | undefined
  /** Vai para o Google e, na volta, para `destino` (caminho do site, ex.: `/convite/K7QF2M`). */
  entrar: (destino: string) => Promise<void>
  sair: () => Promise<void>
}

const ContextoSessao = createContext<Sessao | null>(null)

const CHAVE_SIMULADA = 'sim.logado'

function lerSimulado(): Usuario | null {
  try {
    return sessionStorage.getItem(CHAVE_SIMULADA) ? { nome: 'Aluno de teste' } : null
  } catch {
    return null
  }
}

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null | undefined>(apiSimulada ? lerSimulado() : undefined)

  // Servidor recusou o login (401 / nao_autenticado): sai e a RotaProtegida leva para /entrar,
  // lembrando a página atual para voltar depois.
  useEffect(() => {
    // 'local': o token já não vale no servidor, então só limpa o aparelho.
    quandoSessaoExpirar(() => void (apiSimulada ? sair() : supabase().auth.signOut({ scope: 'local' })))
    return () => quandoSessaoExpirar(null)
  })

  useEffect(() => {
    if (apiSimulada) return
    const auth = supabase().auth
    const { data } = auth.onAuthStateChange((_evento, sessao) => {
      const u = sessao?.user
      setUsuario(
        u ? { nome: (u.user_metadata?.full_name as string | undefined) ?? u.email ?? 'Aluno', email: u.email } : null,
      )
    })
    return () => data.subscription.unsubscribe()
  }, [])

  async function entrar(destino: string) {
    if (apiSimulada) {
      try {
        sessionStorage.setItem(CHAVE_SIMULADA, '1')
      } catch {
        // segue sem lembrar
      }
      setUsuario(lerSimulado() ?? { nome: 'Aluno de teste' })
      return
    }
    const { error } = await supabase().auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: window.location.origin + destino },
    })
    if (error) throw error
  }

  async function sair() {
    if (apiSimulada) {
      try {
        sessionStorage.removeItem(CHAVE_SIMULADA)
      } catch {
        // nada
      }
      setUsuario(null)
      return
    }
    await supabase().auth.signOut()
  }

  return <ContextoSessao.Provider value={{ usuario, entrar, sair }}>{children}</ContextoSessao.Provider>
}

export function useSessao(): Sessao {
  const s = useContext(ContextoSessao)
  if (!s) throw new Error('useSessao fora do SessaoProvider')
  return s
}
