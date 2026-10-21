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
// A Edge Function tem 150 s de duração; deixa folga para o resto do pedido.
const TEMPO_LIMITE_MS = 120_000;

/** Erro com status HTTP sugerido para devolver ao cliente. */
export class ErroGemini extends Error {
  constructor(message: string, readonly status: number) {
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

export function nivelRaciocinioAtual(): string {
  const nivel = Deno.env.get("GEMINI_THINKING_LEVEL")?.trim().toLowerCase() || NIVEL_RACIOCINIO_PADRAO;
  if (!NIVEIS_RACIOCINIO.includes(nivel)) {
    throw new ErroGemini(
      `GEMINI_THINKING_LEVEL inválido: "${nivel}" (use ${NIVEIS_RACIOCINIO.join(", ")}).`,
      500,
    );
  }
  return nivel;
}

/** Envia um PDF (inline, base64) e devolve a resposta já interpretada como JSON. */
export async function gerarJsonComPdf<T>(pedido: PedidoJsonComPdf): Promise<RespostaJson<T>> {
  const chave = Deno.env.get("GEMINI_API_KEY");
  if (!chave) {
    throw new ErroGemini("GEMINI_API_KEY não está configurada nos secrets da função.", 500);
  }
  const modelo = modeloAtual();
  const nivelRaciocinio = nivelRaciocinioAtual();

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
      signal: AbortSignal.timeout(TEMPO_LIMITE_MS),
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "TimeoutError") {
      throw new ErroGemini(`O Gemini não respondeu em ${TEMPO_LIMITE_MS / 1000} s.`, 504);
    }
    throw new ErroGemini(`Falha de rede ao chamar o Gemini: ${(e as Error).message}`, 502);
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
      "Cota do Gemini esgotada (plano gratuito: poucos pedidos por minuto e por dia). " +
        `Aguarde ${espera ? `${espera} s` : "cerca de 1 minuto"} e tente de novo.${sufixo}`,
      429,
    );
  }
  if (resposta.status >= 500) {
    return new ErroGemini(
      `O Gemini está indisponível no momento (HTTP ${resposta.status}). Tente de novo em instantes.${sufixo}`,
      503,
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

function paraBase64(bytes: Uint8Array): string {
  // Usa o codificador nativo quando o runtime tiver (mais barato em CPU para PDFs grandes).
  const nativo = (bytes as Uint8Array & { toBase64?: () => string }).toBase64;
  return typeof nativo === "function" ? nativo.call(bytes) : encodeBase64(bytes);
}
