// Único módulo que fala com o Gemini (CLAUDE.md: provedor de IA isolado).
// Para trocar de provedor, basta reimplementar `gerarJsonComPdf` com a mesma assinatura.
//
// Usa a API REST `generateContent` via fetch, com saída estruturada
// (responseMimeType "application/json" + responseSchema).
// Docs: https://ai.google.dev/api/generate-content
//       https://ai.google.dev/gemini-api/docs/structured-output

import { encodeBase64 } from "jsr:@std/encoding@1/base64";

// Flash estável recomendado, com plano gratuito e entrada de PDF (docs consultadas em 20/10/2026).
// Pode ser trocado sem novo deploy do código: `supabase secrets set GEMINI_MODEL=...`.
const MODELO_PADRAO = "gemini-3.8-flash";
const URL_BASE = "https://generativelanguage.googleapis.com/v1beta";
// Nível de raciocínio (thinkingConfig.thinkingLevel). O gemini-3.8-flash aceita low | medium | high
// (padrão do modelo: medium; "minimal" não é suportado nele). "low" manteve a qualidade das
// questões e reduz o tempo. Configurável por `supabase secrets set GEMINI_THINKING_LEVEL=...`.
// Docs: https://ai.google.dev/gemini-api/docs/thinking
const NIVEL_RACIOCINIO_PADRAO = "low";
const NIVEIS_RACIOCINIO = ["minimal", "low", "medium", "high"];
// Tempo de uma chamada quando ninguém define outro (gerarJsonComPdf avulsa).
const TEMPO_LIMITE_MS = 120_000;

// Modelo RESERVA, usado quando o principal está sobrecarregado (503), sem cota (429) ou lento demais.
// Flash-Lite estável, mais leve, com plano gratuito e entrada de PDF (docs consultadas em 27/10/2026:
// ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite e /pricing). A cota do plano gratuito é
// por modelo, então o reserva costuma ter folga quando o principal esgota.
// Configurável: `supabase secrets set GEMINI_MODELO_RESERVA=...` (e GEMINI_THINKING_LEVEL_RESERVA).
const MODELO_RESERVA_PADRAO = "gemini-3.5-flash-lite";
const NIVEL_RACIOCINIO_RESERVA_PADRAO = "low";

// Orçamento de tempo de gerarComReserva (a Edge Function morre em 150 s).
/** Tempo garantido ao reserva: o principal não pode avançar sobre ele. */
const TEMPO_RESERVA_MS = 50_000;
/** Menos que isso não vale começar uma tentativa (a geração típica leva dezenas de segundos). */
const TEMPO_MINIMO_TENTATIVA_MS = 20_000;
/** Folga depois da IA para validar, salvar e responder. */
const FOLGA_FINAL_MS = 3_000;
/** Espera curta antes de repetir o principal depois de 503/429/falha de rede. */
const ESPERA_REPETICAO_MS = 2_000;

/** Erro com status HTTP sugerido para devolver ao cliente. */
export class ErroGemini extends Error {
  constructor(
    message: string,
    readonly status: number,
    /** Sobrecarga, cota ou tempo: vale tentar de novo ou trocar de modelo. */
    readonly transitorio = false,
  ) {
    super(message);
    this.name = "ErroGemini";
  }
}

export interface PedidoJsonComPdf {
  pdf: Uint8Array;
  instrucaoSistema: string;
  prompt: string;
  /** Schema no formato OpenAPI aceito pelo `responseSchema` do Gemini. */
  schema: Record<string, unknown>;
  temperatura?: number;
}

export interface RespostaJson<T> {
  dados: T;
  modelo: string;
  nivelRaciocinio: string;
  tokens: { entrada: number; saida: number; raciocinio: number; total: number } | null;
}

export function modeloAtual(): string {
  return Deno.env.get("GEMINI_MODEL")?.trim() || MODELO_PADRAO;
}

export function modeloReserva(): string {
  return Deno.env.get("GEMINI_MODELO_RESERVA")?.trim() || MODELO_RESERVA_PADRAO;
}

export function nivelRaciocinioAtual(variavel = "GEMINI_THINKING_LEVEL", padrao = NIVEL_RACIOCINIO_PADRAO): string {
  const nivel = Deno.env.get(variavel)?.trim().toLowerCase() || padrao;
  if (!NIVEIS_RACIOCINIO.includes(nivel)) {
    throw new ErroGemini(`${variavel} inválido: "${nivel}" (use ${NIVEIS_RACIOCINIO.join(", ")}).`, 500);
  }
  return nivel;
}

/** Como chamar: modelo, nível de raciocínio e tempo máximo (padrão: principal e 120 s). */
export interface OpcoesChamada {
  modelo?: string;
  nivelRaciocinio?: string;
  tempoLimiteMs?: number;
}

