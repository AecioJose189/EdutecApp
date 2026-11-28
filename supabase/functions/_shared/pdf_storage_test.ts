// Rodar: npx deno test -A supabase/functions/_shared/pdf_storage_test.ts
// salvarPdf/garantirPdf com o fetch trocado por respostas falsas do Storage (nada vai à rede).
import { assert, assertEquals } from "jsr:@std/assert@1";
import { createClient } from "npm:@supabase/supabase-js@2";
import { garantirPdf, salvarPdf } from "./pdf_storage.ts";

const PDF = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d]);
const CAMINHO = "pdfs/abc.pdf";
type Resp = number | "rede";

/** Cada chamada consome a próxima resposta da fila do método (POST = envio, HEAD = existe?). */
function simular(filas: { POST?: Resp[]; HEAD?: Resp[] }) {
  const chamadas: string[] = [];
  globalThis.fetch = ((entrada: string | Request, init?: RequestInit) => {
    const metodo = (init?.method ?? (entrada instanceof Request ? entrada.method : "GET")) as "POST" | "HEAD";
    chamadas.push(metodo);
    const proxima = filas[metodo]?.shift();
    if (proxima === undefined) throw new Error(`chamada inesperada: ${metodo}`);
    if (proxima === "rede") return Promise.reject(new TypeError("falha de rede"));
    if (metodo === "HEAD") return Promise.resolve(new Response(null, { status: proxima }));
    const corpo = proxima === 200
      ? { Key: `materiais/${CAMINHO}`, Id: "1" }
      : proxima === 409
      ? { statusCode: "409", error: "Duplicate", message: "The resource already exists" }
      : { statusCode: String(proxima), error: "erro", message: "Web server is returning an unknown error" };
    // O Storage real devolve "já existe" como HTTP 400 com statusCode "409" no corpo.
    const http = proxima === 409 ? 400 : proxima;
    return Promise.resolve(
      new Response(JSON.stringify(corpo), { status: http, headers: { "content-type": "application/json" } }),
    );
  }) as typeof fetch;
  return chamadas;
}

const cliente = () => createClient("http://storage.teste", "chave", { auth: { persistSession: false } });

Deno.test("envio ok: salvo de primeira, sem conferir nem repetir", async () => {
  const chamadas = simular({ POST: [200] });
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals(r.como, "enviado");
  assertEquals(chamadas, ["POST"]);
});

Deno.test("já existe (409): conta como salvo", async () => {
  const chamadas = simular({ POST: [409] });
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals([r.salvo, r.como], [true, "ja_existia"]);
  assertEquals(chamadas, ["POST"]);
});

Deno.test("520 com o PDF já no Storage: ignora o erro, não reenvia", async () => {
  const chamadas = simular({ POST: [520], HEAD: [200] });
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals([r.salvo, r.como], [true, "ja_existia"]);
  assertEquals(chamadas, ["POST", "HEAD"]);
});

Deno.test("520 e o PDF não existe: espera e reenvia uma vez", async () => {
  const chamadas = simular({ POST: [520, 200], HEAD: [404] });
  const inicio = Date.now();
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 150);
  assert(Date.now() - inicio >= 150, "esperou antes de reenviar");
  assertEquals([r.salvo, r.como], [true, "reenviado"]);
  assertEquals(chamadas, ["POST", "HEAD", "POST"]);
});

Deno.test("não deu para conferir (HEAD 520): reenvia mesmo assim", async () => {
  const chamadas = simular({ POST: [520, 409], HEAD: [520] });
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals([r.salvo, r.como], [true, "ja_existia"]);
  assertEquals(chamadas, ["POST", "HEAD", "POST"]);
});

Deno.test("falha duas vezes: não lança, devolve salvo = false com os dois erros", async () => {
  const chamadas = simular({ POST: [520, "rede"], HEAD: [404] });
  const r = await salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals([r.salvo, r.como], [false, "falhou"]);
  assertEquals(r.erros.length, 2);
  assertEquals(chamadas, ["POST", "HEAD", "POST"]);
});

Deno.test("geração em paralelo sobrevive à falha do Storage", async () => {
  simular({ POST: [520, 520], HEAD: [404] });
  const ia = new Promise<string>((r) => setTimeout(() => r("atividade"), 10));
  const [pdf, resposta] = await Promise.all([salvarPdf(cliente(), "materiais", CAMINHO, PDF, 0), ia]);
  assertEquals(resposta, "atividade");
  assertEquals(pdf.salvo, false);
});

Deno.test("garantirPdf: PDF já no Storage → só confere (HEAD), não envia", async () => {
  const chamadas = simular({ HEAD: [200] });
  assertEquals(await garantirPdf(cliente(), "materiais", CAMINHO, PDF, 0), null);
  assertEquals(chamadas, ["HEAD"]);
});

Deno.test("garantirPdf: PDF faltando (cache sem PDF) → envia agora", async () => {
  const chamadas = simular({ HEAD: [404], POST: [200] });
  const r = await garantirPdf(cliente(), "materiais", CAMINHO, PDF, 0);
  assertEquals(r?.como, "enviado");
  assertEquals(chamadas, ["HEAD", "POST"]);
});
