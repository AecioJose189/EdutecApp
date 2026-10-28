-- Sessões em sala (QR rotativo), tentativas dos alunos e respostas ligadas ao aluno de verdade.
--
-- Fluxo: o professor publica a atividade → abre uma sessão → o desktop mostra um QR que muda a cada
-- 20 s (token = HMAC de sessao_id + janela, assinado com sessoes.segredo, que só o servidor lê) →
-- o aluno lê o QR → entrar_sessao cria a tentativa → enviar_tentativa corrige a múltipla escolha e
-- alimenta o BKT; as abertas ficam aguardando correção e fora do KT.

-- ---------------------------------------------------------------------------
-- Atividades: título e questões sem gabarito (o que o aluno pode ver)
-- ---------------------------------------------------------------------------

-- Questões como o aluno as vê: id = posição (1, 2, ...), alternativas com id 'a', 'b', ... e só o
-- texto. Sem "correta", "feedback", "rubrica" nem página de origem.
create function public.questoes_sem_gabarito(questoes jsonb) returns jsonb
language sql
immutable
set search_path = ''
as $$
  select coalesce(jsonb_agg(
    jsonb_build_object(
      'id', q.i::text,
      'tipo', q.questao ->> 'tipo',
      'enunciado', q.questao ->> 'enunciado',
      'conceitos', coalesce(q.questao -> 'conceitos', '[]'::jsonb),
      'alternativas', case when q.questao ->> 'tipo' = 'multipla_escolha' then (
        select jsonb_agg(jsonb_build_object('id', chr(96 + a.j::int), 'texto', a.alternativa ->> 'texto') order by a.j)
          from jsonb_array_elements(q.questao -> 'alternativas') with ordinality as a (alternativa, j)
      ) end
    ) order by q.i), '[]'::jsonb)
  from jsonb_array_elements(questoes) with ordinality as q (questao, i);
$$;

alter table public.atividades
  add column titulo text not null default 'Atividade' check (length(trim(titulo)) between 1 and 200),
  add column questoes_aluno jsonb generated always as (public.questoes_sem_gabarito(questoes)) stored;

-- publicar_atividade ganha o título (opcional). Sem título: os dois primeiros conceitos da geração.
drop function public.publicar_atividade(uuid, bigint, boolean, int);

create function public.publicar_atividade(
  turma uuid, geracao bigint, permitir_refazer boolean, tempo_minutos int, titulo text default null
) returns uuid
language plpgsql
set search_path = ''
as $$
declare
  resultado jsonb;
  nova uuid;
  ciclo text[];
  titulo_final text;
begin
  -- Trava a turma: duas publicações simultâneas não misturam a junção dos conceitos.
  perform 1 from public.turmas t where t.id = turma for update;
  if not found then
    raise exception 'Turma não encontrada.' using errcode = 'ED404';
  end if;

  select g.resultado into resultado from public.geracoes g where g.id = geracao;
  if not found then
    raise exception 'Geração não encontrada.' using errcode = 'ED404';
  end if;

  titulo_final := left(coalesce(
    nullif(trim(publicar_atividade.titulo), ''),
    (select string_agg(c.nome, ', ' order by c.i)
       from (select trim(x ->> 'nome') as nome, i
               from jsonb_array_elements(resultado -> 'conceitos') with ordinality as e (x, i)
              order by i limit 2) c)
      || case when jsonb_array_length(resultado -> 'conceitos') > 2 then ' e outros' else '' end,
    'Atividade'), 200);

  begin
    insert into public.atividades (turma_id, geracao_id, questoes, permitir_refazer, tempo_minutos, titulo)
    values (turma, geracao, resultado -> 'questoes', publicar_atividade.permitir_refazer,
            publicar_atividade.tempo_minutos, titulo_final)
    returning id into nova;
  exception when unique_violation then
    raise exception 'Esta atividade já foi publicada nesta turma.' using errcode = 'ED409';
  end;

  insert into public.conceitos_turma as ct (turma_id, nome, prerequisitos)
  select turma, trim(c ->> 'nome'),
         coalesce(array(select distinct trim(p) from jsonb_array_elements_text(c -> 'prerequisitos') p
                        where trim(p) <> '' order by 1), '{}')
    from jsonb_array_elements(resultado -> 'conceitos') c
  on conflict (turma_id, nome) do update
    set prerequisitos = array(select distinct p from unnest(ct.prerequisitos || excluded.prerequisitos) p order by 1);

  ciclo := public.ciclo_conceitos(turma);
  if ciclo is not null then
    raise exception 'Os pré-requisitos formariam um ciclo (RN03): %', array_to_string(ciclo, ' → ')
      using errcode = 'ED003';
  end if;

  return nova;
end;
$$;

-- ---------------------------------------------------------------------------
-- Sessões
-- ---------------------------------------------------------------------------