/** Envia um PDF (inline, base64) e devolve a resposta já interpretada como JSON. */
export async function gerarJsonComPdf<T>(pedido: PedidoJsonComPdf, opcoes: OpcoesChamada = {}): Promise<RespostaJson<T>> {
  const chave = Deno.env.get("GEMINI_API_KEY");
  if (!chave) {
    throw new ErroGemini("GEMINI_API_KEY não está configurada nos secrets da função.", 500);
  }
  const modelo = opcoes.modelo ?? modeloAtual();
  const nivelRaciocinio = opcoes.nivelRaciocinio ?? nivelRaciocinioAtual();
  const tempoLimite = opcoes.tempoLimiteMs ?? TEMPO_LIMITE_MS;

  const corpo = {
    systemInstruction: { parts: [{ text: pedido.instrucaoSistema }] },
    contents: [{
      role: "user",
      parts: [
        { inlineData: { mimeType: "application/pdf", data: paraBase64(pedido.pdf) } },
        { text: pedido.prompt },
      ],
    }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: pedido.schema,
      temperature: pedido.temperatura ?? 0.4,
      thinkingConfig: { thinkingLevel: nivelRaciocinio },
    },
  };

  let resposta: Response;
  try {
    resposta = await fetch(`${URL_BASE}/models/${encodeURIComponent(modelo)}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": chave },
      body: JSON.stringify(corpo),
      signal: AbortSignal.timeout(tempoLimite),
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "TimeoutError") {
      throw new ErroGemini(`O Gemini (${modelo}) não respondeu em ${Math.round(tempoLimite / 1000)} s.`, 504, true);
    }
    throw new ErroGemini(`Falha de rede ao chamar o Gemini: ${(e as Error).message}`, 502, true);
  }

  if (!resposta.ok) {
    throw await erroDeStatus(resposta, modelo);
  }

  // deno-lint-ignore no-explicit-any
  const json: any = await resposta.json();

  const bloqueio = json.promptFeedback?.blockReason;
  if (bloqueio) {
    throw new ErroGemini(`O Gemini recusou o pedido (blockReason: ${bloqueio}).`, 422);
  }
  const candidato = json.candidates?.[0];
  const motivo = candidato?.finishReason;
  if (motivo && motivo !== "STOP") {
    const msg = motivo === "MAX_TOKENS"
      ? "A resposta do Gemini foi cortada por limite de tokens (MAX_TOKENS). Tente menos questões."
      : `O Gemini encerrou a resposta antes do fim (finishReason: ${motivo}).`;
    throw new ErroGemini(msg, 502);
  }
  // Ignora partes de "pensamento" (thought: true), se o modelo as devolver.
  // deno-lint-ignore no-explicit-any
  const texto = (candidato?.content?.parts ?? []).filter((p: any) => !p.thought && p.text)
    // deno-lint-ignore no-explicit-any
    .map((p: any) => p.text).join("");
  if (!texto) {
    throw new ErroGemini("O Gemini devolveu uma resposta vazia.", 502);
  }

  let dados: T;
  try {
    dados = JSON.parse(texto);
  } catch {
    throw new ErroGemini("O Gemini devolveu um JSON inválido.", 502);
  }

  const uso = json.usageMetadata;
  return {
    dados,
    modelo: json.modelVersion ?? modelo,
    nivelRaciocinio,
    tokens: uso
      ? {
        entrada: uso.promptTokenCount ?? 0,
        saida: uso.candidatesTokenCount ?? 0,
        raciocinio: uso.thoughtsTokenCount ?? 0,
        total: uso.totalTokenCount ?? 0,
      }
      : null,
  };
}

async function erroDeStatus(resposta: Response, modelo: string): Promise<ErroGemini> {
  // A mensagem do Google não contém a chave; é útil para o diagnóstico.
  let detalhe = "";
  try {
    detalhe = (await resposta.json())?.error?.message ?? "";
  } catch { /* corpo não é JSON */ }
  const sufixo = detalhe ? ` Detalhe: ${detalhe}` : "";

  if (resposta.status === 429) {
    const espera = resposta.headers.get("retry-after");
    return new ErroGemini(
      `Cota do Gemini (${modelo}) esgotada (plano gratuito: poucos pedidos por minuto e por dia). ` +
        `Aguarde ${espera ? `${espera} s` : "cerca de 1 minuto"} e tente de novo.${sufixo}`,
      429,
      true,
    );
  }
  if (resposta.status >= 500) {
    return new ErroGemini(
      `O Gemini (${modelo}) está indisponível no momento (HTTP ${resposta.status}). Tente de novo em instantes.${sufixo}`,
      503,
      true,
    );
  }
  if (resposta.status === 404) {
    return new ErroGemini(`Modelo "${modelo}" não encontrado no Gemini. Confira GEMINI_MODEL.${sufixo}`, 500);
  }
  if (resposta.status === 401 || resposta.status === 403) {
    return new ErroGemini(`O Gemini recusou a chave de API (HTTP ${resposta.status}).${sufixo}`, 500);
  }
  return new ErroGemini(`Erro do Gemini (HTTP ${resposta.status}).${sufixo}`, 502);
}

// ---------------------------------------------------------------------------
// Principal com uma nova tentativa e modelo reserva, dentro de um prazo
// ---------------------------------------------------------------------------

/** Uma chamada ao Gemini dentro de gerarComReserva (vai para o log e para a resposta). */
export interface Tentativa {
  modelo: string;
  ms: number;
  /** "ok", "503", "429", "504" (tempo), "502" (rede) ou "pulada" (sem tempo para tentar). */
  resultado: string;
}

export interface RespostaComReserva<T> extends RespostaJson<T> {
  /** true quando quem gerou foi o modelo reserva. */
  reserva: boolean;
  tentativas: Tentativa[];
}

/**
 * Gera com o modelo principal; em sobrecarga (503), cota (429), falha de rede ou tempo esgotado,
 * repete o principal uma vez depois de uma espera curta e, se falhar de novo, usa o modelo reserva.
 * Tudo termina antes de [prazo] (epoch ms): o principal nunca avança sobre os [TEMPO_RESERVA_MS] do
 * reserva, e uma tentativa sem pelo menos [TEMPO_MINIMO_TENTATIVA_MS] nem começa. Sem tempo ou sem
 * sucesso, lança um ErroGemini claro (503, ou 429 se todas foram por cota) em vez de estourar a função.
 * Erros que não são transitórios (chave, modelo inexistente, bloqueio, JSON inválido) saem na hora.
 * GEMINI_FORCAR_RESERVA=true pula o principal (para testar o reserva).
 */
export async function gerarComReserva<T>(pedido: PedidoJsonComPdf, prazo: number): Promise<RespostaComReserva<T>> {
  const principal = modeloAtual();
  const reserva = modeloReserva();
  const forcarReserva = Deno.env.get("GEMINI_FORCAR_RESERVA")?.trim().toLowerCase() === "true";
  const plano: { modelo: string; ehReserva: boolean }[] = forcarReserva
    ? [{ modelo: reserva, ehReserva: true }]
    : [
      { modelo: principal, ehReserva: false },
      { modelo: principal, ehReserva: false },
      ...(reserva !== principal ? [{ modelo: reserva, ehReserva: true }] : []),
    ];
  const nivelPrincipal = nivelRaciocinioAtual();
  const nivelReserva = nivelRaciocinioAtual("GEMINI_THINKING_LEVEL_RESERVA", NIVEL_RACIOCINIO_RESERVA_PADRAO);

  const tentativas: Tentativa[] = [];
  const erros: ErroGemini[] = [];
  for (const [i, passo] of plano.entries()) {
    const repeticao = i > 0 && plano[i - 1].modelo === passo.modelo;
    if (repeticao) {
      const ultimo = tentativas[tentativas.length - 1];
      // Depois de esgotar o tempo, repetir o mesmo modelo não cabe: vai direto para o reserva.
      if (ultimo?.resultado === "504" || ultimo?.resultado === "pulada") continue;
    }
    const guardarParaReserva = !passo.ehReserva && plano.some((p) => p.ehReserva) ? TEMPO_RESERVA_MS : 0;
    const espera = repeticao ? ESPERA_REPETICAO_MS : 0;
    const tempo = prazo - Date.now() - espera - guardarParaReserva - FOLGA_FINAL_MS;
    if (tempo < TEMPO_MINIMO_TENTATIVA_MS) {
      tentativas.push({ modelo: passo.modelo, ms: 0, resultado: "pulada" });
      continue;
    }
    if (espera) await new Promise((r) => setTimeout(r, espera));

    const inicio = Date.now();
    try {
      const resposta = await gerarJsonComPdf<T>(pedido, {
        modelo: passo.modelo,
        nivelRaciocinio: passo.ehReserva ? nivelReserva : nivelPrincipal,
        tempoLimiteMs: tempo,
      });
      tentativas.push({ modelo: passo.modelo, ms: Date.now() - inicio, resultado: "ok" });
      return { ...resposta, reserva: passo.ehReserva, tentativas };
    } catch (e) {
      if (!(e instanceof ErroGemini) || !e.transitorio) throw e;
      tentativas.push({ modelo: passo.modelo, ms: Date.now() - inicio, resultado: String(e.status) });
      erros.push(e);
      console.error(`Gemini falhou (${passo.modelo}, tentativa ${i + 1}): ${e.message}`);
    }
  }

  const resumo = tentativas.map((t) => `${t.modelo}: ${t.resultado}`).join("; ");
  const soCota = erros.length > 0 && erros.every((e) => e.status === 429);
  throw new ErroGemini(
    soCota
      ? `A cota gratuita da IA acabou por agora (${resumo}). Aguarde cerca de 1 minuto e tente de novo.`
      : `A IA está sobrecarregada agora e não respondeu a tempo (${resumo}). ` +
        "Tente de novo em instantes ou gere menos questões.",
    soCota ? 429 : 503,
    true,
  );
}

function paraBase64(bytes: Uint8Array): string {
  // Usa o codificador nativo quando o runtime tiver (mais barato em CPU para PDFs grandes).
  const nativo = (bytes as Uint8Array & { toBase64?: () => string }).toBase64;
  return typeof nativo === "function" ? nativo.call(bytes) : encodeBase64(bytes);
}
