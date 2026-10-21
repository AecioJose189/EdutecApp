// Edge Function "turma-api": todo o acesso do app às turmas, numa função só (um deploy).
//
// Entrada: POST com JSON { "acao": "...", ...parâmetros }. Ações:
//   listar_turmas                     → { turmas: [Turma] }
//   criar_turma   { nome, codigo }    → { turma: Turma }
//   publicar      { turma_id, geracao_id, permitir_refazer, tempo_minutos }
//                                     → { atividade_id }   (409 se já publicada ou se formar ciclo)
//
// Turma: { id, nome, codigo, criado_em, n_atividades }
// Erro: { erro: "..." } com status 4xx/5xx.
//
// As tabelas têm RLS sem políticas: só a service_role (usada aqui) lê e escreve.
// Autenticação: verify_jwt = false (config.toml) e a mesma checagem de chave pública da gerar-atividade.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { chavePublicaValida } from "../_shared/chave_publica.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Cliente = SupabaseClient;
type Corpo = Record<string, unknown>;

class ErroPedido extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

const ACOES: Record<string, (supabase: Cliente, corpo: Corpo) => Promise<unknown>> = {
  listar_turmas: listarTurmas,
  criar_turma: criarTurma,
  publicar,
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return erro("Use POST com um JSON { acao, ... }.", 405);

  try {
    if (!chavePublicaValida(req)) {
      return erro("Chave de API ausente ou inválida (envie a chave pública do Supabase em `apikey`).", 401);
    }
    let corpo: Corpo;
    try {
      corpo = await req.json();
    } catch {
      throw new ErroPedido("Corpo da requisição não é um JSON válido.", 400);
    }
    const acao = ACOES[String(corpo?.acao ?? "")];
    if (!acao) throw new ErroPedido(`Ação desconhecida. Use uma de: ${Object.keys(ACOES).join(", ")}.`, 400);

    return json(await acao(clienteServico(), corpo));
  } catch (e) {
    if (e instanceof ErroPedido) {
      console.error(e.message);
      return erro(e.message, e.status);
    }
    console.error("Erro inesperado:", e);
    return erro("Erro interno no servidor.", 500);
  }
});

// ---------- turmas ----------

const COLUNAS_TURMA = "id, nome, codigo, criado_em";

async function listarTurmas(supabase: Cliente) {
  const { data, error } = await supabase
    .from("turmas")
    .select(`${COLUNAS_TURMA}, atividades(count)`)
    .order("criado_em");
  if (error) throw falhaBanco("listar as turmas", error);
  const turmas = (data as (Record<string, unknown> & { atividades: { count: number }[] })[])
    .map(({ atividades, ...t }) => ({ ...t, n_atividades: atividades[0]?.count ?? 0 }));
  return { turmas };
}

async function criarTurma(supabase: Cliente, corpo: Corpo) {
  const nome = texto(corpo.nome, "nome", 120);
  const codigo = texto(corpo.codigo, "codigo", 60);
  const { data, error } = await supabase.from("turmas").insert({ nome, codigo }).select(COLUNAS_TURMA).single();
  if (error) throw falhaBanco("criar a turma", error);
  return { turma: data };
}

// ---------- publicação ----------

/**
 * Publica na turma as questões de uma geração que o professor revisou (RN04) e junta os conceitos
 * da geração ao grafo da turma. Tudo acontece na função publicar_atividade do Postgres, numa
 * transação: se o grafo ficar com ciclo (RN03), nada é gravado.
 */
async function publicar(supabase: Cliente, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  const geracao = Number(corpo.geracao_id);
  if (!Number.isInteger(geracao)) throw new ErroPedido('"geracao_id" deve ser um número inteiro.', 400);
  const tempo = corpo.tempo_minutos == null ? null : Number(corpo.tempo_minutos);
  if (tempo !== null && (!Number.isInteger(tempo) || tempo <= 0)) {
    throw new ErroPedido('"tempo_minutos" deve ser um inteiro positivo ou null (sem limite).', 400);
  }

  const { data, error } = await supabase.rpc("publicar_atividade", {
    turma,
    geracao,
    permitir_refazer: corpo.permitir_refazer !== false,
    tempo_minutos: tempo,
  });
  if (error) {
    const status = { ED404: 404, ED409: 409, ED003: 409 }[error.code ?? ""];
    if (status) throw new ErroPedido(error.message, status);
    throw falhaBanco("publicar a atividade", error);
  }
  return { atividade_id: data };
}

// ---------- utilitários ----------

function uuid(valor: unknown, campo: string): string {
  const t = typeof valor === "string" ? valor.trim() : "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(t)) {
    throw new ErroPedido(`"${campo}" deve ser um uuid.`, 400);
  }
  return t;
}

function texto(valor: unknown, campo: string, maximo: number): string {
  const t = typeof valor === "string" ? valor.trim() : "";
  if (!t) throw new ErroPedido(`"${campo}" é obrigatório.`, 400);
  if (t.length > maximo) throw new ErroPedido(`"${campo}" passa de ${maximo} caracteres.`, 400);
  return t;
}

function falhaBanco(oQue: string, e: { message: string }): ErroPedido {
  console.error(`Falha ao ${oQue}:`, e.message);
  return new ErroPedido(`Não foi possível ${oQue}.`, 500);
}

function clienteServico(): Cliente {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
}

function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

function erro(mensagem: string, status: number): Response {
  return json({ erro: mensagem }, status);
}
