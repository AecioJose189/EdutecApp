// Edge Function "aluno-api": tudo o que o site do aluno faz, numa função só.
// Contrato (ações, campos e códigos de erro): docs/contrato-api-mvp.md — não mudar sem combinar.
//
// Entrada: POST { "acao": "...", ...parâmetros } com o JWT do aluno (verify_jwt = true).
//   minhas_turmas    {}                          → { turmas: [Turma] }
//   aceitar_convite  { codigo }                  → { turma: Turma, ja_era_membro }
//   entrar_sessao    { token }                   → { tentativa_id, iniciada_em, prazo_em, ja_enviada, atividade }
//   enviar_respostas { tentativa_id, respostas } → { acertos, total_multipla_escolha, abertas_aguardando_correcao }
//   meu_grafo        { turma_id }                → { conceitos: [{ nome, prerequisitos, dominio, estado, n_respostas }] }
// Turma: { id, nome, codigo, professor_nome }
// Erro: { erro: <código>, mensagem: <texto para o aluno> } com status 4xx/5xx.
//
// Códigos: só os do contrato. Pedido malformado vira o código mais próximo do que o aluno deve fazer:
// token ausente ou ilegível → qr_expirado; código de convite fora do formato → convite_invalido;
// turma_id ou tentativa_id inválidos ou de outra pessoa → nao_matriculado; ação desconhecida → erro_interno (400).
//
// O gabarito nunca sai daqui: as questões vêm de atividades.questoes_aluno e a correção é feita no
// Postgres (enviar_tentativa), que também chama o BKT. RN13: no BKT o aluno é só o uuid.

import { type Cliente, clienteServico, type Usuario, usuarioDoPedido } from "../_shared/auth.ts";
import { CORS, json, UUID } from "../_shared/http.ts";
import { assinaturaValida, lerToken, tokenRecente } from "../_shared/qr.ts";

type Corpo = Record<string, unknown>;

type CodigoErro =
  | "nao_autenticado" | "convite_invalido" | "qr_expirado" | "sessao_encerrada" | "nao_matriculado"
  | "tentativa_ja_enviada" | "tempo_esgotado" | "erro_interno";

class ErroAluno extends Error {
  constructor(readonly codigo: CodigoErro, message: string, readonly status: number) {
    super(message);
  }
}

const ACOES: Record<string, (u: Usuario, corpo: Corpo) => Promise<unknown>> = {
  minhas_turmas: minhasTurmas,
  aceitar_convite: aceitarConvite,
  entrar_sessao: entrarSessao,
  enviar_respostas: enviarRespostas,
  meu_grafo: meuGrafo,
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    if (req.method !== "POST") throw new ErroAluno("erro_interno", "Use POST com um JSON { acao, ... }.", 405);
    const usuario = await usuarioDoPedido(req);
    if (!usuario) throw new ErroAluno("nao_autenticado", "Sua sessão expirou. Entre com o Google de novo.", 401);

    let corpo: Corpo;
    try {
      corpo = await req.json();
    } catch {
      throw new ErroAluno("erro_interno", "Pedido inválido.", 400);
    }
    const acao = ACOES[String(corpo?.acao ?? "")];
    if (!acao) throw new ErroAluno("erro_interno", "Ação desconhecida.", 400);
    return json(await acao(usuario, corpo));
  } catch (e) {
    if (e instanceof ErroAluno) {
      if (e.status >= 500) console.error(e.message);
      return json({ erro: e.codigo, mensagem: e.message }, e.status);
    }
    console.error("Erro inesperado:", e);
    return json({ erro: "erro_interno", mensagem: "Algo deu errado no servidor. Tente de novo em instantes." }, 500);
  }
});

// ---------- turmas ----------

interface TurmaLinha {
  id: string;
  nome: string;
  codigo: string;
  professor: { nome: string } | null;
}

const SELECT_TURMA = "id, nome, codigo, professor:perfis(nome)";

function turmaDoContrato(t: TurmaLinha) {
  return { id: t.id, nome: t.nome, codigo: t.codigo, professor_nome: t.professor?.nome ?? "" };
}

/** Pelo cliente do aluno: o RLS só deixa ver as turmas de que ele é membro (e o nome do professor). */
async function minhasTurmas(u: Usuario) {
  const { data, error } = await u.cliente
    .from("membros_turma")
    .select(`entrou_em, turma:turmas(${SELECT_TURMA})`)
    .eq("aluno_id", u.id)
    .order("entrou_em");
  if (error) throw falhaBanco("carregar suas turmas", error);
  const linhas = data as unknown as { turma: TurmaLinha | null }[];
  return { turmas: linhas.filter((l) => l.turma).map((l) => turmaDoContrato(l.turma!)) };
}

