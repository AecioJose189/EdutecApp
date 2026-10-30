// Edge Function "gerar-atividade": recebe um PDF e devolve conceitos e questões gerados pela IA.
//
// Entrada (multipart/form-data):
//   pdf          arquivo PDF, até 20 MB (obrigatório)
//   quantidade   1–10 (padrão 5)
//   formato      multipla_escolha | aberta | mista (padrão mista)
//   dificuldade  facil | media | dificil (padrão media)
//   forcar_nova  "true" para gerar de novo mesmo havendo resultado salvo (padrão false)
//
// Saída 200: { conceitos, questoes, arquivo, modelo, nivel_raciocinio, tokens, duracao_ms,
//              reaproveitado, gerado_em, geracao_id, reserva, tentativas }
// modelo = quem gerou; reserva = true se foi o modelo reserva; tentativas = [{ modelo, ms, resultado }]
// (vazia quando reaproveitado).
//
// Robustez (_shared/gemini.ts, gerarComReserva): em 503/429/rede/tempo, repete o modelo principal uma
// vez e depois tenta o modelo reserva (GEMINI_MODELO_RESERVA). Tudo termina até ~140 s depois do
// início do pedido (a função morre em 150 s); sem sucesso, 503 (ou 429 se foi só cota) com mensagem clara.
// geracao_id identifica a linha de `geracoes`; a turma-api usa para publicar (null se não foi salva).
//
// RN12 (cada PDF é processado uma vez): o PDF é identificado pelo SHA-256 e salvo como
// pdfs/<hash>.pdf. Se já houver na tabela `geracoes` um resultado para o mesmo hash e os mesmos
// parâmetros, ele volta na hora, sem chamar o Gemini, com reaproveitado = true.
// Erro:      { erro: "..." } com status 4xx/5xx.
//
// Autenticação: verify_jwt = true e só usuários logados (o professor no desktop). As tabelas e o
// Storage continuam acessados com a service_role: geracoes é um cache por PDF, sem dono (RN12).

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { usuarioDoPedido } from "../_shared/auth.ts";
import { ErroGemini, gerarComReserva } from "../_shared/gemini.ts";

const TAMANHO_MAXIMO = 20 * 1024 * 1024;
/** Prazo total do pedido (a Edge Function é encerrada em 150 s). */
const PRAZO_TOTAL_MS = 140_000;
const BUCKET = "materiais";
const FORMATOS = ["multipla_escolha", "aberta", "mista"] as const;
const DIFICULDADES = ["facil", "media", "dificil"] as const;
type Formato = typeof FORMATOS[number];
type Dificuldade = typeof DIFICULDADES[number];

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface Conceito {
  nome: string;
  prerequisitos: string[];
}
interface Alternativa {
  texto: string;
  correta: boolean;
  feedback: string;
}
interface Questao {
  tipo: "multipla_escolha" | "aberta";
  enunciado: string;
  conceitos: string[];
  pagina_origem: number | null;
  alternativas: Alternativa[] | null;
  rubrica: string | null;
}
interface Atividade {
  conceitos: Conceito[];
  questoes: Questao[];
}
/** O que fica salvo em geracoes.resultado. */
interface ResultadoSalvo extends Atividade {
  nivel_raciocinio: string;
  tokens: unknown;
}

