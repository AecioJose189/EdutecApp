// Usuário do pedido (Supabase Auth) e clientes do Supabase para as Edge Functions.
//
// As funções rodam com verify_jwt = true: a plataforma só deixa passar pedidos com um JWT válido
// no cabeçalho Authorization. Aqui o token vira o usuário (getUser confere no Auth que ele existe;
// o JWT de anon passa no verify_jwt, mas não tem usuário e é recusado).

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

export type Cliente = SupabaseClient;

export interface Usuario {
  id: string;
  email: string | null;
  /** Cliente com o JWT do usuário: as consultas passam pelas políticas de RLS. */
  cliente: Cliente;
}

const URL_SUPABASE = Deno.env.get("SUPABASE_URL")!;

/** Chave pública do projeto (publishable; a anon legada se não houver). */
function chavePublica(): string {
  try {
    const chaves = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    if (typeof chaves.default === "string") return chaves.default;
  } catch { /* variável ausente ou malformada */ }
  return Deno.env.get("SUPABASE_ANON_KEY")!;
}

/** O usuário logado que fez o pedido, ou null se o token não for de um usuário. */
export async function usuarioDoPedido(req: Request): Promise<Usuario | null> {
  const token = req.headers.get("authorization")?.match(/^Bearer\s+(\S+)$/i)?.[1];
  if (!token) return null;
  const cliente = createClient(URL_SUPABASE, chavePublica(), {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await cliente.auth.getUser(token);
  if (error || !data.user) return null;
  return { id: data.user.id, email: data.user.email ?? null, cliente };
}

/** service_role: ignora o RLS. Só depois de conferir o usuário e o papel dele na turma. */
export function clienteServico(): Cliente {
  return createClient(URL_SUPABASE, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/** URL do site do aluno (secret SITE_URL), sem a barra final. */
export function urlSite(): string {
  return (Deno.env.get("SITE_URL") ?? "http://localhost:5173").replace(/\/+$/, "");
}
