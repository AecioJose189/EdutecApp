// Edge Function "turma-api": tudo o que o professor (app desktop) faz nas turmas, numa função só.
//
// Entrada: POST com JSON { "acao": "...", ...parâmetros } e o JWT do professor (verify_jwt = true).
// O professor de uma turma é quem a criou (turmas.professor_id); toda ação sobre uma turma, atividade
// ou sessão confere isso antes. Ações:
//   listar_turmas                     → { turmas: [Turma] }   (só as do usuário)
//   criar_turma   { nome, codigo }    → { turma: Turma }
//   criar_convite { turma_id, novo? } → { convite: { codigo, link, criado_em } }
//                                       devolve o convite ativo; novo = true desativa e cria outro
//   publicar      { turma_id, geracao_id, permitir_refazer, tempo_minutos, titulo? }
//                                     → { atividade_id }   (409 se já publicada ou se formar ciclo)
//   listar_atividades { turma_id }    → { atividades: [{ id, titulo, publicada_em, n_questoes, tempo_minutos,
//                                         permitir_refazer, sessao_aberta: Sessao | null }] }
//   abrir_sessao  { atividade_id }    → { sessao: Sessao }   (devolve a aberta, se já houver)
//   token_qr      { sessao_id }       → { token, url, expira_em, janela_segundos }
//                                       url = <SITE_URL>/s/<token>; pedir de novo a cada janela (20 s)
//   encerrar_entrada { sessao_id }    → { sessao: Sessao }   (quem já entrou ainda envia)
//   encerrar_sessao  { sessao_id }    → { sessao: Sessao }
//   sessao        { sessao_id }       → { sessao: Sessao, participantes: [Participante] }
//   grafo         { turma_id }        → { conceitos: [ConceitoGrafo], alunos: [AlunoGrafo] }
//   simular       { turma_id }        → { n_alunos, n_respostas }   DEMONSTRAÇÃO: 25 pseudônimos
//                                       respondendo às questões publicadas (substitui a anterior)
//
// Turma: { id, nome, codigo, criado_em, n_atividades, n_alunos, n_membros, pct_consolidado,
//          pct_desenvolvimento, pct_revisar, conceitos_a_revisar }
//        (percentuais dos conceitos com dados, pelo estado da média)
// Sessao: { id, atividade_id, aberta, entrada_aberta, aberta_em, encerrada_em }
// Participante: { tentativa_id, aluno_id, nome, iniciada_em, prazo_em, enviada_em, acertos,
//                 total_multipla_escolha, abertas_aguardando_correcao }
// ConceitoGrafo: { nome, prerequisitos, n_alunos, n_respostas, media_dominio, mediana_dominio,
//                  estado_da_media, estado_moda, n_revisar }  (sem respostas: números 0 e estados null)
// AlunoGrafo: { aluno_id, rotulo, pior_estado, menor_dominio }  rotulo = nome do aluno (pseudônimo
//             "Aluno NN" para a simulação)
// Erro: { erro: "..." } com status 4xx/5xx (401 sem usuário).
//
// Ao vivo (Realtime, no cliente com o JWT do professor): postgres_changes em public.tentativas com
// filtro sessao_id=eq.<id> — INSERT = aluno entrou; UPDATE com enviada_em = enviou (acertos etc.).
// As políticas de RLS garantem que só o professor da turma recebe os eventos.

import { type Cliente, clienteServico, type Usuario, urlSite, usuarioDoPedido } from "../_shared/auth.ts";
import { CORS, json, UUID } from "../_shared/http.ts";
import { fimDaJanela, gerarToken, JANELA_SEGUNDOS, janelaAtual } from "../_shared/qr.ts";

type Corpo = Record<string, unknown>;

/** Usuário (cliente com RLS) e service_role (só depois de conferir que a turma é dele). */
interface Contexto {
  usuario: Usuario;
  servico: Cliente;
}

class ErroPedido extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

