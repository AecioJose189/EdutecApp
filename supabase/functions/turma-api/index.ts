// Edge Function "turma-api": todo o acesso do app às turmas, numa função só (um deploy).
//
// Entrada: POST com JSON { "acao": "...", ...parâmetros }. Ações:
//   listar_turmas                     → { turmas: [Turma] }
//   criar_turma   { nome, codigo }    → { turma: Turma }
//   publicar      { turma_id, geracao_id, permitir_refazer, tempo_minutos }
//                                     → { atividade_id }   (409 se já publicada ou se formar ciclo)
//   grafo         { turma_id }        → { conceitos: [ConceitoGrafo], alunos: [AlunoGrafo] }
//
// Turma: { id, nome, codigo, criado_em, n_atividades, n_alunos, pct_consolidado, pct_desenvolvimento,
//          pct_revisar, conceitos_a_revisar }  (percentuais dos conceitos com dados, pelo estado da média)
// ConceitoGrafo: { nome, prerequisitos, n_alunos, n_respostas, media_dominio, mediana_dominio,
//                  estado_da_media, estado_moda, n_revisar }  (sem respostas: números 0 e estados null)
// AlunoGrafo: { rotulo: "Aluno 01", pior_estado, menor_dominio }  — só pseudônimos (RN13)
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
  grafo,
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

  const { data: resumos, error: erroResumo } = await supabase.from("resumo_turmas").select("*");
  if (erroResumo) throw falhaBanco("resumir as turmas", erroResumo);
  const porTurma = new Map((resumos as Resumo[]).map((r) => [r.turma_id, r]));

  const turmas = (data as (Record<string, unknown> & { id: string; atividades: { count: number }[] })[])
    .map(({ atividades, ...t }) => {
      const r = porTurma.get(t.id);
      const [consolidado, desenvolvimento, revisar] = percentuais([
        r?.n_consolidado ?? 0,
        r?.n_desenvolvimento ?? 0,
        r?.n_revisar ?? 0,
      ]);
      return {
        ...t,
        n_atividades: atividades[0]?.count ?? 0,
        n_alunos: r?.n_alunos ?? 0,
        pct_consolidado: consolidado,
        pct_desenvolvimento: desenvolvimento,
        pct_revisar: revisar,
        conceitos_a_revisar: r?.n_revisar ?? 0,
      };
    });
  return { turmas };
}

interface Resumo {
  turma_id: string;
  n_alunos: number;
  n_consolidado: number;
  n_desenvolvimento: number;
  n_revisar: number;
}

/** Percentuais inteiros que somam 100 (maiores restos); todos 0 se não houver nada. */
function percentuais(contagens: number[]): number[] {
  const total = contagens.reduce((a, b) => a + b, 0);
  if (total === 0) return contagens.map(() => 0);
  const exatos = contagens.map((c) => (c * 100) / total);
  const inteiros = exatos.map(Math.floor);
  let falta = 100 - inteiros.reduce((a, b) => a + b, 0);
  for (const i of exatos.map((e, i) => [e - Math.floor(e), i]).sort((a, b) => b[0] - a[0]).map(([, i]) => i)) {
    if (falta-- <= 0) break;
    inteiros[i]++;
  }
  return inteiros;
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

// ---------- grafo da turma ----------

const ORDEM_ESTADO: Record<string, number> = { revisar: 1, em_desenvolvimento: 2, consolidado: 3 };

async function grafo(supabase: Cliente, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");

  const [conceitos, dominioTurma, dominioAlunos] = await Promise.all([
    supabase.from("conceitos_turma").select("nome, prerequisitos").eq("turma_id", turma).order("criado_em"),
    supabase.from("dominio_turma").select("*").eq("turma_id", turma),
    supabase.from("dominio").select("aluno_pseudonimo, p_dominio").eq("turma_id", turma),
  ]);
  for (const r of [conceitos, dominioTurma, dominioAlunos]) {
    if (r.error) throw falhaBanco("carregar o grafo da turma", r.error);
  }

  const resumo = new Map(
    (dominioTurma.data as Record<string, unknown>[]).map((d) => [d.conceito as string, d]),
  );
  const nos = (conceitos.data as { nome: string; prerequisitos: string[] }[]).map((c) => {
    const d = resumo.get(c.nome);
    return {
      nome: c.nome,
      prerequisitos: c.prerequisitos,
      n_alunos: Number(d?.n_alunos ?? 0),
      n_respostas: Number(d?.n_respostas ?? 0),
      media_dominio: d ? Number(d.media_dominio) : null,
      mediana_dominio: d ? Number(d.mediana_dominio) : null,
      estado_da_media: (d?.estado_da_media as string) ?? null,
      estado_moda: (d?.estado_moda as string) ?? null,
      n_revisar: Number(d?.n_revisar ?? 0),
    };
  });

  // Pior conceito de cada aluno (menor domínio estimado), com rótulo "Aluno NN".
  const menor = new Map<string, number>();
  for (const d of dominioAlunos.data as { aluno_pseudonimo: string; p_dominio: number }[]) {
    const p = Number(d.p_dominio);
    menor.set(d.aluno_pseudonimo, Math.min(p, menor.get(d.aluno_pseudonimo) ?? 1));
  }
  const alunos = [...menor.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([pseudonimo, p], i) => ({
      rotulo: `Aluno ${(pseudonimo.match(/(\d+)$/)?.[1] ?? String(i + 1)).padStart(2, "0")}`,
      pior_estado: estadoDominio(p),
      menor_dominio: Math.round(p * 10000) / 10000,
    }))
    .sort((a, b) => ORDEM_ESTADO[a.pior_estado] - ORDEM_ESTADO[b.pior_estado] || a.rotulo.localeCompare(b.rotulo));

  return { conceitos: nos, alunos };
}

/** Mesmos limiares (propostos) de public.estado_dominio: 0,5 e 0,8. */
function estadoDominio(p: number): string {
  return p < 0.5 ? "revisar" : p < 0.8 ? "em_desenvolvimento" : "consolidado";
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
