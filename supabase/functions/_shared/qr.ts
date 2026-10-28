// Token do QR da sessão: muda a cada janela de 20 s e só o servidor sabe gerar.
//
// Formato: <sessao_id em base64url>.<janela em base36>.<HMAC-SHA256 truncado em base64url>
// HMAC de "<sessao_id>:<janela>" com sessoes.segredo (que nenhum cliente lê). A janela vai no token
// para o servidor conferir a assinatura com uma conta só e saber se o QR ainda vale:
// vale a janela atual e a anterior (até ~40 s), para dar tempo de ler o QR e abrir o site.

export const JANELA_SEGUNDOS = 20;

export function janelaAtual(agoraMs = Date.now()): number {
  return Math.floor(agoraMs / 1000 / JANELA_SEGUNDOS);
}

/** Fim da janela (quando o desktop deve pedir o próximo token). */
export function fimDaJanela(janela: number): Date {
  return new Date((janela + 1) * JANELA_SEGUNDOS * 1000);
}

export async function gerarToken(sessaoId: string, segredoHex: string, janela = janelaAtual()): Promise<string> {
  const id = base64url(uuidParaBytes(sessaoId));
  return `${id}.${janela.toString(36)}.${base64url(await assinar(sessaoId, janela, segredoHex))}`;
}

export interface TokenLido {
  sessaoId: string;
  janela: number;
  assinatura: Uint8Array;
}

/** Separa as partes do token; null se o formato não bater (o site trata como QR expirado). */
export function lerToken(token: unknown): TokenLido | null {
  if (typeof token !== "string") return null;
  const partes = token.trim().split(".");
  if (partes.length !== 3 || !/^[0-9a-z]{1,10}$/.test(partes[1])) return null;
  try {
    const id = deBase64url(partes[0]);
    const assinatura = deBase64url(partes[2]);
    if (id.length !== 16 || assinatura.length !== TAMANHO_ASSINATURA) return null;
    return { sessaoId: bytesParaUuid(id), janela: parseInt(partes[1], 36), assinatura };
  } catch {
    return null;
  }
}

export async function assinaturaValida(lido: TokenLido, segredoHex: string): Promise<boolean> {
  const esperada = await assinar(lido.sessaoId, lido.janela, segredoHex);
  let diferenca = 0; // comparação em tempo constante
  for (let i = 0; i < esperada.length; i++) diferenca |= esperada[i] ^ lido.assinatura[i];
  return diferenca === 0;
}

/** A janela do token é a atual ou a anterior (a próxima também, por diferença de relógio). */
export function tokenRecente(lido: TokenLido): boolean {
  const atual = janelaAtual();
  return lido.janela >= atual - 1 && lido.janela <= atual + 1;
}

// ---------- internos ----------

const TAMANHO_ASSINATURA = 12; // 96 bits: curto para o QR, sem força bruta viável em 40 s

async function assinar(sessaoId: string, janela: number, segredoHex: string): Promise<Uint8Array> {
  const chave = await crypto.subtle.importKey(
    "raw", hexParaBytes(segredoHex), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const mensagem = new TextEncoder().encode(`${sessaoId.toLowerCase()}:${janela}`);
  return new Uint8Array(await crypto.subtle.sign("HMAC", chave, mensagem)).slice(0, TAMANHO_ASSINATURA);
}

function hexParaBytes(hex: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(hex.match(/../g)!.map((b) => parseInt(b, 16)));
}

function uuidParaBytes(uuid: string): Uint8Array {
  return hexParaBytes(uuid.replace(/-/g, ""));
}

function bytesParaUuid(b: Uint8Array): string {
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function base64url(b: Uint8Array): string {
  return btoa(String.fromCharCode(...b)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function deBase64url(s: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw new Error("base64url inválido");
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}