const ACOES: Record<string, (ctx: Contexto, corpo: Corpo) => Promise<unknown>> = {
  listar_turmas: listarTurmas,
  criar_turma: criarTurma,
  criar_convite: criarConvite,
  publicar,
  listar_atividades: listarAtividades,
  abrir_sessao: abrirSessao,
  token_qr: tokenQr,
  encerrar_entrada: encerrarEntrada,
  encerrar_sessao: encerrarSessao,
  sessao: verSessao,
  grafo,
  simular,
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return erro("Use POST com um JSON { acao, ... }.", 405);

  try {
    const usuario = await usuarioDoPedido(req);
    if (!usuario) return erro("Entre com sua conta Google para continuar.", 401);
    let corpo: Corpo;
    try {
      corpo = await req.json();
    } catch {
      throw new ErroPedido("Corpo da requisição não é um JSON válido.", 400);
    }
    const acao = ACOES[String(corpo?.acao ?? "")];
    if (!acao) throw new ErroPedido(`Ação desconhecida. Use uma de: ${Object.keys(ACOES).join(", ")}.`, 400);

    return json(await acao({ usuario, servico: clienteServico() }, corpo));
  } catch (e) {
    if (e instanceof ErroPedido) {
      console.error(e.message);
      return erro(e.message, e.status);
    }
    console.error("Erro inesperado:", e);
    return erro("Erro interno no servidor.", 500);
  }
});

// ---------- dono da turma ----------

/** Confere que a turma existe e é do usuário (404 para não revelar turmas de outros). */
async function exigirTurma(ctx: Contexto, turmaId: string): Promise<void> {
  const { data, error } = await ctx.usuario.cliente
    .from("turmas").select("id").eq("id", turmaId).eq("professor_id", ctx.usuario.id).maybeSingle();
  if (error) throw falhaBanco("conferir a turma", error);
  if (!data) throw new ErroPedido("Turma não encontrada.", 404);
}

interface AtividadeLinha {
  id: string;
  turma_id: string;
}

async function exigirAtividade(ctx: Contexto, atividadeId: string): Promise<AtividadeLinha> {
  const { data, error } = await ctx.servico.from("atividades").select("id, turma_id").eq("id", atividadeId).maybeSingle();
  if (error) throw falhaBanco("conferir a atividade", error);
  if (!data) throw new ErroPedido("Atividade não encontrada.", 404);
  await exigirTurma(ctx, data.turma_id).catch(() => {
    throw new ErroPedido("Atividade não encontrada.", 404);
  });
  return data as AtividadeLinha;
}

const COLUNAS_SESSAO = "id, atividade_id, aberta, entrada_aberta, aberta_em, encerrada_em";

interface SessaoLinha {
  id: string;
  atividade_id: string;
  aberta: boolean;
  entrada_aberta: boolean;
  aberta_em: string;
  encerrada_em: string | null;
}

async function exigirSessao(ctx: Contexto, sessaoId: string): Promise<SessaoLinha> {
  const { data, error } = await ctx.servico.from("sessoes").select(COLUNAS_SESSAO).eq("id", sessaoId).maybeSingle();
  if (error) throw falhaBanco("conferir a sessão", error);
  if (!data) throw new ErroPedido("Sessão não encontrada.", 404);
  await exigirAtividade(ctx, data.atividade_id).catch(() => {
    throw new ErroPedido("Sessão não encontrada.", 404);
  });
  return data as SessaoLinha;
}

// ---------- turmas ----------

const COLUNAS_TURMA = "id, nome, codigo, criado_em";

