// Testes das partes puras do reforço: npx deno test -A supabase/functions/_shared/reforco_test.ts
import { assert, assertEquals, assertNotEquals, assertThrows } from "jsr:@std/assert@1";
import { blocoReforco, chaveFoco, lerConceitosFoco, normalizarConceito, validarFoco } from "./reforco.ts";

Deno.test("normaliza como o Postgres", () => {
  assertEquals(normalizarConceito("  Evento   Complementar "), "evento complementar");
  assertEquals(normalizarConceito("Distribuição Binomial"), "distribuicao binomial");
});

Deno.test("conceitos_foco: ordem mantida, repetidos e vazios fora", () => {
  assertEquals(lerConceitosFoco(null), null);
  assertEquals(lerConceitosFoco(""), null);
  assertEquals(lerConceitosFoco('["Regra da cadeia", " regra da  cadeia", "", "Derivada"]'), ["Regra da cadeia", "Derivada"]);
  assertThrows(() => lerConceitosFoco("Derivada"));
  assertThrows(() => lerConceitosFoco("[1, 2]"));
  assertThrows(() => lerConceitosFoco('[""]'));
  assertThrows(() => lerConceitosFoco(JSON.stringify(Array.from({ length: 9 }, (_, i) => `C${i}`))));
});

Deno.test("chave do cache: depende do foco e dos enunciados, não da ordem nem da grafia", async () => {
  const a = await chaveFoco(["Derivada", "Regra da cadeia"], ["Q1", "Q2"]);
  assertEquals(a, await chaveFoco(["regra da cadeia", "Derivada"], ["Q2", " Q1 "]));
  assertNotEquals(a, await chaveFoco(["Derivada"], ["Q1", "Q2"]));
  assertNotEquals(a, await chaveFoco(["Derivada", "Regra da cadeia"], ["Q1", "Q2", "Q3"]));
  assert(/^[0-9a-f]{64}$/.test(a));
});

Deno.test("bloco do prompt: foco e questões a não repetir", () => {
  const b = blocoReforco(["Regra da cadeia"], ["Derive  sen(x²).", "x".repeat(500)]);
  assert(b.includes('"Regra da cadeia"'));
  assert(b.includes("1. Derive sen(x²)."));
  assert(b.includes("x".repeat(400) + "…") && !b.includes("x".repeat(401)));
  assert(!blocoReforco(["A"], []).includes("JÁ foram aplicadas"));
});

Deno.test("validação: toda questão avalia algum conceito do foco", () => {
  assertEquals(validarFoco([{ conceitos: ["regra da cadeia", "Derivada"] }], ["Regra da Cadeia"]), []);
  assertEquals(validarFoco([{ conceitos: ["Derivada"] }, { conceitos: [] }], ["Regra da cadeia"]),
    ["questão 1 não avalia nenhum dos conceitos escolhidos", "questão 2 não avalia nenhum dos conceitos escolhidos"]);
});
