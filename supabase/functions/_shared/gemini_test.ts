// Rodar: npx deno test -A supabase/functions/_shared/gemini_test.ts
// gerarComReserva com o fetch trocado por respostas falsas (nenhuma chamada real ao Gemini).
import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1";
import { ErroGemini, gerarComReserva } from "./gemini.ts";

Deno.env.set("GEMINI_API_KEY", "teste");
Deno.env.set("GEMINI_MODEL", "principal");
Deno.env.set("GEMINI_MODELO_RESERVA", "reserva");

const PEDIDO = { pdf: new Uint8Array([1, 2, 3]), instrucaoSistema: "s", prompt: "p", schema: {} };
const OK = { candidates: [{ finishReason: "STOP", content: { parts: [{ text: '{"ok":true}' }] } }] };

/** Troca o fetch: cada chamada consome a próxima resposta da fila para aquele modelo. */
function simular(respostas: Record<string, (number | "lento")[]>) {
  const chamadas: string[] = [];
  globalThis.fetch = ((url: string, init?: RequestInit) => {
    const modelo = decodeURIComponent(String(url).match(/models\/([^:]+):/)![1]);
    chamadas.push(modelo);
    const proxima = respostas[modelo].shift();
    if (proxima === "lento") {
      // Nunca responde: só o AbortSignal.timeout encerra.
      return new Promise((_, rejeitar) =>
        init!.signal!.addEventListener("abort", () => rejeitar(new DOMException("t", "TimeoutError")))
      );
    }
    const corpo = proxima === 200 ? OK : { error: { message: "high demand" } };
    return Promise.resolve(new Response(JSON.stringify(corpo), { status: proxima }));
  }) as typeof fetch;
  return chamadas;
}

const daqui = (ms: number) => Date.now() + ms;

Deno.test("503 e depois sucesso: repete o principal, sem reserva", async () => {
  const chamadas = simular({ principal: [503, 200], reserva: [] });
  const r = await gerarComReserva(PEDIDO, daqui(140_000));
  assertEquals(chamadas, ["principal", "principal"]);
  assertEquals(r.reserva, false);
  assertEquals(r.tentativas.map((t) => t.resultado), ["503", "ok"]);
});

Deno.test("503 duas vezes: usa o reserva", async () => {
  const chamadas = simular({ principal: [503, 503], reserva: [200] });
  const r = await gerarComReserva(PEDIDO, daqui(140_000));
  assertEquals(chamadas, ["principal", "principal", "reserva"]);
  assertEquals(r.reserva, true);
});

Deno.test("429 em todos: erro de cota (429), transitório", async () => {
  simular({ principal: [429, 429], reserva: [429] });
  const e = await assertRejects(() => gerarComReserva(PEDIDO, daqui(140_000)), ErroGemini);
  assertEquals(e.status, 429);
  assert(e.transitorio);
});

Deno.test("principal estoura o tempo: não repete, vai direto ao reserva dentro do prazo", async () => {
  const chamadas = simular({ principal: ["lento"], reserva: [200] });
  // Prazo curto: 20 s para o principal (73 − 50 − 3) e o resto para o reserva.
  const inicio = Date.now();
  const r = await gerarComReserva(PEDIDO, daqui(73_000));
  assertEquals(chamadas, ["principal", "reserva"]);
  assertEquals(r.tentativas.map((t) => t.resultado), ["504", "ok"]);
  assert(Date.now() - inicio < 73_000);
});

Deno.test("sem tempo para nada: erro claro (503) sem chamar a IA", async () => {
  const chamadas = simular({ principal: [], reserva: [] });
  const e = await assertRejects(() => gerarComReserva(PEDIDO, daqui(10_000)), ErroGemini);
  assertEquals(e.status, 503);
  assertEquals(chamadas, []);
  assert(e.message.includes("sobrecarregada"));
});

Deno.test("erro que não é transitório (chave recusada) sai na hora", async () => {
  const chamadas = simular({ principal: [403], reserva: [200] });
  await assertRejects(() => gerarComReserva(PEDIDO, daqui(140_000)), ErroGemini, "recusou a chave");
  assertEquals(chamadas, ["principal"]);
});

Deno.test("GEMINI_FORCAR_RESERVA pula o principal", async () => {
  Deno.env.set("GEMINI_FORCAR_RESERVA", "true");
  try {
    const chamadas = simular({ principal: [200], reserva: [200] });
    const r = await gerarComReserva(PEDIDO, daqui(140_000));
    assertEquals(chamadas, ["reserva"]);
    assertEquals(r.reserva, true);
  } finally {
    Deno.env.delete("GEMINI_FORCAR_RESERVA");
  }
});
