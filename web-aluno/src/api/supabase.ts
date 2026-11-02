import { createClient, type SupabaseClient } from '@supabase/supabase-js'
import { apiSimulada, supabaseChavePublica, supabaseUrl } from './config'

let cliente: SupabaseClient | null = null

/** Cliente único do Supabase (só URL e chave pública). Não existe no modo simulado. */
export function supabase(): SupabaseClient {
  if (apiSimulada) throw new Error('Supabase não é usado no modo simulado')
  if (!cliente) {
    if (!supabaseUrl || !supabaseChavePublica) {
      throw new Error('Faltam VITE_SUPABASE_URL e VITE_SUPABASE_CHAVE_PUBLICA no .env.local')
    }
    cliente = createClient(supabaseUrl, supabaseChavePublica, {
      auth: { flowType: 'pkce', persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
    })
  }
  return cliente
}