class ErroPedido extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return erro("Use POST com multipart/form-data.", 405);

  const inicio = Date.now();
  try {
    if (!(await usuarioDoPedido(req))) {
      return erro("Entre com sua conta Google para gerar atividades.", 401);
    }

    const { pdf, quantidade, formato, dificuldade, forcarNova } = await lerFormulario(req);
    const hash = await sha256(pdf);
    const caminho = `pdfs/${hash}.pdf`;
    const supabase = clienteServico();
    const parametros = { hash_pdf: hash, quantidade, formato, dificuldade };

    if (!forcarNova) {
      const salvo = await buscarGeracao(supabase, parametros);
      if (salvo) {
        return json({
          conceitos: salvo.resultado.conceitos,
          questoes: salvo.resultado.questoes,
          arquivo: `${BUCKET}/${caminho}`,
          modelo: salvo.modelo,
          nivel_raciocinio: salvo.resultado.nivel_raciocinio,
          tokens: salvo.resultado.tokens,
          duracao_ms: Date.now() - inicio,
          reaproveitado: true,
          gerado_em: new Date(salvo.criado_em).toISOString(),
          geracao_id: salvo.id,
          reserva: false,
          tentativas: [],
        });
      }
    }

    // TODO: a especificação prevê uma questão por chamada ao Gemini (limites do plano gratuito:
    // ~10 pedidos/min e 150 s por Edge Function). Para a demo da 1ª VA, conceitos e todas as
    // questões saem numa única chamada. Depois: 1 chamada para os conceitos e 1 por questão,
    // orquestradas pelo app ou por uma fila.
    const [, resposta] = await Promise.all([
      salvarPdf(supabase, caminho, pdf),
      gerarComReserva<Atividade>({
        pdf,
        instrucaoSistema: INSTRUCAO_SISTEMA,
        prompt: montarPrompt(quantidade, formato, dificuldade),
        schema: SCHEMA_ATIVIDADE,
      }, inicio + PRAZO_TOTAL_MS),
    ]);
    console.log(
      `Gerado por ${resposta.modelo}${resposta.reserva ? " (RESERVA)" : ""} em ${Date.now() - inicio} ms; ` +
        `tentativas: ${resposta.tentativas.map((t) => `${t.modelo} ${t.resultado} ${t.ms} ms`).join(", ")}`,
    );

    const problemas = validarAtividade(resposta.dados, quantidade, formato);
    if (problemas.length > 0) {
      console.error("Resposta do Gemini reprovada na validação:", problemas);
      return erro(`A IA devolveu uma atividade inválida: ${problemas.join("; ")}`, 502);
    }

    const resultado: ResultadoSalvo = {
      conceitos: resposta.dados.conceitos,
      questoes: resposta.dados.questoes.map(normalizarQuestao),
      nivel_raciocinio: resposta.nivelRaciocinio,
      tokens: resposta.tokens,
    };
    const { geradoEm, id } = await registrarGeracao(supabase, { ...parametros, resultado, modelo: resposta.modelo });

    return json({
      conceitos: resultado.conceitos,
      questoes: resultado.questoes,
      arquivo: `${BUCKET}/${caminho}`,
      modelo: resposta.modelo,
      nivel_raciocinio: resultado.nivel_raciocinio,
      tokens: resultado.tokens,
      duracao_ms: Date.now() - inicio,
      reaproveitado: false,
      gerado_em: geradoEm,
      geracao_id: id,
      reserva: resposta.reserva,
      tentativas: resposta.tentativas,
    });
  } catch (e) {
    if (e instanceof ErroPedido || e instanceof ErroGemini) {
      console.error(e.message);
      return erro(e.message, e.status);
    }
    console.error("Erro inesperado:", e);
    return erro("Erro interno ao gerar a atividade.", 500);
  }
});

// ---------- entrada ----------