create table public.sessoes (
  id uuid primary key default gen_random_uuid(),
  atividade_id uuid not null references public.atividades (id) on delete cascade,
  aberta boolean not null default true,
  -- Fechada: quem já entrou continua e pode enviar; ninguém novo entra.
  entrada_aberta boolean not null default true,
  -- Chave do HMAC do QR. Só o servidor lê (sem GRANT de SELECT nesta coluna para authenticated).
  segredo text not null default encode(extensions.gen_random_bytes(32), 'hex'),
  aberta_em timestamptz not null default now(),
  encerrada_em timestamptz,
  check (aberta or not entrada_aberta),
  check (aberta = (encerrada_em is null))
);

-- No máximo uma sessão aberta por atividade.
create unique index sessoes_uma_aberta_idx on public.sessoes (atividade_id) where aberta;

-- ---------------------------------------------------------------------------
-- Tentativas (uma por aluno e sessão)
-- ---------------------------------------------------------------------------

create table public.tentativas (
  id uuid primary key default gen_random_uuid(),
  sessao_id uuid not null references public.sessoes (id) on delete cascade,
  aluno_id uuid not null references public.perfis (id) on delete cascade,
  -- Nome no momento da entrada, para o painel ao vivo do professor (Realtime) sem outra consulta.
  aluno_nome text not null,
  iniciada_em timestamptz not null default now(),
  -- iniciada_em + tempo_minutos da atividade; null = sem limite.
  prazo_em timestamptz,
  enviada_em timestamptz,
  acertos int check (acertos >= 0),
  total_multipla_escolha int check (total_multipla_escolha >= 0),
  abertas_aguardando_correcao int check (abertas_aguardando_correcao >= 0),
  -- Respostas como enviadas: [{questao_id, tipo, alternativa_id | texto, correta (null nas abertas),
  -- situacao: corrigida | sem_resposta | aguardando_correcao}]
  respostas jsonb,
  unique (sessao_id, aluno_id),
  check ((enviada_em is null) = (acertos is null))
);

create index tentativas_aluno_idx on public.tentativas (aluno_id);

-- ---------------------------------------------------------------------------
-- Respostas e domínio passam a apontar para o aluno (uuid) e a tentativa
-- ---------------------------------------------------------------------------

-- aluno_pseudonimo continua sendo a chave do BKT: para alunos reais é o próprio uuid em texto
-- (RN13: nunca nome ou e-mail). Os dados "demo-" e "sim-" ficam com aluno_id nulo.
alter table public.respostas
  add column aluno_id uuid references public.perfis (id),
  add column tentativa_id uuid references public.tentativas (id),
  add constraint respostas_aluno_id_pseudonimo check (aluno_id is null or aluno_pseudonimo = aluno_id::text);

alter table public.dominio
  add column aluno_id uuid references public.perfis (id) on delete cascade,
  add constraint dominio_aluno_id_pseudonimo check (aluno_id is null or aluno_pseudonimo = aluno_id::text);

create index respostas_aluno_idx on public.respostas (aluno_id) where aluno_id is not null;
create index respostas_tentativa_idx on public.respostas (tentativa_id) where tentativa_id is not null;
create index dominio_aluno_idx on public.dominio (aluno_id, turma_id) where aluno_id is not null;

drop function public.registrar_resposta(text, text, boolean, uuid);

create function public.registrar_resposta(
  aluno text, conceito text, correta boolean, turma uuid default null,
  aluno_id uuid default null, tentativa uuid default null
)
returns table (p_dominio numeric, estado text)
language plpgsql
set search_path = ''
as $$
declare
  par public.bkt_parametros;
  atual public.dominio;
  novo numeric;
begin
  select * into par from public.bkt_parametros b
   where b.conceito in (registrar_resposta.conceito, '*')
   order by (b.conceito = '*')  -- o específico (false) vem antes do padrão
   limit 1;
  if not found then
    raise exception 'bkt_parametros sem a linha padrão ''*''';
  end if;

  -- Garante a linha e a trava, para respostas simultâneas do mesmo aluno não se perderem.
  insert into public.dominio (turma_id, aluno_pseudonimo, aluno_id, conceito, p_dominio, n_respostas)
  values (turma, aluno, registrar_resposta.aluno_id, registrar_resposta.conceito, par.p_l0, 0)
  on conflict do nothing;

  select * into atual from public.dominio d
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito
   for update;

  insert into public.respostas (turma_id, aluno_pseudonimo, aluno_id, tentativa_id, conceito, correta, tentativa)
  values (turma, aluno, registrar_resposta.aluno_id, registrar_resposta.tentativa, registrar_resposta.conceito,
          correta, atual.n_respostas + 1);

  novo := public.bkt_passo(atual.p_dominio, correta, par.p_t, par.p_g, par.p_s);

  update public.dominio d
     set p_dominio = novo, n_respostas = atual.n_respostas + 1, atualizado_em = now()
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito;

  return query select novo, public.estado_dominio(novo);
