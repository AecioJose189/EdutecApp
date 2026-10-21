// Edge Function "turma-api": todo o acesso do app às turmas, numa função só (um deploy).
//
// Entrada: POST com JSON { "acao": "...", ...parâmetros }. Ações:
//   listar_turmas                     → { turmas: [Turma] }
//   criar_turma   { nome, codigo }    → { turma: Turma }
//
// Turma: { id, nome, codigo, criado_em }
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
  const { data, error } = await supabase.from("turmas").select(COLUNAS_TURMA).order("criado_em");
  if (error) throw falhaBanco("listar as turmas", error);
  return { turmas: data };
}

async function criarTurma(supabase: Cliente, corpo: Corpo) {
  const nome = texto(corpo.nome, "nome", 120);
  const codigo = texto(corpo.codigo, "codigo", 60);
  const { data, error } = await supabase.from("turmas").insert({ nome, codigo }).select(COLUNAS_TURMA).single();
  if (error) throw falhaBanco("criar a turma", error);
  return { turma: data };
}

// ---------- utilitários ----------

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