async function lerFormulario(req: Request) {
  if (!(req.headers.get("content-type") ?? "").includes("multipart/form-data")) {
    throw new ErroPedido("Envie os dados como multipart/form-data.", 415);
  }
  const tamanhoDeclarado = Number(req.headers.get("content-length") ?? 0);
  if (tamanhoDeclarado > TAMANHO_MAXIMO + 64 * 1024) {
    throw new ErroPedido("O PDF passa de 20 MB.", 413);
  }

  let form: FormData;
  try {
    form = await req.formData();
  } catch {
    throw new ErroPedido("Não foi possível ler o formulário enviado.", 400);
  }

  const arquivo = form.get("pdf");
  if (!(arquivo instanceof File)) throw new ErroPedido('Campo "pdf" ausente.', 400);
  if (arquivo.size === 0) throw new ErroPedido("O PDF está vazio.", 400);
  if (arquivo.size > TAMANHO_MAXIMO) throw new ErroPedido("O PDF passa de 20 MB.", 413);
  const pdf = new Uint8Array(await arquivo.arrayBuffer());
  // Assinatura "%PDF-" no início do arquivo.
  if (new TextDecoder().decode(pdf.subarray(0, 5)) !== "%PDF-") {
    throw new ErroPedido("O arquivo enviado não é um PDF.", 415);
  }

  const quantidadeTexto = (form.get("quantidade") ?? "5").toString().trim() || "5";
  const quantidade = Number(quantidadeTexto);
  if (!Number.isInteger(quantidade) || quantidade < 1 || quantidade > 10) {
    throw new ErroPedido('"quantidade" deve ser um inteiro de 1 a 10.', 400);
  }
  const formato = (form.get("formato") ?? "mista").toString().trim() as Formato;
  if (!FORMATOS.includes(formato)) {
    throw new ErroPedido(`"formato" deve ser um de: ${FORMATOS.join(", ")}.`, 400);
  }
  const dificuldade = (form.get("dificuldade") ?? "media").toString().trim() as Dificuldade;
  if (!DIFICULDADES.includes(dificuldade)) {
    throw new ErroPedido(`"dificuldade" deve ser uma de: ${DIFICULDADES.join(", ")}.`, 400);
  }

  const forcarNova = (form.get("forcar_nova") ?? "").toString().trim().toLowerCase() === "true";

  return { pdf, quantidade, formato, dificuldade, forcarNova };
}

