// Checagem da chave pública do projeto, comum às Edge Functions com verify_jwt = false.
//
// A chave publishable (sb_publishable_...) não é JWT, então o verify_jwt da plataforma a rejeitaria.
// Cada função confere aqui se o cabeçalho `apikey` (ou o Bearer) é uma chave pública do projeto.
// Não é autenticação de usuário: qualquer um com o APK tem a chave. Login fica para depois.

export function chavePublicaValida(req: Request): boolean {
  const enviada = req.headers.get("apikey") ??
    req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!enviada) return false;

  const aceitas = new Set<string>();
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  if (anon) aceitas.add(anon);
  try {
    // JSON no formato { "default": "sb_publishable_..." }
    const publicas = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    for (const v of Object.values(publicas)) if (typeof v === "string") aceitas.add(v);
  } catch { /* variável ausente ou malformada: fica só a anon */ }
  return aceitas.has(enviada);
}