end;
$$;

-- ---------------------------------------------------------------------------
-- Envio da tentativa (correção + BKT, numa transação)
-- ---------------------------------------------------------------------------

-- Erros (a aluno-api traduz para os códigos do contrato):
--   LU403 nao_matriculado (tentativa de outro aluno ou inexistente) · LU409 tentativa_ja_enviada ·
--   LU410 sessao_encerrada · LU408 tempo_esgotado (com 30 s de tolerância para a rede).
-- Múltipla escolha sem resposta conta no total e como erro na nota, mas não entra no BKT (não é
-- evidência). Cada conceito de uma questão respondida chama registrar_resposta.
create function public.enviar_tentativa(tentativa uuid, aluno uuid, respostas jsonb)
returns table (acertos int, total_multipla_escolha int, abertas_aguardando_correcao int)
language plpgsql
set search_path = ''
as $$
declare
  t public.tentativas;
  s public.sessoes;
  atv public.atividades;
  q record;
  resposta jsonb;
  alternativa text;
  indice int;
  correta boolean;
  conceito text;
  n_acertos int := 0;
  n_mc int := 0;
  n_abertas int := 0;
  registro jsonb := '[]'::jsonb;
begin
  select * into t from public.tentativas x where x.id = enviar_tentativa.tentativa for update;
  if not found or t.aluno_id <> aluno then
    raise exception 'Tentativa não encontrada para este aluno.' using errcode = 'LU403';
  end if;
  if t.enviada_em is not null then
    raise exception 'Esta tentativa já foi enviada.' using errcode = 'LU409';
  end if;

  select * into s from public.sessoes x where x.id = t.sessao_id;
  if not s.aberta then
    raise exception 'A sessão foi encerrada pelo professor.' using errcode = 'LU410';
  end if;
  if t.prazo_em is not null and now() > t.prazo_em + interval '30 seconds' then
    raise exception 'O tempo da atividade acabou.' using errcode = 'LU408';
  end if;

  select * into atv from public.atividades x where x.id = s.atividade_id;

  for q in select questao, i::text as id from jsonb_array_elements(atv.questoes) with ordinality as e (questao, i) loop
    select r into resposta
      from jsonb_array_elements(coalesce(respostas, '[]'::jsonb)) r
     where r ->> 'questao_id' = q.id
     limit 1;

    if q.questao ->> 'tipo' = 'multipla_escolha' then
      n_mc := n_mc + 1;
      alternativa := lower(trim(resposta ->> 'alternativa_id'));
      indice := case when alternativa ~ '^[a-z]$' then ascii(alternativa) - 96 end;
      if indice is null or indice > jsonb_array_length(q.questao -> 'alternativas') then
        registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'multipla_escolha',
          'alternativa_id', null, 'correta', false, 'situacao', 'sem_resposta');
        continue;
      end if;
      correta := coalesce((q.questao -> 'alternativas' -> (indice - 1) ->> 'correta')::boolean, false);
      if correta then n_acertos := n_acertos + 1; end if;
      for conceito in select jsonb_array_elements_text(q.questao -> 'conceitos') loop
        perform public.registrar_resposta(aluno::text, trim(conceito), correta, atv.turma_id, aluno, t.id);
      end loop;
      registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'multipla_escolha',
        'alternativa_id', alternativa, 'correta', correta, 'situacao', 'corrigida');
    else
      n_abertas := n_abertas + 1;
      registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'aberta',
        'texto', left(coalesce(resposta ->> 'texto', ''), 10000), 'correta', null,
        'situacao', 'aguardando_correcao');
    end if;
  end loop;

  update public.tentativas x
     set enviada_em = now(), acertos = n_acertos, total_multipla_escolha = n_mc,
         abertas_aguardando_correcao = n_abertas, respostas = registro
   where x.id = t.id;

  return query select n_acertos, n_mc, n_abertas;
end;
$$;

alter table public.sessoes enable row level security;
alter table public.tentativas enable row level security;
revoke all on table public.sessoes, public.tentativas from anon, authenticated;

revoke execute on function
  public.questoes_sem_gabarito(jsonb),
  public.publicar_atividade(uuid, bigint, boolean, int, text),
  public.registrar_resposta(text, text, boolean, uuid, uuid, uuid),
  public.enviar_tentativa(uuid, uuid, jsonb)
  from public, anon, authenticated;
grant execute on function
  public.questoes_sem_gabarito(jsonb),
  public.publicar_atividade(uuid, bigint, boolean, int, text),
  public.registrar_resposta(text, text, boolean, uuid, uuid, uuid),
  public.enviar_tentativa(uuid, uuid, jsonb)
  to service_role;
