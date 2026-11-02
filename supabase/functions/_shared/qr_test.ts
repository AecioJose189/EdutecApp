// Rodar: npx deno test supabase/functions/_shared/qr_test.ts
import { assert, assertEquals } from "jsr:@std/assert@1";
import { assinaturaValida, gerarToken, janelaAtual, lerToken, tokenRecente } from "./qr.ts";

const SESSAO = "3f2a9c1e-7b4d-4e8f-9a01-23456789abcd";
const SEGREDO = "ab".repeat(32);

Deno.test("token lido de volta tem a mesma sessão e janela, e assinatura válida", async () => {
  const token = await gerarToken(SESSAO, SEGREDO);
  const lido = lerToken(token)!;
  assertEquals(lido.sessaoId, SESSAO);
  assertEquals(lido.janela, janelaAtual());
  assert(await assinaturaValida(lido, SEGREDO));
  assert(tokenRecente(lido));
  assert(token.length < 60, `curto para o QR (${token.length})`);
});

Deno.test("outro segredo ou janela adulterada não validam", async () => {
  const token = await gerarToken(SESSAO, SEGREDO);
  assert(!(await assinaturaValida(lerToken(token)!, "cd".repeat(32))));
  const [id, janela, assinatura] = token.split(".");
  const adulterado = lerToken(`${id}.${(parseInt(janela, 36) + 1).toString(36)}.${assinatura}`)!;
  assert(!(await assinaturaValida(adulterado, SEGREDO)));
});

Deno.test("janela de dois períodos atrás não é recente; lixo não é token", async () => {
  const velho = lerToken(await gerarToken(SESSAO, SEGREDO, janelaAtual() - 2))!;
  assert(await assinaturaValida(velho, SEGREDO));
  assert(!tokenRecente(velho));
  for (const lixo of ["", "abc", "a.b.c", null, 42, `${"A".repeat(22)}.zz.!!`]) assertEquals(lerToken(lixo), null);
});
