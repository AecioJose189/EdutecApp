// Guarda o PDF de uma geração no Storage (pdfs/<hash>.pdf) sem derrubar o pedido.
//
// O envio corre junto com a chamada à IA: se ele lançasse erro, a geração já paga seria jogada fora
// por uma falha passageira do Storage (ex.: 520). Por isso salvarPdf nunca lança:
//   1. envia (upsert: false); "já existe" (409) conta como salvo;
//   2. outra falha → confere se o arquivo já está lá (mesmo hash = mesmo conteúdo): se está, salvo;
//   3. não está (ou não deu para conferir) → espera 2 s e envia de novo, uma vez;
//   4. ainda falhou → registra no log e devolve salvo = false. A geração segue para o professor;
//      o cache (geracoes) fica sem o PDF, e um reforço dessa geração pede o PDF de novo
//      (baixarPdf → 409 com mensagem). Uma nova geração comum com o mesmo PDF o salva (garantirPdf).
// Testes: npx deno test -A supabase/functions/_shared/pdf_storage_test.ts (fetch simulado).

import type { SupabaseClient } from "npm:@supabase/supabase-js@2";

export const ESPERA_REENVIO_MS = 2_000;

export interface ResultadoPdf {
  salvo: boolean;
  /** "enviado" | "ja_existia" | "reenviado" | "falhou" */
  como: "enviado" | "ja_existia" | "reenviado" | "falhou";
  erros: string[];
}

type Envio = "ok" | "ja_existe" | string; // string = mensagem do erro

async function enviar(supabase: SupabaseClient, bucket: string, caminho: string, pdf: Uint8Array): Promise<Envio> {
  try {
    const { error } = await supabase.storage.from(bucket).upload(caminho, pdf, {
      contentType: "application/pdf",
      upsert: false,
    });
    if (!error) return "ok";
    const status = String((error as { statusCode?: string | number }).statusCode ?? "");
    if (status === "409" || /already exists|duplicate/i.test(error.message)) return "ja_existe";
    return `${status || "?"} ${error.message}`;
  } catch (e) {
    return `exceção: ${(e as Error).message}`;
  }
}

/** true = está lá; false = não está; null = não deu para saber. */
export async function pdfExiste(supabase: SupabaseClient, bucket: string, caminho: string): Promise<boolean | null> {
  try {
    const { data, error } = await supabase.storage.from(bucket).exists(caminho);
    if (error) {
      const status = String((error as { statusCode?: string | number; status?: number }).statusCode ??
        (error as { status?: number }).status ?? "");
      return status === "404" || status === "400" ? false : null;
    }
    return data === true;
  } catch {
    return null;
  }
}

export async function salvarPdf(
  supabase: SupabaseClient,
  bucket: string,
  caminho: string,
  pdf: Uint8Array,
  esperaMs = ESPERA_REENVIO_MS,
): Promise<ResultadoPdf> {
  const erros: string[] = [];
  const primeiro = await enviar(supabase, bucket, caminho, pdf);
  if (primeiro === "ok") return { salvo: true, como: "enviado", erros };
  if (primeiro === "ja_existe") return { salvo: true, como: "ja_existia", erros };
  erros.push(`envio: ${primeiro}`);

  const existe = await pdfExiste(supabase, bucket, caminho);
  if (existe === true) {
    console.warn(`PDF ${caminho}: envio falhou (${primeiro}), mas o arquivo já está no Storage.`);
    return { salvo: true, como: "ja_existia", erros };
  }

  await new Promise((r) => setTimeout(r, esperaMs));
  const segundo = await enviar(supabase, bucket, caminho, pdf);
  if (segundo === "ok" || segundo === "ja_existe") {
    console.warn(`PDF ${caminho}: salvo na 2ª tentativa (1ª: ${primeiro}).`);
    return { salvo: true, como: segundo === "ok" ? "reenviado" : "ja_existia", erros };
  }
  erros.push(`reenvio: ${segundo}`);
  console.error(
    `PDF ${caminho} NÃO foi salvo no Storage (${erros.join("; ")}). A geração segue; ` +
      "um reforço a partir dela vai pedir o PDF de novo.",
  );
  return { salvo: false, como: "falhou", erros };
}

/**
 * Geração comum que veio do cache (RN12): se o PDF ficou de fora numa geração anterior, aproveita o
 * envio do professor para guardá-lo agora. Conferir é um HEAD (barato); só envia se faltar.
 */
export async function garantirPdf(
  supabase: SupabaseClient,
  bucket: string,
  caminho: string,
  pdf: Uint8Array,
  esperaMs = ESPERA_REENVIO_MS,
): Promise<ResultadoPdf | null> {
  if (await pdfExiste(supabase, bucket, caminho) === true) return null;
  return await salvarPdf(supabase, bucket, caminho, pdf, esperaMs);
}
