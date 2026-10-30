// Faz o papel do(s) aluno(s) enquanto o site do aluno não está pronto: aceita o convite da turma,
// "lê" o QR projetado pelo desktop e envia respostas pela aluno-api (como o site fará).
//
// Uso (na raiz do repositório):
//   export SUPABASE_CHAVE_SECRETA=...   # ver o cabeçalho de scripts/testar-fluxo-mvp.ts
//   npx deno run -A scripts/simular-aluno.ts <convite> <qr> [alunos] [alternativa]
//     convite      código (K7QF2M) ou link .../convite/K7QF2M (botão "Convidar alunos")
//     qr           URL do QR (.../s/<token>) ou só o token; o token vale ~40 s: rode logo depois de copiar
//     alunos       quantos alunos de teste (padrão 1): aluno.teste1@..., aluno.teste2@...
//     alternativa  a, b, c... para todas as questões, ou "sorteio" (padrão)
//
// Os alunos de teste são contas criadas pela Admin API (chave secreta só no ambiente) com e-mail
// aluno.testeN@lumini-teste.example.com. O professor precisa estar logado no desktop com outra conta.

import { createClient } from "npm:@supabase/supabase-js@2";

const raiz = new URL("..", import.meta.url).pathname;

function propriedade(nome: string): string {
  const doAmbiente = Deno.env.get(nome)?.trim();
  if (doAmbiente) return doAmbiente;
  try {
    const linha = Deno.readTextFileSync(`${raiz}local.properties`).split("\n").find((l) => l.startsWith(`${nome}=`));
    return linha?.slice(nome.length + 1).trim() ?? "";
  } catch {
    return "";
  }
}

const URL_SUPABASE = propriedade("SUPABASE_URL");
const CHAVE_PUBLICA = propriedade("SUPABASE_CHAVE_PUBLICA");
const CHAVE_SECRETA = Deno.env.get("SUPABASE_CHAVE_SECRETA") ?? "";
const [conviteArg, qrArg, alunosArg = "1", alternativaArg = "sorteio"] = Deno.args;
if (!URL_SUPABASE || !CHAVE_PUBLICA || !CHAVE_SECRETA || !conviteArg || !qrArg) {
  console.error("Uso: simular-aluno.ts <convite> <qr> [alunos] [alternativa]  (com SUPABASE_CHAVE_SECRETA no ambiente)");
  Deno.exit(2);
}
const codigo = conviteArg.split("/").pop()!.trim();
const token = qrArg.split("/s/").pop()!.trim();

const admin = createClient(URL_SUPABASE, CHAVE_SECRETA, { auth: { persistSession: false } });

async function entrarComo(email: string, nome: string): Promise<string> {
  const senha = `${crypto.randomUUID()}Aa1!`;
  const criado = await admin.auth.admin.createUser({ email, password: senha, email_confirm: true, user_metadata: { full_name: nome } });
  if (!criado.data.user) {
    const { data } = await admin.auth.admin.listUsers({ perPage: 1000 });
    const id = data.users.find((u) => u.email === email)?.id;
    if (!id) throw new Error(`Não foi possível criar ${email}: ${criado.error?.message}`);
    await admin.auth.admin.updateUserById(id, { password: senha });
  }
  const cliente = createClient(URL_SUPABASE, CHAVE_PUBLICA, { auth: { persistSession: false } });
  const { data, error } = await cliente.auth.signInWithPassword({ email, password: senha });
  if (error || !data.session) throw new Error(`Não foi possível entrar como ${email}: ${error?.message}`);
  return data.session.access_token;
}

// deno-lint-ignore no-explicit-any
async function alunoApi(jwt: string, corpo: unknown): Promise<any> {
  const r = await fetch(`${URL_SUPABASE}/functions/v1/aluno-api`, {
    method: "POST",
    headers: { apikey: CHAVE_PUBLICA, Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  const json = await r.json().catch(() => null);
  if (!r.ok) throw new Error(`${(corpo as { acao: string }).acao}: ${json?.erro ?? r.status} — ${json?.mensagem ?? ""}`);
  return json;
}

const quantos = Math.max(1, Math.min(30, Number(alunosArg) || 1));
const nomes = ["Ana Souza", "Bruno Lima", "Carla Dias", "Diego Rocha", "Elisa Moura", "Felipe Costa"];

// 1. Login e convite de todos antes de usar o token do QR (ele vale só ~40 s).
const alunos: { nome: string; jwt: string; turmaId: string }[] = [];
for (let i = 1; i <= quantos; i++) {
  const nome = `${nomes[(i - 1) % nomes.length]} (teste ${i})`;
  try {
    const jwt = await entrarComo(`aluno.teste${i}@lumini-teste.example.com`, nome);
    const convite = await alunoApi(jwt, { acao: "aceitar_convite", codigo });
    alunos.push({ nome, jwt, turmaId: convite.turma.id });
  } catch (e) {
    console.log(`${nome}: ${(e as Error).message}`);
  }
}

// 2. Todos "leem o QR" juntos; 3. cada um responde e vê o próprio grafo.
const entradas = await Promise.allSettled(alunos.map((a) => alunoApi(a.jwt, { acao: "entrar_sessao", token })));
for (const [i, a] of alunos.entries()) {
  const entrada = entradas[i];
  if (entrada.status === "rejected") {
    console.log(`${a.nome}: ${(entrada.reason as Error).message}`);
    continue;
  }
  const sessao = entrada.value;
  try {
    if (sessao.ja_enviada) {
      console.log(`${a.nome}: já tinha enviado nesta sessão.`);
      continue;
    }
    // deno-lint-ignore no-explicit-any
    const respostas = sessao.atividade.questoes.map((q: any) =>
      q.tipo === "aberta" ? { questao_id: q.id, texto: "Resposta de teste." } : {
        questao_id: q.id,
        alternativa_id: alternativaArg === "sorteio"
          ? q.alternativas[Math.floor(Math.random() * q.alternativas.length)].id
          : alternativaArg,
      }
    );
    const r = await alunoApi(a.jwt, { acao: "enviar_respostas", tentativa_id: sessao.tentativa_id, respostas });
    const grafo = await alunoApi(a.jwt, { acao: "meu_grafo", turma_id: a.turmaId });
    const resumo = grafo.conceitos
      // deno-lint-ignore no-explicit-any
      .map((c: any) => `${c.nome}: ${c.dominio == null ? "?" : `${Math.round(c.dominio * 100)}%`}`).join(" · ");
    console.log(`${a.nome}: acertou ${r.acertos} de ${r.total_multipla_escolha}` +
      `${r.abertas_aguardando_correcao ? `, ${r.abertas_aguardando_correcao} aberta(s) aguardando correção` : ""}\n  ${resumo}`);
  } catch (e) {
    console.log(`${a.nome}: ${(e as Error).message}`);
  }
}