async function listarTurmas(ctx: Contexto) {
  const { data, error } = await ctx.servico
    .from("turmas")
    .select(`${COLUNAS_TURMA}, atividades(count), membros_turma(count)`)
    .eq("professor_id", ctx.usuario.id)
    .order("criado_em");
  if (error) throw falhaBanco("listar as turmas", error);
  const linhas = data as (Record<string, unknown> & {
    id: string;
    atividades: { count: number }[];
    membros_turma: { count: number }[];
  })[];

  const { data: resumos, error: erroResumo } = await ctx.servico
    .from("resumo_turmas").select("*").in("turma_id", linhas.map((t) => t.id));
  if (erroResumo) throw falhaBanco("resumir as turmas", erroResumo);
  const porTurma = new Map((resumos as Resumo[]).map((r) => [r.turma_id, r]));

  const turmas = linhas.map(({ atividades, membros_turma, ...t }) => {
    const r = porTurma.get(t.id);
    const [consolidado, desenvolvimento, revisar] = percentuais([
      r?.n_consolidado ?? 0,
      r?.n_desenvolvimento ?? 0,
      r?.n_revisar ?? 0,
    ]);
    const membros = membros_turma[0]?.count ?? 0;
    return {
      ...t,
      n_atividades: atividades[0]?.count ?? 0,
      // Alunos com dados (inclui a simulação) ou matriculados, o que for maior.
      n_alunos: Math.max(r?.n_alunos ?? 0, membros),
      n_membros: membros,
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

/** Pelo cliente do usuário: o RLS exige professor_id = ele (preenchido pelo padrão auth.uid()). */
async function criarTurma(ctx: Contexto, corpo: Corpo) {
  const nome = texto(corpo.nome, "nome", 120);
  const codigo = texto(corpo.codigo, "codigo", 60);
  const { data, error } = await ctx.usuario.cliente
    .from("turmas").insert({ nome, codigo }).select(COLUNAS_TURMA).single();
  if (error) throw falhaBanco("criar a turma", error);
  return { turma: data };
}

// ---------- convites ----------

async function criarConvite(ctx: Contexto, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  await exigirTurma(ctx, turma);
  const cliente = ctx.usuario.cliente; // convites: o RLS só deixa o professor da turma

  if (corpo.novo !== true) {
    const { data, error } = await cliente
      .from("convites").select("codigo, criado_em").eq("turma_id", turma).eq("ativo", true).maybeSingle();
    if (error) throw falhaBanco("carregar o convite", error);
    if (data) return { convite: comLink(data) };
  } else {
    const { error } = await cliente.from("convites").update({ ativo: false }).eq("turma_id", turma).eq("ativo", true);
    if (error) throw falhaBanco("desativar o convite anterior", error);
  }

  // O código é sorteado no banco; numa colisão rara (código repetido), sorteia de novo.
  for (let tentativa = 0; tentativa < 5; tentativa++) {
    const { data, error } = await cliente.from("convites").insert({ turma_id: turma }).select("codigo, criado_em").single();
    if (!error) return { convite: comLink(data) };
    if (error.code !== "23505") throw falhaBanco("criar o convite", error);
    // 23505 também é "já existe um ativo" (dois cliques): devolve o que ganhou.
    const { data: ativo } = await cliente
      .from("convites").select("codigo, criado_em").eq("turma_id", turma).eq("ativo", true).maybeSingle();
    if (ativo) return { convite: comLink(ativo) };
  }
  throw new ErroPedido("Não foi possível criar o convite.", 500);
}

function comLink(c: { codigo: string; criado_em: string }) {
  return { codigo: c.codigo, link: `${urlSite()}/convite/${c.codigo}`, criado_em: c.criado_em };
}

// ---------- publicação ----------

/**
 * Publica na turma as questões de uma geração que o professor revisou (RN04) e junta os conceitos
 * da geração ao grafo da turma. Tudo acontece na função publicar_atividade do Postgres, numa
 * transação: se o grafo ficar com ciclo (RN03), nada é gravado.
 */
async function publicar(ctx: Contexto, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  await exigirTurma(ctx, turma);
  const geracao = Number(corpo.geracao_id);
  if (!Number.isInteger(geracao)) throw new ErroPedido('"geracao_id" deve ser um número inteiro.', 400);
  const tempo = corpo.tempo_minutos == null ? null : Number(corpo.tempo_minutos);
  if (tempo !== null && (!Number.isInteger(tempo) || tempo <= 0)) {
    throw new ErroPedido('"tempo_minutos" deve ser um inteiro positivo ou null (sem limite).', 400);
  }
  const titulo = corpo.titulo == null ? null : texto(corpo.titulo, "titulo", 200);

  const { data, error } = await ctx.servico.rpc("publicar_atividade", {
    turma,
    geracao,
    permitir_refazer: corpo.permitir_refazer !== false,
    tempo_minutos: tempo,
    titulo,
  });
  if (error) {
    const status = { ED404: 404, ED409: 409, ED003: 409 }[error.code ?? ""];
    if (status) throw new ErroPedido(error.message, status);
    throw falhaBanco("publicar a atividade", error);
  }
  return { atividade_id: data };
}

async function listarAtividades(ctx: Contexto, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  await exigirTurma(ctx, turma);
  const { data, error } = await ctx.servico
    .from("atividades")
    .select(`id, titulo, publicada_em, tempo_minutos, permitir_refazer, questoes_aluno, sessoes(${COLUNAS_SESSAO})`)
    .eq("turma_id", turma)
    .eq("sessoes.aberta", true)
    .order("publicada_em");
  if (error) throw falhaBanco("listar as atividades", error);
  const atividades = (data as (Record<string, unknown> & { questoes_aluno: unknown[]; sessoes: SessaoLinha[] })[])
    .map(({ questoes_aluno, sessoes, ...a }) => ({
      ...a,
      n_questoes: questoes_aluno.length,
      sessao_aberta: sessoes[0] ?? null,
    }));
  return { atividades };
}

// ---------- sessão em sala ----------

async function abrirSessao(ctx: Contexto, corpo: Corpo) {
  const atividade = await exigirAtividade(ctx, uuid(corpo.atividade_id, "atividade_id"));
  const aberta = async () => {
    const { data, error } = await ctx.servico
      .from("sessoes").select(COLUNAS_SESSAO).eq("atividade_id", atividade.id).eq("aberta", true).maybeSingle();
    if (error) throw falhaBanco("carregar a sessão", error);
    return data as SessaoLinha | null;
  };

  const existente = await aberta();
  if (existente) return { sessao: existente };
  const { data, error } = await ctx.servico
    .from("sessoes").insert({ atividade_id: atividade.id }).select(COLUNAS_SESSAO).single();
  if (!error) return { sessao: data };
  if (error.code === "23505") { // dois cliques: a outra chamada abriu primeiro
    const outra = await aberta();
    if (outra) return { sessao: outra };
  }
  throw falhaBanco("abrir a sessão", error);
}

async function tokenQr(ctx: Contexto, corpo: Corpo) {
  const sessao = await exigirSessao(ctx, uuid(corpo.sessao_id, "sessao_id"));
  if (!sessao.aberta) throw new ErroPedido("A sessão está encerrada.", 409);
  const { data, error } = await ctx.servico.from("sessoes").select("segredo").eq("id", sessao.id).single();
  if (error) throw falhaBanco("gerar o QR", error);

  const janela = janelaAtual();
  const token = await gerarToken(sessao.id, data.segredo, janela);
  return {
    token,
    url: `${urlSite()}/s/${token}`,
    expira_em: fimDaJanela(janela).toISOString(),
    janela_segundos: JANELA_SEGUNDOS,
  };
}

async function encerrarEntrada(ctx: Contexto, corpo: Corpo) {
  const sessao = await exigirSessao(ctx, uuid(corpo.sessao_id, "sessao_id"));
  return { sessao: await atualizarSessao(ctx, sessao.id, { entrada_aberta: false }) };
}

async function encerrarSessao(ctx: Contexto, corpo: Corpo) {
  const sessao = await exigirSessao(ctx, uuid(corpo.sessao_id, "sessao_id"));
  if (!sessao.aberta) return { sessao };
  return {
    sessao: await atualizarSessao(ctx, sessao.id, {
      aberta: false,
      entrada_aberta: false,
      encerrada_em: new Date().toISOString(),
    }),
  };
}

async function atualizarSessao(ctx: Contexto, id: string, campos: Record<string, unknown>) {
  const { data, error } = await ctx.servico.from("sessoes").update(campos).eq("id", id).select(COLUNAS_SESSAO).single();
  if (error) throw falhaBanco("atualizar a sessão", error);
  return data;
}

async function verSessao(ctx: Contexto, corpo: Corpo) {
  const sessao = await exigirSessao(ctx, uuid(corpo.sessao_id, "sessao_id"));
  const { data, error } = await ctx.servico
    .from("tentativas")
    .select(`id, aluno_id, aluno_nome, iniciada_em, prazo_em, enviada_em, acertos, total_multipla_escolha,
             abertas_aguardando_correcao`)
    .eq("sessao_id", sessao.id)
    .order("iniciada_em");
  if (error) throw falhaBanco("carregar os participantes", error);
  const participantes = (data as Record<string, unknown>[]).map(({ id, aluno_nome, ...t }) => ({
    tentativa_id: id,
    nome: aluno_nome,
    ...t,
  }));
  return { sessao, participantes };
}

// ---------- grafo da turma ----------

const ORDEM_ESTADO: Record<string, number> = { revisar: 1, em_desenvolvimento: 2, consolidado: 3 };

async function grafo(ctx: Contexto, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  await exigirTurma(ctx, turma);
  const supabase = ctx.servico;

  const [conceitos, dominioTurma, dominioAlunos] = await Promise.all([
    supabase.from("conceitos_turma").select("nome, prerequisitos").eq("turma_id", turma).order("criado_em"),
    supabase.from("dominio_turma").select("*").eq("turma_id", turma),
    supabase.from("dominio").select("aluno_pseudonimo, aluno_id, p_dominio, perfis!dominio_aluno_id_fkey(nome)").eq("turma_id", turma),
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

  // Pior conceito de cada aluno (menor domínio estimado). Alunos reais aparecem pelo nome (o
  // professor sabe quem são); a simulação, por "Aluno NN".
  interface LinhaDominio {
    aluno_pseudonimo: string;
    aluno_id: string | null;
    p_dominio: number;
    perfis: { nome: string } | null;
  }
  const porAluno = new Map<string, { aluno_id: string | null; nome: string | null; menor: number }>();
  for (const d of dominioAlunos.data as unknown as LinhaDominio[]) {
    const atual = porAluno.get(d.aluno_pseudonimo);
    const p = Number(d.p_dominio);
    porAluno.set(d.aluno_pseudonimo, {
      aluno_id: d.aluno_id,
      nome: d.perfis?.nome ?? null,
      menor: Math.min(p, atual?.menor ?? 1),
    });
  }
  const alunos = [...porAluno.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([pseudonimo, a], i) => ({
      aluno_id: a.aluno_id,
      rotulo: a.nome ?? `Aluno ${(pseudonimo.match(/(\d+)$/)?.[1] ?? String(i + 1)).padStart(2, "0")}`,
      pior_estado: estadoDominio(a.menor),
      menor_dominio: Math.round(a.menor * 10000) / 10000,
    }))
    .sort((a, b) => ORDEM_ESTADO[a.pior_estado] - ORDEM_ESTADO[b.pior_estado] || a.rotulo.localeCompare(b.rotulo));

  return { conceitos: nos, alunos };
}

/** Mesmos limiares (propostos) de public.estado_dominio: 0,5 e 0,8. */
function estadoDominio(p: number): string {
  return p < 0.5 ? "revisar" : p < 0.8 ? "em_desenvolvimento" : "consolidado";
}

// ---------- demonstração ----------

/**
 * Simula 25 alunos (pseudônimos "sim-...", RN13) respondendo às questões publicadas na turma.
 * Tudo roda em simular_respostas no Postgres (semente fixa, resultado reproduzível): uma chamada só,
 * sem gastar a CPU da Edge Function com centenas de respostas.
 */
async function simular(ctx: Contexto, corpo: Corpo) {
  const turma = uuid(corpo.turma_id, "turma_id");
  await exigirTurma(ctx, turma);
  const { count, error: erroContagem } = await ctx.servico
    .from("atividades").select("id", { count: "exact", head: true }).eq("turma_id", turma);
  if (erroContagem) throw falhaBanco("simular as respostas", erroContagem);
  if (!count) throw new ErroPedido("Publique uma atividade antes de simular as respostas.", 409);

  const { data, error } = await ctx.servico.rpc("simular_respostas", { turma });
  if (error) {
    if (error.code === "ED404") throw new ErroPedido(error.message, 404);
    throw falhaBanco("simular as respostas", error);
  }
  return (data as unknown[])[0];
}

// ---------- utilitários ----------

function uuid(valor: unknown, campo: string): string {
  const t = typeof valor === "string" ? valor.trim() : "";
  if (!UUID.test(t)) throw new ErroPedido(`"${campo}" deve ser um uuid.`, 400);
  return t;
}

function texto(valor: unknown, campo: string, maximo: number): string {
  const t = typeof valor === "string" ? valor.trim() : "";
  if (!t) throw new ErroPedido(`"${campo}" é obrigatório.`, 400);
  if (t.length > maximo) throw new ErroPedido(`"${campo}" passa de ${maximo} caracteres.`, 400);
  return t;
}

function falhaBanco(oQue: string, e: { message: string; code?: string }): ErroPedido {
  console.error(`Falha ao ${oQue}:`, e.code, e.message);
  return new ErroPedido(`Não foi possível ${oQue}.`, 500);
}

function erro(mensagem: string, status: number): Response {
  return json({ erro: mensagem }, status);
}
