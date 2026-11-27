// Simula o fluxo inteiro do MVP contra o Supabase de verdade, com dois usuários de teste:
//   professor: cria turma → convite → gera atividade (PDF) → publica → abre sessão → token do QR
//   aluno:     aceita convite → lê o QR → responde → vê o próprio grafo
//   aluno 2:   NÃO aceita o convite → lê o QR → nao_matriculado com a turma → entrar_na_turma → responde
//   professor: acompanha a sessão ao vivo (Realtime) → vê o grafo da turma → encerra
//   aluno:     revê a atividade (fase 2): sem gabarito com a sessão aberta, gabarito completo depois de encerrar
//   fase 2b:   alternativas embaralhadas por tentativa (estável ao recarregar, diferente entre alunos,
//              correção pelo id) e confiança na resposta (gravada, devolvida na revisão, estatísticas)
// Também confere os erros do contrato (docs/contrato-api-mvp.md) e que o aluno não recebe gabarito.
//
// Uso (na raiz do repositório):
//   export SUPABASE_CHAVE_SECRETA=$(npx supabase projects api-keys --project-ref vuxnyjkykftecqgznswg -o json \
//     | node -e 'const k=JSON.parse(require("fs").readFileSync(0));console.log(k.find(x=>x.name==="service_role").api_key)')
//   npx deno run -A scripts/testar-fluxo-mvp.ts [arquivo.pdf]
//
// URL e chave pública: SUPABASE_URL e SUPABASE_CHAVE_PUBLICA (ambiente ou local.properties).
// A chave secreta só cria/atualiza os usuários de teste (Admin API) e nunca vai para arquivo.
// O PDF padrão é amostras/teste.pdf (com os parâmetros 5 · mista · média a geração é reaproveitada,
// sem gastar cota do Gemini). Cada execução cria uma turma nova na conta do professor de teste.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

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
if (!URL_SUPABASE || !CHAVE_PUBLICA || !CHAVE_SECRETA) {
  console.error("Defina SUPABASE_URL e SUPABASE_CHAVE_PUBLICA (ou local.properties) e SUPABASE_CHAVE_SECRETA.");
  Deno.exit(2);
}
const PDF = Deno.args[0] ?? `${raiz}amostras/teste.pdf`;

// ---------- verificação ----------

let falhas = 0;
function verificar(condicao: unknown, descricao: string, detalhe?: unknown) {
  if (condicao) {
    console.log(`  ✓ ${descricao}`);
  } else {
    falhas++;
    console.log(`  ✗ ${descricao}`, detalhe === undefined ? "" : JSON.stringify(detalhe).slice(0, 400));
  }
}
function etapa(titulo: string) {
  console.log(`\n${titulo}`);
}

// ---------- usuários de teste ----------

const admin = createClient(URL_SUPABASE, CHAVE_SECRETA, { auth: { persistSession: false } });

async function entrarComo(email: string, nome: string): Promise<{ cliente: SupabaseClient; token: string; id: string }> {
  const senha = `${crypto.randomUUID()}Aa1!`;
  const criado = await admin.auth.admin.createUser({
    email, password: senha, email_confirm: true, user_metadata: { full_name: nome },
  });
  let id = criado.data.user?.id;
  if (!id) { // já existe: troca a senha
    const { data } = await admin.auth.admin.listUsers({ perPage: 1000 });
    id = data.users.find((u) => u.email === email)?.id;
    if (!id) throw new Error(`Não foi possível criar ${email}: ${criado.error?.message}`);
    const { error } = await admin.auth.admin.updateUserById(id, { password: senha });
    if (error) throw error;
  }
  const cliente = createClient(URL_SUPABASE, CHAVE_PUBLICA, { auth: { persistSession: false } });
  let sessao = (await cliente.auth.signInWithPassword({ email, password: senha })).data.session;
  if (!sessao) { // provedor de e-mail/senha desligado: link mágico gerado pelo admin
    const { data, error } = await admin.auth.admin.generateLink({ type: "magiclink", email });
    if (error) throw error;
    sessao = (await cliente.auth.verifyOtp({ token_hash: data.properties.hashed_token, type: "magiclink" })).data.session;
  }
  if (!sessao) throw new Error(`Não foi possível entrar como ${email}.`);
  return { cliente, token: sessao.access_token, id };
}

// ---------- chamadas às funções ----------

interface Resposta {
  status: number;
  // deno-lint-ignore no-explicit-any
  corpo: any;
}