async function aceitarConvite(u: Usuario, corpo: Corpo) {
  const codigo = String(corpo.codigo ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const invalido = new ErroAluno("convite_invalido",
    "Este convite não existe ou foi desativado. Peça ao professor o link de convite da turma.", 404);
  if (!/^[A-Z0-9]{6}$/.test(codigo)) throw invalido;

  // O aluno não lê convites (RLS): a busca pelo código é do servidor.
  const servico = clienteServico();
  const { data: convite, error } = await servico
    .from("convites")
    .select(`turma:turmas(${SELECT_TURMA}, professor_id)`)
    .eq("codigo", codigo)
    .eq("ativo", true)
    .maybeSingle();
  if (error) throw falhaBanco("conferir o convite", error);
  const turma = (convite as unknown as { turma: (TurmaLinha & { professor_id: string }) | null } | null)?.turma;
  if (!turma) throw invalido;
  if (turma.professor_id === u.id) {
    throw new ErroAluno("convite_invalido",
      "Você é o professor desta turma. Para entrar como aluno, use outra conta Google.", 400);
  }

  const { data: inseridos, error: erroInsercao } = await servico
    .from("membros_turma")
    .upsert({ turma_id: turma.id, aluno_id: u.id }, { onConflict: "turma_id,aluno_id", ignoreDuplicates: true })
    .select("aluno_id");
  if (erroInsercao) throw falhaBanco("entrar na turma", erroInsercao);

  return { turma: turmaDoContrato(turma), ja_era_membro: (inseridos ?? []).length === 0 };
}

// ---------- sessão em sala ----------

interface SessaoLinha {
  id: string;
  aberta: boolean;
  entrada_aberta: boolean;
  segredo: string;
  atividade: {
    id: string;
    titulo: string;
    tempo_minutos: number | null;
    questoes_aluno: unknown[];
    turma: { id: string; nome: string; codigo: string; professor_id: string };
  };
}

interface TentativaLinha {
  id: string;
  iniciada_em: string;
  prazo_em: string | null;
  enviada_em: string | null;
}

const COLUNAS_TENTATIVA = "id, iniciada_em, prazo_em, enviada_em";

async function entrarSessao(u: Usuario, corpo: Corpo) {
  const expirado = new ErroAluno("qr_expirado", "O código mudou. Aponte a câmera para o QR de novo.", 410);
  const lido = lerToken(corpo.token);
  if (!lido) throw expirado;

  const servico = clienteServico();
  const { data, error } = await servico
    .from("sessoes")
    .select(`id, aberta, entrada_aberta, segredo,
      atividade:atividades(id, titulo, tempo_minutos, questoes_aluno, turma:turmas(id, nome, codigo, professor_id))`)
    .eq("id", lido.sessaoId)
    .maybeSingle();
  if (error) throw falhaBanco("abrir a sessão", error);
  const sessao = data as unknown as SessaoLinha | null;
  if (!sessao || !(await assinaturaValida(lido, sessao.segredo))) throw expirado;
  if (!sessao.aberta) {
    throw new ErroAluno("sessao_encerrada", "Esta sessão foi encerrada pelo professor.", 410);
  }

  const turma = sessao.atividade.turma;
  if (turma.professor_id === u.id) {
    throw new ErroAluno("nao_matriculado",
      "Você é o professor desta turma. Para responder como aluno, use outra conta Google.", 403);
  }
  const { data: membro, error: erroMembro } = await servico
    .from("membros_turma").select("aluno_id").eq("turma_id", turma.id).eq("aluno_id", u.id).maybeSingle();
  if (erroMembro) throw falhaBanco("conferir a matrícula", erroMembro);
  if (!membro) {
    throw new ErroAluno("nao_matriculado",
      "Você ainda não está nesta turma. Abra o link de convite que o professor compartilhou.", 403);
  }

  // Ler o QR de novo (ou recarregar a página com um token velho) devolve a mesma tentativa.
  let tentativa = await buscarTentativa(servico, sessao.id, u.id);
  if (!tentativa) {
    if (!tokenRecente(lido)) throw expirado;
    if (!sessao.entrada_aberta) {
      throw new ErroAluno("sessao_encerrada", "O professor já fechou a entrada nesta sessão.", 410);
    }
    tentativa = await criarTentativa(servico, sessao, u.id);
  }

  return {
    tentativa_id: tentativa.id,
    iniciada_em: iso(tentativa.iniciada_em),
    prazo_em: tentativa.prazo_em ? iso(tentativa.prazo_em) : null,
    ja_enviada: tentativa.enviada_em != null,
    atividade: {
      id: sessao.atividade.id,
      titulo: sessao.atividade.titulo,
      turma: { id: turma.id, nome: turma.nome, codigo: turma.codigo },
      questoes: sessao.atividade.questoes_aluno,
    },
  };
}

async function buscarTentativa(servico: Cliente, sessaoId: string, alunoId: string): Promise<TentativaLinha | null> {
  const { data, error } = await servico
    .from("tentativas").select(COLUNAS_TENTATIVA).eq("sessao_id", sessaoId).eq("aluno_id", alunoId).maybeSingle();
  if (error) throw falhaBanco("carregar a tentativa", error);
  return data as TentativaLinha | null;
}

async function criarTentativa(servico: Cliente, sessao: SessaoLinha, alunoId: string): Promise<TentativaLinha> {
  const { data: perfil } = await servico.from("perfis").select("nome").eq("id", alunoId).maybeSingle();
  const inicio = new Date();
  const minutos = sessao.atividade.tempo_minutos;
  const { data, error } = await servico
    .from("tentativas")
    .insert({
      sessao_id: sessao.id,
      aluno_id: alunoId,
      aluno_nome: perfil?.nome ?? "Aluno",
      iniciada_em: inicio.toISOString(),
      prazo_em: minutos ? new Date(inicio.getTime() + minutos * 60_000).toISOString() : null,
    })
    .select(COLUNAS_TENTATIVA)
    .single();
  if (!error) return data as TentativaLinha;
  // Dois toques ao mesmo tempo: a outra chamada criou primeiro.
  if (error.code === "23505") {
    const existente = await buscarTentativa(servico, sessao.id, alunoId);
    if (existente) return existente;
  }
  throw falhaBanco("começar a tentativa", error);
}

async function enviarRespostas(u: Usuario, corpo: Corpo) {
  const tentativa = String(corpo.tentativa_id ?? "");
  if (!UUID.test(tentativa)) {
    throw new ErroAluno("nao_matriculado", "Tentativa não encontrada. Leia o QR da sessão de novo.", 403);
  }
  const respostas = (Array.isArray(corpo.respostas) ? corpo.respostas : []).slice(0, 200).map((r) => {
    const item = (r ?? {}) as Record<string, unknown>;
    return {
      questao_id: String(item.questao_id ?? ""),
      ...(typeof item.alternativa_id === "string" ? { alternativa_id: item.alternativa_id } : {}),
      ...(typeof item.texto === "string" ? { texto: item.texto.slice(0, 10_000) } : {}),
    };
  });

  const { data, error } = await clienteServico().rpc("enviar_tentativa", { tentativa, aluno: u.id, respostas });
  if (error) {
    const mapa: Record<string, [CodigoErro, string, number]> = {
      LU403: ["nao_matriculado", "Tentativa não encontrada. Leia o QR da sessão de novo.", 403],
      LU409: ["tentativa_ja_enviada", "Você já enviou esta atividade.", 409],
      LU410: ["sessao_encerrada", "O professor encerrou a sessão antes do envio.", 410],
      LU408: ["tempo_esgotado", "O tempo da atividade acabou e as respostas não foram enviadas.", 422],
    };
    const conhecido = mapa[error.code ?? ""];
    if (conhecido) throw new ErroAluno(...conhecido);
    throw falhaBanco("enviar as respostas", error);
  }
  const r = (data as Record<string, number>[])[0];
  return {
    acertos: r.acertos,
    total_multipla_escolha: r.total_multipla_escolha,
    abertas_aguardando_correcao: r.abertas_aguardando_correcao,
  };
}

// ---------- grafo do aluno ----------

/** Pelo cliente do aluno: o RLS só devolve os conceitos de turmas dele e o próprio domínio. */
async function meuGrafo(u: Usuario, corpo: Corpo) {
  const turma = String(corpo.turma_id ?? "");
  const naoMatriculado = new ErroAluno("nao_matriculado",
    "Você não está nesta turma. Abra o link de convite que o professor compartilhou.", 403);
  if (!UUID.test(turma)) throw naoMatriculado;

  const { data: membro, error: erroMembro } = await u.cliente
    .from("membros_turma").select("aluno_id").eq("turma_id", turma).eq("aluno_id", u.id).maybeSingle();
  if (erroMembro) throw falhaBanco("conferir a matrícula", erroMembro);
  if (!membro) throw naoMatriculado;

  const [conceitos, dominio] = await Promise.all([
    u.cliente.from("conceitos_turma").select("nome, prerequisitos").eq("turma_id", turma).order("criado_em"),
    u.cliente.from("dominio").select("conceito, p_dominio, n_respostas").eq("turma_id", turma).eq("aluno_id", u.id),
  ]);
  for (const r of [conceitos, dominio]) if (r.error) throw falhaBanco("carregar seu grafo", r.error);

  const meu = new Map(
    (dominio.data as { conceito: string; p_dominio: number; n_respostas: number }[]).map((d) => [d.conceito, d]),
  );
  return {
    conceitos: (conceitos.data as { nome: string; prerequisitos: string[] }[]).map((c) => {
      const d = meu.get(c.nome);
      const p = d ? Math.round(Number(d.p_dominio) * 10000) / 10000 : null;
      return {
        nome: c.nome,
        prerequisitos: c.prerequisitos,
        dominio: p,
        estado: p == null ? "sem_dados" : estadoDominio(p),
        n_respostas: d?.n_respostas ?? 0,
      };
    }),
  };
}

/** Mesmos limiares (propostos) de public.estado_dominio: 0,5 e 0,8. */
function estadoDominio(p: number): string {
  return p < 0.5 ? "revisar" : p < 0.8 ? "em_desenvolvimento" : "consolidado";
}

// ---------- utilitários ----------

function iso(data: string): string {
  return new Date(data).toISOString();
}

function falhaBanco(oQue: string, e: { message: string; code?: string }): ErroAluno {
  console.error(`Falha ao ${oQue}:`, e.code, e.message);
  return new ErroAluno("erro_interno", `Não foi possível ${oQue}. Tente de novo em instantes.`, 500);
}