async function sha256(dados: Uint8Array): Promise<string> {
  const resumo = new Uint8Array(await crypto.subtle.digest("SHA-256", dados as Uint8Array<ArrayBuffer>));
  return Array.from(resumo, (b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------- storage e banco (service_role: ignora RLS) ----------

type Cliente = SupabaseClient;

interface Parametros {
  hash_pdf: string;
  quantidade: number;
  formato: Formato;
  dificuldade: Dificuldade;
}

function clienteServico(): Cliente {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
}

/** Salva o PDF uma vez só: se pdfs/<hash>.pdf já existe, não envia de novo. */
async function salvarPdf(supabase: Cliente, caminho: string, pdf: Uint8Array) {
  const { error } = await supabase.storage.from(BUCKET).upload(caminho, pdf, {
    contentType: "application/pdf",
    upsert: false,
  });
  if (!error) return;
  const status = String((error as { statusCode?: string | number }).statusCode ?? "");
  if (status === "409" || /already exists|duplicate/i.test(error.message)) return;
  throw new ErroPedido(`Não foi possível salvar o PDF no Storage: ${error.message}`, 500);
}

async function buscarGeracao(supabase: Cliente, p: Parametros) {
  const { data, error } = await supabase
    .from("geracoes")
    .select("id, resultado, modelo, criado_em")
    .eq("hash_pdf", p.hash_pdf)
    .eq("quantidade", p.quantidade)
    .eq("formato", p.formato)
    .eq("dificuldade", p.dificuldade)
    .order("criado_em", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) {
    // Sem o reaproveitamento a função ainda funciona: segue para uma geração nova.
    console.error("Falha ao buscar geração salva:", error.message);
    return null;
  }
  return data as { id: number; resultado: ResultadoSalvo; modelo: string | null; criado_em: string } | null;
}

/**
 * Grava a geração e devolve o id e a data (ISO). Se falhar, a atividade ainda é devolvida ao app
 * (com id null: dá para revisar, mas não para publicar).
 */
async function registrarGeracao(
  supabase: Cliente,
  linha: Parametros & { resultado: ResultadoSalvo; modelo: string },
): Promise<{ geradoEm: string; id: number | null }> {
  const { data, error } = await supabase.from("geracoes").insert(linha).select("id, criado_em").single();
  if (error) {
    console.error("Falha ao salvar a geração:", error.message);
    return { geradoEm: new Date().toISOString(), id: null };
  }
  const salva = data as { id: number; criado_em: string };
  return { geradoEm: new Date(salva.criado_em).toISOString(), id: salva.id };
}

// ---------- prompt e schema ----------

const INSTRUCAO_SISTEMA = `Você é um assistente pedagógico de ensino superior.
A partir do material em PDF do professor, você identifica os conceitos ensinados e cria questões
para avaliar o domínio de cada conceito. Escreva tudo em português do Brasil, com acentuação
correta (inclusive nos nomes dos conceitos).
Use LaTeX para fórmulas, delimitado por $...$ (inline) ou $$...$$ (bloco).
Baseie-se apenas no conteúdo do PDF.`;

const NOMES_DIFICULDADE: Record<Dificuldade, string> = {
  facil: "fácil",
  media: "média",
  dificil: "difícil",
};

function montarPrompt(quantidade: number, formato: Formato, dificuldade: Dificuldade): string {
  const regraFormato = {
    multipla_escolha: `Todas as ${quantidade} questões devem ser do tipo "multipla_escolha".`,
    aberta: `Todas as ${quantidade} questões devem ser do tipo "aberta".`,
    mista: quantidade === 1
      ? `A questão pode ser "multipla_escolha" ou "aberta".`
      : `Misture os tipos: pelo menos uma "multipla_escolha" e pelo menos uma "aberta".`,
  }[formato];

  return `Gere uma atividade a partir do PDF anexado.

CONCEITOS
- Identifique de 3 a 8 conceitos centrais do material, com nomes curtos e únicos.
- Em "prerequisitos", liste os nomes (exatamente iguais) dos outros conceitos desta lista que
  precisam ser dominados antes. O grafo de pré-requisitos não pode ter ciclos. Use [] se não houver.

QUESTÕES
- Gere exatamente ${quantidade} questão(ões) de dificuldade ${NOMES_DIFICULDADE[dificuldade]}.
- ${regraFormato}
- Em "conceitos", liste pelo menos 1 nome (exatamente igual) da lista de conceitos acima.
- Em "pagina_origem", informe a página do PDF (começando em 1) em que o assunto aparece.
- "multipla_escolha": exatamente 4 alternativas, exatamente 1 com "correta": true; cada alternativa
  tem um "feedback" curto explicando por que está certa ou errada. Use "rubrica": null.
- "aberta": "rubrica" com os critérios de correção e a resposta esperada. Use "alternativas": null.`;
}

const SCHEMA_ATIVIDADE = {
  type: "OBJECT",
  properties: {
    conceitos: {
      type: "ARRAY",
      minItems: 3,
      maxItems: 8,
      items: {
        type: "OBJECT",
        properties: {
          nome: { type: "STRING" },
          prerequisitos: { type: "ARRAY", items: { type: "STRING" } },
        },
        required: ["nome", "prerequisitos"],
        propertyOrdering: ["nome", "prerequisitos"],
      },
    },
    questoes: {
      type: "ARRAY",
      minItems: 1,
      maxItems: 10,
      items: {
        type: "OBJECT",
        properties: {
          tipo: { type: "STRING", enum: ["multipla_escolha", "aberta"] },
          enunciado: { type: "STRING" },
          conceitos: { type: "ARRAY", minItems: 1, items: { type: "STRING" } },
          pagina_origem: { type: "INTEGER", nullable: true },
          alternativas: {
            type: "ARRAY",
            nullable: true,
            items: {
              type: "OBJECT",
              properties: {
                texto: { type: "STRING" },
                correta: { type: "BOOLEAN" },
                feedback: { type: "STRING" },
              },
              required: ["texto", "correta", "feedback"],
              propertyOrdering: ["texto", "correta", "feedback"],
            },
          },
          rubrica: { type: "STRING", nullable: true },
        },
        required: ["tipo", "enunciado", "conceitos", "pagina_origem", "alternativas", "rubrica"],
        propertyOrdering: ["tipo", "enunciado", "conceitos", "pagina_origem", "alternativas", "rubrica"],
      },
    },
  },
  required: ["conceitos", "questoes"],
  propertyOrdering: ["conceitos", "questoes"],
};

// ---------- validação ----------

function validarAtividade(a: Atividade, quantidade: number, formato: Formato): string[] {
  const problemas: string[] = [];
  if (!Array.isArray(a?.conceitos) || !Array.isArray(a?.questoes)) {
    return ["faltam as listas de conceitos ou de questões"];
  }

  // Conceitos: 3 a 8, nomes únicos, pré-requisitos existentes e sem ciclos.
  const nomes = new Set<string>();
  for (const c of a.conceitos) {
    const nome = c.nome;
    if (!nome?.trim()) problemas.push("conceito sem nome");
    else if (nomes.has(nome)) problemas.push(`conceito repetido "${nome}"`);
    else nomes.add(nome);
  }
  if (a.conceitos.length < 3 || a.conceitos.length > 8) {
    problemas.push(`${a.conceitos.length} conceitos (esperado: 3 a 8)`);
  }
  for (const c of a.conceitos) {
    for (const p of c.prerequisitos ?? []) {
      if (p === c.nome) problemas.push(`"${c.nome}" é pré-requisito de si mesmo`);
      else if (!nomes.has(p)) problemas.push(`pré-requisito desconhecido "${p}" em "${c.nome}"`);
    }
  }
  const ciclo = encontrarCiclo(a.conceitos);
  if (ciclo) problemas.push(`ciclo de pré-requisitos: ${ciclo.join(" → ")}`);

  // Questões.
  if (a.questoes.length !== quantidade) {
    problemas.push(`${a.questoes.length} questões (pedido: ${quantidade})`);
  }
  a.questoes.forEach((q, i) => {
    const n = `questão ${i + 1}`;
    if (!q.enunciado?.trim()) problemas.push(`${n} sem enunciado`);
    // RN07: toda questão ligada a pelo menos um conceito existente.
    if (!q.conceitos?.length) problemas.push(`${n} sem conceito (RN07)`);
    for (const c of q.conceitos ?? []) {
      if (!nomes.has(c)) problemas.push(`${n} usa conceito desconhecido "${c}"`);
    }
    if (formato !== "mista" && q.tipo !== formato) problemas.push(`${n} é "${q.tipo}" (pedido: ${formato})`);

    if (q.tipo === "multipla_escolha") {
      const alts = q.alternativas ?? [];
      if (alts.length !== 4) problemas.push(`${n} tem ${alts.length} alternativas (esperado: 4)`);
      const corretas = alts.filter((x) => x.correta === true).length;
      if (corretas !== 1) problemas.push(`${n} tem ${corretas} alternativas corretas (esperado: 1)`);
      if (alts.some((x) => !x.texto?.trim() || !x.feedback?.trim())) {
        problemas.push(`${n} tem alternativa sem texto ou sem feedback`);
      }
    } else if (q.tipo === "aberta") {
      if (!q.rubrica?.trim()) problemas.push(`${n} (aberta) sem rubrica`);
    } else {
      problemas.push(`${n} com tipo inválido "${q.tipo}"`);
    }
  });
  if (formato === "mista" && quantidade >= 2) {
    const tipos = new Set(a.questoes.map((q) => q.tipo));
    if (tipos.size < 2) problemas.push('formato "mista" pede os dois tipos de questão');
  }

  return problemas;
}

/** Formato único para o app: aberta sem alternativas, múltipla escolha sem rubrica (sempre null). */
function normalizarQuestao(q: Questao): Questao {
  return q.tipo === "aberta" ? { ...q, alternativas: null } : { ...q, rubrica: null };
}

/** Devolve um ciclo do grafo de pré-requisitos (lista de nomes), ou null se não houver. */
function encontrarCiclo(conceitos: Conceito[]): string[] | null {
  const arestas = new Map(conceitos.map((c) => [c.nome, c.prerequisitos ?? []]));
  const estado = new Map<string, "visitando" | "feito">();
  const pilha: string[] = [];

  const visitar = (nome: string): string[] | null => {
    if (estado.get(nome) === "feito" || !arestas.has(nome)) return null;
    if (estado.get(nome) === "visitando") return [...pilha.slice(pilha.indexOf(nome)), nome];
    estado.set(nome, "visitando");
    pilha.push(nome);
    for (const p of arestas.get(nome)!) {
      const ciclo = visitar(p);
      if (ciclo) return ciclo;
    }
    pilha.pop();
    estado.set(nome, "feito");
    return null;
  };

  for (const nome of arestas.keys()) {
    const ciclo = visitar(nome);
    if (ciclo) return ciclo;
  }
  return null;
}

// ---------- respostas ----------

function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

function erro(mensagem: string, status: number): Response {
  return json({ erro: mensagem }, status);
}