async function chamar(funcao: string, token: string | null, corpo: unknown): Promise<Resposta> {
  const r = await fetch(`${URL_SUPABASE}/functions/v1/${funcao}`, {
    method: "POST",
    headers: {
      apikey: CHAVE_PUBLICA,
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(corpo),
  });
  return { status: r.status, corpo: await r.json().catch(() => null) };
}

async function gerarAtividade(token: string): Promise<Resposta> {
  const form = new FormData();
  form.append("pdf", new Blob([await Deno.readFile(PDF)], { type: "application/pdf" }), "teste.pdf");
  form.append("quantidade", "5");
  form.append("formato", "mista");
  form.append("dificuldade", "media");
  const r = await fetch(`${URL_SUPABASE}/functions/v1/gerar-atividade`, {
    method: "POST",
    headers: { apikey: CHAVE_PUBLICA, Authorization: `Bearer ${token}` },
    body: form,
  });
  return { status: r.status, corpo: await r.json().catch(() => null) };
}

const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ---------- fluxo ----------

etapa("0. Usuários de teste e acesso sem login");
const prof = await entrarComo("professor.teste@lumini-teste.example.com", "Professora Teste Lumini");
const aluno = await entrarComo("aluno.teste@lumini-teste.example.com", "Aluno Teste Lumini");
// Aluno 2 nunca aceita convite: entra na turma (nova a cada execução) pelo QR da sessão.
const aluno2 = await entrarComo("aluno2.teste@lumini-teste.example.com", "Aluna QR Teste Lumini");
verificar(prof.id && aluno.id && aluno2.id && new Set([prof.id, aluno.id, aluno2.id]).size === 3,
  "professor e dois alunos logados");
for (const f of ["turma-api", "aluno-api", "gerar-atividade"]) {
  const r = await chamar(f, null, { acao: "minhas_turmas" });
  verificar(r.status === 401, `${f} sem JWT → 401`, r);
}
const anon = await chamar("aluno-api", CHAVE_PUBLICA.startsWith("eyJ") ? CHAVE_PUBLICA : null, { acao: "minhas_turmas" });
verificar(anon.status === 401, "aluno-api sem usuário → 401", anon);

etapa("1. Professor: turma e convite");
const turmaR = await chamar("turma-api", prof.token, {
  acao: "criar_turma", nome: "Inteligência Artificial", codigo: `Teste ${new Date().toISOString().slice(0, 16)}`,
});
verificar(turmaR.status === 200, "criar_turma", turmaR);
const turmaId: string = turmaR.corpo?.turma?.id;
const lista = await chamar("turma-api", prof.token, { acao: "listar_turmas" });
verificar(lista.corpo?.turmas?.some((t: { id: string }) => t.id === turmaId), "listar_turmas traz a turma nova");
const conviteR = await chamar("turma-api", prof.token, { acao: "criar_convite", turma_id: turmaId });
const codigo: string = conviteR.corpo?.convite?.codigo;
verificar(/^[A-Z0-9]{6}$/.test(codigo ?? ""), `convite ${codigo}`, conviteR);
verificar(String(conviteR.corpo?.convite?.link).endsWith(`/convite/${codigo}`), `link ${conviteR.corpo?.convite?.link}`);
const conviteDeNovo = await chamar("turma-api", prof.token, { acao: "criar_convite", turma_id: turmaId });
verificar(conviteDeNovo.corpo?.convite?.codigo === codigo, "criar_convite de novo devolve o mesmo convite ativo");
const intruso = await chamar("turma-api", aluno.token, { acao: "grafo", turma_id: turmaId });
verificar(intruso.status === 404, "aluno não usa a turma-api numa turma que não é dele → 404", intruso);

etapa("2. Aluno: convite");
const invalido = await chamar("aluno-api", aluno.token, { acao: "aceitar_convite", codigo: "ZZZZ22" });
verificar(invalido.status === 404 && invalido.corpo?.erro === "convite_invalido", "código inexistente → convite_invalido", invalido);
const aceito = await chamar("aluno-api", aluno.token, { acao: "aceitar_convite", codigo: codigo.toLowerCase() });
verificar(aceito.status === 200 && aceito.corpo?.ja_era_membro === false && aceito.corpo?.turma?.id === turmaId,
  "aceitar_convite (minúsculas também valem)", aceito);
verificar(aceito.corpo?.turma?.professor_nome === "Professora Teste Lumini", "turma traz o nome do professor", aceito.corpo);
const aceitoDeNovo = await chamar("aluno-api", aluno.token, { acao: "aceitar_convite", codigo });
verificar(aceitoDeNovo.corpo?.ja_era_membro === true, "aceitar de novo é idempotente (ja_era_membro)", aceitoDeNovo);
const profAceita = await chamar("aluno-api", prof.token, { acao: "aceitar_convite", codigo });
verificar(profAceita.corpo?.erro === "convite_invalido", "professor não vira aluno da própria turma", profAceita);
const minhas = await chamar("aluno-api", aluno.token, { acao: "minhas_turmas" });
verificar(minhas.corpo?.turmas?.some((t: { id: string }) => t.id === turmaId), "minhas_turmas traz a turma", minhas);

etapa("3. Professor: gerar e publicar");
const geracao = await gerarAtividade(prof.token);
verificar(geracao.status === 200 && Number.isInteger(geracao.corpo?.geracao_id),
  `gerar-atividade (${geracao.corpo?.reaproveitado ? "reaproveitada" : "nova"}, ${geracao.corpo?.questoes?.length} questões)`,
  geracao.status === 200 ? undefined : geracao);
const publicada = await chamar("turma-api", prof.token, {
  acao: "publicar", turma_id: turmaId, geracao_id: geracao.corpo?.geracao_id, permitir_refazer: true, tempo_minutos: 30,
});
const atividadeId: string = publicada.corpo?.atividade_id;
verificar(publicada.status === 200 && atividadeId, "publicar", publicada);

etapa("4. Professor: sessão, QR e Realtime");
const sessaoR = await chamar("turma-api", prof.token, { acao: "abrir_sessao", atividade_id: atividadeId });
const sessaoId: string = sessaoR.corpo?.sessao?.id;
verificar(sessaoR.status === 200 && sessaoR.corpo?.sessao?.aberta, "abrir_sessao", sessaoR);
const atividades = await chamar("turma-api", prof.token, { acao: "listar_atividades", turma_id: turmaId });
verificar(atividades.corpo?.atividades?.[0]?.sessao_aberta?.id === sessaoId, "listar_atividades mostra a sessão aberta");

// deno-lint-ignore no-explicit-any
const eventos: any[] = [];
await prof.cliente.realtime.setAuth(prof.token);
const canal = prof.cliente
  .channel(`sessao-${sessaoId}`)
  .on("postgres_changes", { event: "*", schema: "public", table: "tentativas", filter: `sessao_id=eq.${sessaoId}` },
    (p) => eventos.push(p));
const inscrito = await new Promise<string>((ok) => {
  canal.subscribe((status) => {
    if (status !== "SUBSCRIBED" && status !== "CHANNEL_ERROR" && status !== "TIMED_OUT") return;
    ok(status);
  });
});
verificar(inscrito === "SUBSCRIBED", "professor inscrito no Realtime de tentativas", inscrito);

const qr = await chamar("turma-api", prof.token, { acao: "token_qr", sessao_id: sessaoId });
const token: string = qr.corpo?.token;
verificar(qr.status === 200 && String(qr.corpo?.url).endsWith(`/s/${token}`), `token_qr (${token?.length} caracteres)`, qr);
const qrAluno = await chamar("turma-api", aluno.token, { acao: "token_qr", sessao_id: sessaoId });
verificar(qrAluno.status === 404, "aluno não gera o QR", qrAluno);

etapa("5. Aluno: lê o QR e responde");
const [id, janela, assinatura] = token.split(".");
const adulterado = await chamar("aluno-api", aluno.token, {
  acao: "entrar_sessao", token: `${id}.${janela}.${assinatura.slice(0, -2)}${assinatura.endsWith("A") ? "BB" : "AA"}`,
});
verificar(adulterado.corpo?.erro === "qr_expirado", "token adulterado → qr_expirado", adulterado);
const profEntra = await chamar("aluno-api", prof.token, { acao: "entrar_sessao", token });
verificar(profEntra.corpo?.erro === "nao_matriculado" && profEntra.corpo?.turma === undefined,
  "professor não entra como aluno → nao_matriculado, sem oferecer a turma", profEntra);
const profForca = await chamar("aluno-api", prof.token, { acao: "entrar_sessao", token, entrar_na_turma: true });
verificar(profForca.status === 403 && profForca.corpo?.erro === "nao_matriculado" && profForca.corpo?.turma === undefined &&
  /professor desta turma/.test(profForca.corpo?.mensagem ?? ""),
  `professor lendo o próprio QR com entrar_na_turma → erro claro ("${profForca.corpo?.mensagem}")`, profForca);

const entrou = await chamar("aluno-api", aluno.token, { acao: "entrar_sessao", token });
verificar(entrou.status === 200 && entrou.corpo?.ja_enviada === false, "entrar_sessao", entrou);
const ativ = entrou.corpo?.atividade;
verificar(ativ?.turma?.id === turmaId && ativ?.titulo, `atividade "${ativ?.titulo}"`);
verificar(entrou.corpo?.prazo_em && Date.parse(entrou.corpo.prazo_em) - Date.parse(entrou.corpo.iniciada_em) === 30 * 60_000,
  "prazo_em = iniciada_em + 30 min");
verificar(!/correta|feedback|rubrica|pagina_origem/.test(JSON.stringify(entrou.corpo)), "nenhum gabarito, feedback ou rubrica");
// deno-lint-ignore no-explicit-any
const questoes: any[] = ativ?.questoes ?? [];
const mc = questoes.filter((q) => q.tipo === "multipla_escolha");
const abertas = questoes.filter((q) => q.tipo === "aberta");
// A primeira alternativa não é mais sempre "a": a ordem é a da tentativa (fase 2b; ids conferidos abaixo).
verificar(questoes.length > 0 && mc.every((q) => q.alternativas?.length >= 2) && abertas.every((q) => q.alternativas === null),
  `${mc.length} de múltipla escolha (com alternativas) e ${abertas.length} abertas (alternativas null)`);
const deNovo = await chamar("aluno-api", aluno.token, { acao: "entrar_sessao", token });
verificar(deNovo.corpo?.tentativa_id === entrou.corpo?.tentativa_id, "ler o QR de novo devolve a mesma tentativa");
// deno-lint-ignore no-explicit-any
const ordemDe = (qs: any[]) => qs.map((q) => (q.alternativas ?? []).map((a: { id: string }) => a.id).join("")).join("|");
verificar(ordemDe(deNovo.corpo?.atividade?.questoes ?? []) === ordemDe(questoes),
  `alternativas na mesma ordem ao recarregar (${ordemDe(questoes)})`);
verificar(mc.every((q) => [...q.alternativas.map((a: { id: string }) => a.id)].sort().join("") ===
    "abcdefgh".slice(0, q.alternativas.length)), "ids das alternativas são os originais (a, b, ...), só a ordem muda");

// Gabarito pela chave secreta (só o script; o aluno nunca recebe): id da alternativa certa de cada questão.
const { data: atividadeBanco } = await admin.from("atividades").select("questoes").eq("id", atividadeId).single();
const certa: Record<string, string> = {};
// deno-lint-ignore no-explicit-any
(atividadeBanco?.questoes ?? []).forEach((q: any, i: number) => {
  const j = (q.alternativas ?? []).findIndex((a: { correta: boolean }) => a.correta);
  if (j >= 0) certa[String(i + 1)] = String.fromCharCode(97 + j);
});

const enviado = await chamar("aluno-api", aluno.token, {
  acao: "enviar_respostas",
  tentativa_id: entrou.corpo?.tentativa_id,
  respostas: questoes.map((q) => q.tipo === "aberta" ? { questao_id: q.id, texto: "Resposta aberta do teste.", confianca: "acho" }
    : { questao_id: q.id, alternativa_id: "a", confianca: "certeza" }),
});
const acertosEsperados1 = mc.filter((q) => certa[q.id] === "a").length;
verificar(enviado.corpo?.acertos === acertosEsperados1,
  `correção pelo id: "a" é a certa em ${acertosEsperados1} de ${mc.length}, onde quer que apareça`, enviado.corpo);
verificar(enviado.status === 200 && enviado.corpo?.total_multipla_escolha === mc.length &&
  enviado.corpo?.abertas_aguardando_correcao === abertas.length,
  `enviar_respostas: acertou ${enviado.corpo?.acertos} de ${enviado.corpo?.total_multipla_escolha}`, enviado);
// deno-lint-ignore no-explicit-any
const porQuestao: any[] = enviado.corpo?.questoes ?? [];
verificar(porQuestao.length === questoes.length &&
  porQuestao.every((r, i) => r.questao_id === questoes[i].id &&
    (questoes[i].tipo === "aberta" ? r.acertou === null : typeof r.acertou === "boolean")) &&
  porQuestao.filter((r) => r.acertou === true).length === enviado.corpo?.acertos,
  "enviar_respostas.questoes: acertou por questão (null nas abertas)", porQuestao);
verificar(!/correta|feedback|rubrica/.test(JSON.stringify(enviado.corpo)), "resultado do envio sem gabarito");
const repetido = await chamar("aluno-api", aluno.token, {
  acao: "enviar_respostas", tentativa_id: entrou.corpo?.tentativa_id, respostas: [],
});
verificar(repetido.status === 409 && repetido.corpo?.erro === "tentativa_ja_enviada", "envio repetido → tentativa_ja_enviada", repetido);
const depois = await chamar("aluno-api", aluno.token, { acao: "entrar_sessao", token });
verificar(depois.corpo?.ja_enviada === true, "depois do envio, entrar_sessao diz ja_enviada");

etapa("5b. Aluno 2 (sem convite): entra na turma pelo QR da sessão");
const fora = await chamar("aluno-api", aluno2.token, { acao: "entrar_sessao", token });
verificar(fora.status === 403 && fora.corpo?.erro === "nao_matriculado" && fora.corpo?.turma?.id === turmaId &&
  fora.corpo?.turma?.nome === "Inteligência Artificial" && typeof fora.corpo?.turma?.codigo === "string",
  "sem convite: nao_matriculado com { turma: {id, nome, codigo} }", fora);
const turmasAntes = await chamar("aluno-api", aluno2.token, { acao: "minhas_turmas" });
verificar(!turmasAntes.corpo?.turmas?.some((t: { id: string }) => t.id === turmaId), "ler o QR sozinho não matricula");
const entrou2 = await chamar("aluno-api", aluno2.token, { acao: "entrar_sessao", token, entrar_na_turma: true });
verificar(entrou2.status === 200 && entrou2.corpo?.tentativa_id && entrou2.corpo?.ja_enviada === false &&
  entrou2.corpo?.atividade?.turma?.id === turmaId, "entrar_na_turma: true → tentativa", entrou2);
const turmasDepois = await chamar("aluno-api", aluno2.token, { acao: "minhas_turmas" });
verificar(turmasDepois.corpo?.turmas?.some((t: { id: string }) => t.id === turmaId), "agora é aluno da turma (minhas_turmas)");
// deno-lint-ignore no-explicit-any
const questoes2: any[] = entrou2.corpo?.atividade?.questoes ?? [];
// Probabilidade de coincidir em todas as questões de múltipla escolha: 1 em milhares (só por azar).
verificar(ordemDe(questoes2) !== ordemDe(questoes),
  `dois alunos, ordens diferentes (${ordemDe(questoes)} × ${ordemDe(questoes2)})`);
const posicoes = mc.map((q) => questoes2.find((x) => x.id === q.id).alternativas
  .findIndex((a: { id: string }) => a.id === certa[q.id]));
// Aluno 2 marca a alternativa certa pelo id, na posição em que ela apareceu para ele, e diz "chute".
const enviado2 = await chamar("aluno-api", aluno2.token, {
  acao: "enviar_respostas",
  tentativa_id: entrou2.corpo?.tentativa_id,
  respostas: questoes2.map((q) => q.tipo === "aberta" ? { questao_id: q.id, texto: "Outra resposta." }
    : { questao_id: q.id, alternativa_id: q.alternativas[posicoes[mc.findIndex((m) => m.id === q.id)]].id, confianca: "chute" }),
});
verificar(enviado2.status === 200 && enviado2.corpo?.acertos === mc.length && enviado2.corpo?.total_multipla_escolha === mc.length,
  `correção certa com a ordem trocada: a certa estava nas posições ${posicoes.map((p) => "ABCDEFGH"[p]).join(", ")} → ` +
  `${enviado2.corpo?.acertos} de ${mc.length}`, enviado2);

etapa("5c. Aluno: revisão com a sessão ainda aberta (anti-cola)");
const tentativaId: string = entrou.corpo?.tentativa_id;
const minhasAtiv = await chamar("aluno-api", aluno.token, { acao: "minhas_atividades", turma_id: turmaId });
const item = minhasAtiv.corpo?.atividades?.[0];
verificar(minhasAtiv.status === 200 && minhasAtiv.corpo?.atividades?.length === 1 && item?.tentativa_id === tentativaId &&
  item?.atividade?.id === atividadeId && item?.atividade?.numero === 1 && item?.atividade?.titulo === ativ?.titulo &&
  item?.turma?.id === turmaId && item?.acertos === enviado.corpo?.acertos &&
  item?.total_multipla_escolha === mc.length && item?.gabarito_liberado === false && item?.enviada_em,
  "minhas_atividades: a tentativa enviada, nº 1, gabarito_liberado false", minhasAtiv);
const todas = await chamar("aluno-api", aluno.token, { acao: "minhas_atividades" });
verificar(todas.corpo?.atividades?.some((a: { tentativa_id: string }) => a.tentativa_id === tentativaId),
  "minhas_atividades sem turma_id traz todas as turmas");
const revAberta = await chamar("aluno-api", aluno.token, { acao: "revisao_tentativa", tentativa_id: tentativaId });
// deno-lint-ignore no-explicit-any
const qRev: any[] = revAberta.corpo?.questoes ?? [];
verificar(revAberta.status === 200 && revAberta.corpo?.gabarito_liberado === false && qRev.length === questoes.length,
  "revisao_tentativa com a sessão aberta", revAberta);
verificar(!/"(correta|feedback|rubrica)"/.test(JSON.stringify(revAberta.corpo)),
  "sessão aberta: nenhum correta, feedback ou rubrica (os campos somem)", revAberta.corpo);
verificar(JSON.stringify(qRev.map((q) => q.confianca)) ===
    JSON.stringify(questoes.map((q) => q.tipo === "aberta" ? "acho" : "certeza")),
  "revisao_tentativa devolve a confiança de cada questão", qRev.map((q) => q.confianca));
verificar(ordemDe(qRev) === ordemDe(questoes), "revisão com as alternativas na ordem que o aluno viu");
verificar(qRev.every((q, i) => q.acertou === porQuestao[i].acertou && q.minha_resposta &&
    (q.tipo === "aberta" ? q.minha_resposta.texto : q.minha_resposta.alternativa_id === "a")),
  "acertou e minha_resposta batem com o envio");
const alheia = await chamar("aluno-api", aluno2.token, { acao: "revisao_tentativa", tentativa_id: tentativaId });
verificar(alheia.status === 403 && alheia.corpo?.erro === "nao_matriculado", "um aluno não lê a tentativa de outro", alheia);
const profLe = await chamar("aluno-api", prof.token, { acao: "revisao_tentativa", tentativa_id: tentativaId });
verificar(profLe.corpo?.erro === "nao_matriculado", "nem o professor lê pela aluno-api", profLe);
const lixo = await chamar("aluno-api", aluno.token, { acao: "revisao_tentativa", tentativa_id: "nao-e-uuid" });
verificar(lixo.corpo?.erro === "nao_matriculado", "tentativa_id inválido → nao_matriculado", lixo);

etapa("6. Professor: ao vivo e grafo");
for (let i = 0; i < 20 && eventos.length < 2; i++) await esperar(250);
verificar(eventos.some((e) => e.eventType === "INSERT" && e.new?.aluno_nome === "Aluno Teste Lumini"),
  "Realtime: aluno entrou (INSERT)", eventos.map((e) => e.eventType));
verificar(eventos.some((e) => e.eventType === "UPDATE" && e.new?.enviada_em && e.new?.acertos === enviado.corpo?.acertos),
  "Realtime: respostas chegaram (UPDATE com acertos)");
await prof.cliente.removeChannel(canal);
const painel = await chamar("turma-api", prof.token, { acao: "sessao", sessao_id: sessaoId });
verificar(painel.corpo?.participantes?.[0]?.nome === "Aluno Teste Lumini" && painel.corpo.participantes[0].enviada_em,
  "sessao: participante com envio", painel);
verificar(painel.corpo?.participantes?.some((p: { nome: string; enviada_em: string | null }) =>
  p.nome === "Aluna QR Teste Lumini" && p.enviada_em), "sessao: aluno 2 (entrou pelo QR) também enviou");
const grafoTurma = await chamar("turma-api", prof.token, { acao: "grafo", turma_id: turmaId });
verificar(grafoTurma.corpo?.conceitos?.some((c: { n_respostas: number }) => c.n_respostas > 0), "grafo da turma com respostas reais");
const rotulos = (grafoTurma.corpo?.alunos ?? []).map((a: { rotulo: string }) => a.rotulo);
const conceitosTurma = () => grafoTurma.corpo?.conceitos?.length ?? -1;
verificar(rotulos.includes("Aluno Teste Lumini") && rotulos.includes("Aluna QR Teste Lumini"),
  "grafo lista os dois alunos pelo nome", rotulos);

const conf = await chamar("turma-api", prof.token, { acao: "confianca", turma_id: turmaId });
const esperadoConf: Record<string, number> = {
  n_respostas: 2 * mc.length, n_com_confianca: 2 * mc.length,
  erros_com_certeza: mc.length - acertosEsperados1, acertos_no_chute: mc.length,
};
// Compara campo a campo (o jsonb do Postgres não guarda a ordem das chaves).
verificar(conf.status === 200 && Object.entries(esperadoConf).every(([k, v]) => conf.corpo?.turma?.[k] === v),
  `confiança da turma: ${JSON.stringify(conf.corpo?.turma)}`, conf.corpo);
const confAluno2 = conf.corpo?.alunos?.find((a: { nome: string }) => a.nome === "Aluna QR Teste Lumini");
verificar(confAluno2?.acertos_no_chute === mc.length && confAluno2?.erros_com_certeza === 0, "confiança por aluno", conf.corpo?.alunos);
verificar(conf.corpo?.conceitos?.length > 0 && conf.corpo.conceitos.every((c: { n_respostas: number }) => c.n_respostas > 0),
  `confiança por conceito (${conf.corpo?.conceitos?.length} conceitos)`);
const confAlheia = await chamar("turma-api", aluno.token, { acao: "confianca", turma_id: turmaId });
verificar(confAlheia.status === 404, "aluno não vê as estatísticas de confiança da turma", confAlheia);

etapa("6b. Professor: estatísticas e mapa de cada aluno (fase 3)");
const est = await chamar("turma-api", prof.token, { acao: "estatisticas", turma_id: turmaId });
verificar(est.status === 200 && est.corpo?.resumo?.n_alunos === 2 && est.corpo?.conceitos?.length > 0 &&
  Array.isArray(est.corpo?.evolucao) && est.corpo?.confianca?.turma,
  `estatisticas: ${est.corpo?.resumo?.n_alunos} alunos, ${est.corpo?.conceitos?.length} conceitos, ` +
  `domínio estimado médio ${est.corpo?.resumo?.media_dominio}`, est.corpo);
const chave2 = grafoTurma.corpo?.alunos?.find((a: { rotulo: string }) => a.rotulo === "Aluna QR Teste Lumini")?.chave;
verificar(chave2 === aluno2.id, "grafo devolve a chave de cada aluno", grafoTurma.corpo?.alunos);
const mapa2 = await chamar("turma-api", prof.token, { acao: "mapa_aluno", turma_id: turmaId, aluno: chave2 });
verificar(mapa2.status === 200 && mapa2.corpo?.conceitos?.length === conceitosTurma(),
  `mapa_aluno: ${mapa2.corpo?.conceitos?.filter((c: { p_dominio: number | null }) => c.p_dominio !== null).length} conceitos com dados`, mapa2.corpo);
const conceitoMc = mc[0]?.conceitos?.[0];
const hist2 = await chamar("turma-api", prof.token, { acao: "historico_conceito", turma_id: turmaId, aluno: chave2, conceito: conceitoMc });
verificar(hist2.status === 200 && hist2.corpo?.respostas?.length > 0 && hist2.corpo.respostas.every((r: { confianca: string }) => r.confianca === "chute"),
  `historico_conceito ("${conceitoMc}"): ${hist2.corpo?.respostas?.length} resposta(s), confiança "chute"`, hist2.corpo);
for (const [acao, extra] of [["estatisticas", {}], ["mapa_aluno", { aluno: chave2 }],
                             ["historico_conceito", { aluno: chave2, conceito: conceitoMc }]] as const) {
  const r = await chamar("turma-api", aluno2.token, { acao, turma_id: turmaId, ...extra });
  verificar(r.status === 404, `${acao}: aluno → 404`, r);
}

etapa("7. Aluno: meu grafo");
const meuGrafo = await chamar("aluno-api", aluno.token, { acao: "meu_grafo", turma_id: turmaId });
// deno-lint-ignore no-explicit-any
const conceitos: any[] = meuGrafo.corpo?.conceitos ?? [];
verificar(conceitos.length > 0 && conceitos.some((c) => c.dominio !== null && c.n_respostas > 0),
  `meu_grafo: ${conceitos.length} conceitos, ${conceitos.filter((c) => c.dominio !== null).length} com domínio estimado`, meuGrafo);
verificar(conceitos.every((c) => ["consolidado", "em_desenvolvimento", "revisar", "sem_dados"].includes(c.estado) &&
  (c.estado === "sem_dados") === (c.dominio === null)), "estados do contrato (sem_dados ⇔ dominio null)");
const outra = await chamar("aluno-api", aluno.token, { acao: "meu_grafo", turma_id: crypto.randomUUID() });
verificar(outra.corpo?.erro === "nao_matriculado", "meu_grafo de turma alheia → nao_matriculado", outra);
const profGrafo = await chamar("aluno-api", prof.token, { acao: "meu_grafo", turma_id: turmaId });
verificar(profGrafo.corpo?.erro === "nao_matriculado", "professor não tem 'meu grafo' na própria turma", profGrafo);

etapa("8. Professor: entrada fechada e sessão encerrada");
const fim = await chamar("turma-api", prof.token, { acao: "encerrar_sessao", sessao_id: sessaoId });
verificar(fim.corpo?.sessao?.aberta === false, "encerrar_sessao", fim);
const qrFechado = await chamar("turma-api", prof.token, { acao: "token_qr", sessao_id: sessaoId });
verificar(qrFechado.status === 409, "sem QR depois de encerrar", qrFechado);
const tarde = await chamar("aluno-api", aluno.token, { acao: "entrar_sessao", token });
verificar(tarde.corpo?.erro === "sessao_encerrada", "entrar depois de encerrar → sessao_encerrada", tarde);

const revFechada = await chamar("aluno-api", aluno.token, { acao: "revisao_tentativa", tentativa_id: tentativaId });
// deno-lint-ignore no-explicit-any
const qFim: any[] = revFechada.corpo?.questoes ?? [];
verificar(revFechada.corpo?.gabarito_liberado === true, "depois de encerrar: gabarito_liberado true", revFechada);
verificar(qFim.filter((q) => q.tipo === "multipla_escolha").every((q) =>
    q.alternativas?.length > 0 && q.alternativas.every((a: { correta: unknown; feedback: unknown }) =>
      typeof a.correta === "boolean" && typeof a.feedback === "string") &&
    q.alternativas.filter((a: { correta: boolean }) => a.correta).length === 1),
  "depois de encerrar: cada alternativa com correta e feedback (uma correta por questão)", qFim);
verificar(qFim.filter((q) => q.tipo === "aberta").every((q) => typeof q.rubrica === "string" && q.rubrica.length > 0),
  "depois de encerrar: rubrica das abertas");
verificar(qFim.every((q) => q.tipo !== "multipla_escolha" ||
    q.acertou === q.alternativas.some((a: { id: string; correta: boolean }) => a.correta && a.id === q.minha_resposta?.alternativa_id)),
  "acertou confere com o gabarito revelado");
const minhasDepois = await chamar("aluno-api", aluno.token, { acao: "minhas_atividades", turma_id: turmaId });
verificar(minhasDepois.corpo?.atividades?.[0]?.gabarito_liberado === true, "minhas_atividades: gabarito_liberado true");

const sessao2 = await chamar("turma-api", prof.token, { acao: "abrir_sessao", atividade_id: atividadeId });
verificar(sessao2.corpo?.sessao?.id && sessao2.corpo.sessao.id !== sessaoId, "nova sessão para a mesma atividade");
const token2 = (await chamar("turma-api", prof.token, { acao: "token_qr", sessao_id: sessao2.corpo?.sessao?.id })).corpo?.token;
const fechada = await chamar("turma-api", prof.token, { acao: "encerrar_entrada", sessao_id: sessao2.corpo?.sessao?.id });
verificar(fechada.corpo?.sessao?.entrada_aberta === false && fechada.corpo?.sessao?.aberta === true, "encerrar_entrada", fechada);
const barrado = await chamar("aluno-api", aluno.token, { acao: "entrar_sessao", token: token2 });
verificar(barrado.corpo?.erro === "sessao_encerrada", "entrada fechada barra quem ainda não entrou", barrado);
await chamar("turma-api", prof.token, { acao: "encerrar_sessao", sessao_id: sessao2.corpo?.sessao?.id });

console.log(falhas === 0 ? "\nTudo certo." : `\n${falhas} verificação(ões) falharam.`);
Deno.exit(falhas === 0 ? 0 : 1);
