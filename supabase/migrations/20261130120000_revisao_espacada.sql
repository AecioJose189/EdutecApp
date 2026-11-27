-- Revisão espaçada com flashcards (fase 4; docs/contrato-api-fase2.md, seção "Revisão espaçada").
--
-- Regras:
--   - Só questões de MÚLTIPLA ESCOLHA já publicadas (RN04), de atividades das turmas do aluno com o
--     gabarito liberado: pelo menos uma sessão ENCERRADA e nenhuma aberta agora (se o professor reabrir
--     a atividade em sala, os cartões dela somem até a sessão ser encerrada de novo: nada vaza).
--   - Nada é gerado (sem Gemini): o cartão é a própria questão publicada.
--   - O treino NÃO entra no BKT nem em respostas/dominio: fica em treinos (só inserção).
--   - Caixas de Leitner 1..3: acerto sobe uma caixa (máximo 3), erro volta para a 1; a próxima revisão
--     fica para daqui a 1, 3 ou 7 dias (caixa 1, 2, 3). Só conta para a caixa a resposta a um cartão que
--     está para hoje (vencido ou novo) e ainda não revisado hoje; as outras só são registradas.
--   - "Hoje" = dia em America/Sao_Paulo. As funções aceitam `hoje` (só para os testes; a aluno-api não
--     passa) para simular os dias seguintes.
--
-- Cartões: um por aluno × atividade × questão. O id é determinístico (md5 de aluno:atividade:questão),
-- então o baralho é só leitura: a linha em `cartoes` nasce na primeira resposta. A ordem das alternativas
-- do cartão é fixa (chave_alternativa com o id do cartão), como a das tentativas.
--
-- Conceitos: treinos.conceitos guarda os nomes do momento do treino; as contagens por conceito usam os
-- conceitos ATUAIS da questão (atividades.questoes), que acompanham as fusões de conceitos.
--
-- Tudo só para a service_role (aluno-api e turma-api). O aluno lê só as próprias linhas pelo RLS.

-- ---------------------------------------------------------------------------
-- Tabelas
-- ---------------------------------------------------------------------------

create table public.cartoes (
  id uuid primary key,
  aluno_id uuid not null references public.perfis (id) on delete cascade,
  turma_id uuid not null references public.turmas (id) on delete cascade,
  atividade_id uuid not null references public.atividades (id) on delete cascade,
  questao_id text not null check (questao_id ~ '^[1-9][0-9]*$'),
  caixa int not null default 1 check (caixa between 1 and 3),
  proxima_revisao date not null,
  ultima_revisao date,
  acertos int not null default 0 check (acertos >= 0),
  erros int not null default 0 check (erros >= 0),
  criado_em timestamptz not null default now(),
  unique (aluno_id, atividade_id, questao_id)
);

create index cartoes_aluno_turma_idx on public.cartoes (aluno_id, turma_id, proxima_revisao);
create index cartoes_turma_idx on public.cartoes (turma_id);

create table public.treinos (
  id bigint generated always as identity primary key,
  aluno_id uuid not null references public.perfis (id),
  turma_id uuid not null references public.turmas (id),
  atividade_id uuid not null references public.atividades (id),
  questao_id text not null,
  cartao_id uuid not null,
  conceitos text[] not null,
  alternativa_id text not null,
  acertou boolean not null,
  confianca text check (confianca in ('certeza', 'acho', 'chute')),
  contou_para_caixa boolean not null,
  caixa_depois int not null check (caixa_depois between 1 and 3),
  criado_em timestamptz not null default now()
);

create index treinos_turma_idx on public.treinos (turma_id, criado_em);
create index treinos_aluno_idx on public.treinos (aluno_id, criado_em);

-- Só inserção, como respostas: nem UPDATE nem DELETE (apagar a turma ou a atividade com treinos fica
-- barrado pela chave estrangeira, como acontece com respostas de alunos reais).
create function public.treinos_somente_insercao() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception 'treinos é só de inserção (% não permitido)', tg_op
    using errcode = 'insufficient_privilege';
end;
$$;

create trigger treinos_somente_insercao
  before update or delete on public.treinos
  for each row execute function public.treinos_somente_insercao();

alter table public.cartoes enable row level security;
alter table public.treinos enable row level security;
revoke all on table public.cartoes, public.treinos from anon, authenticated;
grant select on public.cartoes, public.treinos to authenticated;

create policy cartoes_ler_os_proprios on public.cartoes for select to authenticated
  using (aluno_id = (select auth.uid()));
create policy treinos_ler_os_proprios on public.treinos for select to authenticated
  using (aluno_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Auxiliares
-- ---------------------------------------------------------------------------

create function privado.hoje_local() returns date
language sql
stable
set search_path = ''
as $$
  select (now() at time zone 'America/Sao_Paulo')::date;
$$;

create function privado.dia_local(momento timestamptz) returns date
language sql
stable
set search_path = ''
as $$
  select (momento at time zone 'America/Sao_Paulo')::date;
$$;

-- Dias até a próxima revisão em cada caixa de Leitner.
create function privado.intervalo_caixa(caixa int) returns int
language sql
immutable
set search_path = ''
as $$
  select case caixa when 1 then 1 when 2 then 3 else 7 end;
$$;

create function privado.cartao_id(aluno uuid, atividade uuid, questao text) returns uuid
language sql
immutable
set search_path = ''
as $$
  select md5('cartao:' || aluno::text || ':' || atividade::text || ':' || questao)::uuid;
$$;

-- Questões que o aluno pode treinar (sem gabarito: vêm de questoes_aluno), com o id do cartão.
create function privado.questoes_treinaveis(aluno uuid, turma uuid default null)
returns table (turma_id uuid, atividade_id uuid, numero int, titulo text, questao_id text, questao jsonb,
               conceitos text[], cartao_id uuid)
language sql
stable
set search_path = ''
as $$
  select a.turma_id, a.id, public.numero_da_atividade(a.id), a.titulo, q.questao ->> 'id', q.questao,
         array(select trim(c) from jsonb_array_elements_text(q.questao -> 'conceitos') c),
         privado.cartao_id(questoes_treinaveis.aluno, a.id, q.questao ->> 'id')
    from public.membros_turma m
    join public.atividades a on a.turma_id = m.turma_id
    cross join lateral jsonb_array_elements(a.questoes_aluno) q (questao)
   where m.aluno_id = questoes_treinaveis.aluno
     and (questoes_treinaveis.turma is null or m.turma_id = questoes_treinaveis.turma)
     and q.questao ->> 'tipo' = 'multipla_escolha'
     and jsonb_typeof(q.questao -> 'alternativas') = 'array'
     and jsonb_array_length(q.questao -> 'alternativas') >= 2
     and exists (select 1 from public.sessoes s where s.atividade_id = a.id and not s.aberta)
     and not exists (select 1 from public.sessoes s where s.atividade_id = a.id and s.aberta);
$$;

-- Alternativas (sem gabarito) na ordem fixa do cartão.
create function privado.alternativas_do_cartao(cartao uuid, questao jsonb) returns jsonb
language sql
immutable
set search_path = ''
as $$
  select coalesce(jsonb_agg(alt order by public.chave_alternativa(cartao, questao ->> 'id', alt ->> 'id')), '[]'::jsonb)
    from jsonb_array_elements(questao -> 'alternativas') alt;
$$;

-- ---------------------------------------------------------------------------
-- Baralho
-- ---------------------------------------------------------------------------

-- Cartões para treinar agora: primeiro os vencidos (hoje ou antes; os mais atrasados e de caixa menor
-- primeiro), depois os novos das questões ligadas a conceitos em que o domínio estimado do aluno está
-- em "revisar" (antes) ou "em desenvolvimento" (menor domínio primeiro). Até `limite` (1..50).
--   pendentes_hoje = vencidos + novos disponíveis (sem o limite)
--   total_cartoes  = cartões que o aluno já tem (respondeu ao menos uma vez), nas atividades treináveis
create function public.revisao_baralho(aluno uuid, turma uuid default null, limite int default 10,
                                       hoje date default null) returns jsonb
language sql
stable
set search_path = ''
as $$
  with dia as (select coalesce(revisao_baralho.hoje, privado.hoje_local()) as d),
  q as (select * from privado.questoes_treinaveis(revisao_baralho.aluno, revisao_baralho.turma)),
  existentes as (
    select q.*, c.caixa, c.proxima_revisao
      from q join public.cartoes c on c.id = q.cartao_id and c.aluno_id = revisao_baralho.aluno
  ),
  vencidos as (
    select e.*, 0 as grupo, null::numeric as pior
      from existentes e, dia where e.proxima_revisao <= dia.d
  ),
  novos as (
    select q.*, 1 as caixa, null::date as proxima_revisao,
           case when min(d.p_dominio) < 0.5 then 1 else 2 end as grupo, min(d.p_dominio) as pior
      from q
      join public.dominio d on d.turma_id = q.turma_id and d.aluno_id = revisao_baralho.aluno
                           and d.conceito = any (q.conceitos)
     where not exists (select 1 from public.cartoes c where c.id = q.cartao_id)
       and public.estado_dominio(d.p_dominio) in ('revisar', 'em_desenvolvimento')
     group by q.turma_id, q.atividade_id, q.numero, q.titulo, q.questao_id, q.questao, q.conceitos, q.cartao_id
  ),
  todos as (
    select v.*, row_number() over (order by v.grupo, v.proxima_revisao nulls last, v.caixa, v.pior nulls last,
                                            v.numero, v.questao_id::int, v.turma_id) as ordem
      from (select * from vencidos union all select * from novos) v
  )
  select jsonb_build_object(
    'cartoes', coalesce((
      select jsonb_agg(jsonb_build_object(
          'cartao_id', t.cartao_id,
          'turma', jsonb_build_object('id', tu.id, 'nome', tu.nome, 'codigo', tu.codigo),
          'atividade', jsonb_build_object('id', t.atividade_id, 'numero', t.numero, 'titulo', t.titulo),
          'questao_id', t.questao_id,
          'enunciado', t.questao ->> 'enunciado',
          'conceitos', to_jsonb(t.conceitos),
          'alternativas', privado.alternativas_do_cartao(t.cartao_id, t.questao),
          'caixa', t.caixa,
          'novo', t.grupo > 0) order by t.ordem)
        from todos t join public.turmas tu on tu.id = t.turma_id
       where t.ordem <= greatest(1, least(coalesce(revisao_baralho.limite, 10), 50))), '[]'::jsonb),
    'pendentes_hoje', (select count(*) from todos),
    'total_cartoes', (select count(*) from existentes)
  );
$$;

-- ---------------------------------------------------------------------------
-- Responder um cartão
-- ---------------------------------------------------------------------------

-- Erros: LU403 cartão que não é do aluno, inexistente ou de atividade que não pode ser treinada agora;
-- LU400 alternativa que não existe na questão.
create function public.revisao_responder(aluno uuid, cartao uuid, alternativa text, confianca text default null,
                                         hoje date default null) returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  dia date := coalesce(hoje, privado.hoje_local());
  q record;
  completa jsonb;
  alt text := lower(trim(alternativa));
  indice int;
  correta boolean;
  conf text := lower(trim(confianca));
  c public.cartoes;
  conta boolean;
  nova int;
  proxima date;
begin
  select * into q from privado.questoes_treinaveis(revisao_responder.aluno) x where x.cartao_id = cartao;
  if not found then
    raise exception 'Cartão não encontrado para este aluno.' using errcode = 'LU403';
  end if;

  select a.questoes -> (q.questao_id::int - 1) into completa from public.atividades a where a.id = q.atividade_id;
  indice := case when alt ~ '^[a-z]$' then ascii(alt) - 96 end;
  if indice is null or indice > jsonb_array_length(completa -> 'alternativas') then
    raise exception 'Alternativa inválida.' using errcode = 'LU400';
  end if;
  correta := coalesce((completa -> 'alternativas' -> (indice - 1) ->> 'correta')::boolean, false);
  if conf is null or conf not in ('certeza', 'acho', 'chute') then conf := null; end if;

  insert into public.cartoes (id, aluno_id, turma_id, atividade_id, questao_id, proxima_revisao)
  values (cartao, aluno, q.turma_id, q.atividade_id, q.questao_id, dia)
  on conflict (id) do nothing;
  select * into c from public.cartoes x where x.id = cartao for update;

  -- Conta para a caixa só a primeira resposta a um cartão que está para hoje.
  conta := c.proxima_revisao <= dia and c.ultima_revisao is distinct from dia;
  if conta then
    nova := case when correta then least(c.caixa + 1, 3) else 1 end;
    proxima := dia + privado.intervalo_caixa(nova);
    update public.cartoes x
       set caixa = nova, proxima_revisao = proxima, ultima_revisao = dia,
           acertos = x.acertos + correta::int, erros = x.erros + (not correta)::int
     where x.id = cartao;
  else
    nova := c.caixa;
    proxima := c.proxima_revisao;
  end if;

  insert into public.treinos (aluno_id, turma_id, atividade_id, questao_id, cartao_id, conceitos, alternativa_id,
                              acertou, confianca, contou_para_caixa, caixa_depois)
  values (aluno, q.turma_id, q.atividade_id, q.questao_id, cartao, q.conceitos, alt, correta, conf, conta, nova);

  return jsonb_build_object(
    'acertou', correta,
    'alternativa_correta', (
      select chr(96 + a.j::int) from jsonb_array_elements(completa -> 'alternativas') with ordinality a (alternativa, j)
       where coalesce((a.alternativa ->> 'correta')::boolean, false) order by a.j limit 1),
    'alternativas', (
      select coalesce(jsonb_agg(jsonb_build_object(
          'id', chr(96 + a.j::int), 'texto', a.alternativa ->> 'texto',
          'correta', coalesce((a.alternativa ->> 'correta')::boolean, false),
          'feedback', coalesce(a.alternativa ->> 'feedback', ''))
        order by public.chave_alternativa(cartao, q.questao_id, chr(96 + a.j::int))), '[]'::jsonb)
        from jsonb_array_elements(completa -> 'alternativas') with ordinality a (alternativa, j)),
    'nova_caixa', nova,
    'proxima_revisao', to_char(proxima, 'YYYY-MM-DD'),
    'contou_para_caixa', conta
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Resumos
-- ---------------------------------------------------------------------------

-- Conceitos ATUAIS de uma questão publicada (acompanham as fusões).
create function privado.conceitos_da_questao(atividade uuid, questao text) returns text[]
language sql
stable
set search_path = ''
as $$
  select array(select trim(c) from public.atividades a,
                 jsonb_array_elements_text(a.questoes -> (questao::int - 1) -> 'conceitos') c
                where a.id = atividade);
$$;

-- Resumo do aluno por turma (todas as turmas dele, ou uma).
create function public.revisao_resumo(aluno uuid, turma uuid default null, hoje date default null) returns jsonb
language sql
stable
set search_path = ''
as $$
  with dia as (select coalesce(revisao_resumo.hoje, privado.hoje_local()) as d),
  minhas as (
    select tu.id, tu.nome, tu.codigo, m.entrou_em
      from public.membros_turma m join public.turmas tu on tu.id = m.turma_id
     where m.aluno_id = revisao_resumo.aluno and (revisao_resumo.turma is null or m.turma_id = revisao_resumo.turma)
  ),
  meus as (
    select c.* from public.cartoes c join privado.questoes_treinaveis(revisao_resumo.aluno, revisao_resumo.turma) q
        on q.cartao_id = c.id
  )
  select jsonb_build_object('turmas', coalesce(jsonb_agg(jsonb_build_object(
      'turma', jsonb_build_object('id', mi.id, 'nome', mi.nome, 'codigo', mi.codigo),
      'pendentes_hoje', (public.revisao_baralho(revisao_resumo.aluno, mi.id, 1, dia.d) -> 'pendentes_hoje')::int,
      'total_cartoes', (select count(*) from meus c where c.turma_id = mi.id),
      'cartoes_por_caixa', jsonb_build_object(
        '1', (select count(*) from meus c where c.turma_id = mi.id and c.caixa = 1),
        '2', (select count(*) from meus c where c.turma_id = mi.id and c.caixa = 2),
        '3', (select count(*) from meus c where c.turma_id = mi.id and c.caixa = 3)),
      'treinos_7_dias', (select count(*) from public.treinos t
                          where t.aluno_id = revisao_resumo.aluno and t.turma_id = mi.id
                            and privado.dia_local(t.criado_em) > dia.d - 7),
      'acertos_7_dias', (select count(*) filter (where t.acertou) from public.treinos t
                          where t.aluno_id = revisao_resumo.aluno and t.turma_id = mi.id
                            and privado.dia_local(t.criado_em) > dia.d - 7),
      'conceitos_com_mais_cartoes', coalesce((
        select jsonb_agg(jsonb_build_object('conceito', x.conceito, 'n_cartoes', x.n) order by x.n desc, x.conceito)
          from (select k.conceito, count(*)::int as n
                  from meus c cross join lateral unnest(privado.conceitos_da_questao(c.atividade_id, c.questao_id)) k (conceito)
                 where c.turma_id = mi.id group by k.conceito order by n desc, k.conceito limit 5) x), '[]'::jsonb)
    ) order by mi.entrou_em, mi.id), '[]'::jsonb))
    from minhas mi, dia;
$$;

-- Treino da turma para o professor (a turma-api confere o dono). Só alunos reais (membros).
--   resumo:    n_alunos, n_alunos_treinando (treinaram nos últimos 7 dias), treinos_7_dias, n_treinos, taxa_acerto
--   alunos:    por membro: treinos_7_dias, n_treinos, acertos, taxa_acerto (null sem treinos), cartões por caixa,
--              ultimo_treino_em
--   conceitos: conceitos (atuais) treinados nos últimos 7 dias: n_alunos_treinando, treinos_7_dias
create function public.treino_turma(turma uuid, hoje date default null) returns jsonb
language sql
stable
set search_path = ''
as $$
  with dia as (select coalesce(treino_turma.hoje, privado.hoje_local()) as d),
  membros as (
    select m.aluno_id, coalesce(p.nome, 'Aluno') as nome
      from public.membros_turma m left join public.perfis p on p.id = m.aluno_id
     where m.turma_id = treino_turma.turma
  ),
  tr as (
    select t.*, privado.dia_local(t.criado_em) > dia.d - 7 as recente
      from public.treinos t, dia
     where t.turma_id = treino_turma.turma and t.aluno_id in (select aluno_id from membros)
  ),
  por_conceito as (
    select k.conceito, tr.aluno_id
      from tr cross join lateral unnest(privado.conceitos_da_questao(tr.atividade_id, tr.questao_id)) k (conceito)
     where tr.recente
  )
  select jsonb_build_object(
    'resumo', jsonb_build_object(
      'n_alunos', (select count(*) from membros),
      'n_alunos_treinando', (select count(distinct aluno_id) from tr where recente),
      'treinos_7_dias', (select count(*) from tr where recente),
      'n_treinos', (select count(*) from tr),
      'taxa_acerto', (select round(avg(acertou::int), 4) from tr)),
    'alunos', coalesce((select jsonb_agg(jsonb_build_object(
        'aluno_id', mb.aluno_id,
        'nome', mb.nome,
        'treinos_7_dias', (select count(*) from tr where tr.aluno_id = mb.aluno_id and recente),
        'n_treinos', (select count(*) from tr where tr.aluno_id = mb.aluno_id),
        'acertos', (select count(*) filter (where acertou) from tr where tr.aluno_id = mb.aluno_id),
        'taxa_acerto', (select round(avg(acertou::int), 4) from tr where tr.aluno_id = mb.aluno_id),
        'cartoes_por_caixa', jsonb_build_object(
          '1', (select count(*) from public.cartoes c where c.turma_id = treino_turma.turma and c.aluno_id = mb.aluno_id and c.caixa = 1),
          '2', (select count(*) from public.cartoes c where c.turma_id = treino_turma.turma and c.aluno_id = mb.aluno_id and c.caixa = 2),
          '3', (select count(*) from public.cartoes c where c.turma_id = treino_turma.turma and c.aluno_id = mb.aluno_id and c.caixa = 3)),
        'ultimo_treino_em', (select to_char(max(criado_em) at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
                               from tr where tr.aluno_id = mb.aluno_id))
      order by mb.nome, mb.aluno_id) from membros mb), '[]'::jsonb),
    'conceitos', coalesce((select jsonb_agg(jsonb_build_object(
        'conceito', x.conceito, 'n_alunos_treinando', x.n_alunos, 'treinos_7_dias', x.n)
      order by x.n_alunos desc, x.n desc, x.conceito)
      from (select conceito, count(distinct aluno_id)::int as n_alunos, count(*)::int as n
              from por_conceito group by conceito) x), '[]'::jsonb)
  );
$$;

-- Treinos de um aluno num conceito (conceito atual da questão), para o histórico do mapa do aluno.
-- aluno = a chave do mapa (uuid em texto; a simulação não treina e dá zeros).
create function public.treino_aluno_conceito(turma uuid, aluno text, conceito text) returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object('n_treinos', count(*), 'acertos', count(*) filter (where t.acertou))
    from public.treinos t
   where t.turma_id = treino_aluno_conceito.turma
     and t.aluno_id::text = treino_aluno_conceito.aluno
     and treino_aluno_conceito.conceito = any (privado.conceitos_da_questao(t.atividade_id, t.questao_id));
$$;

revoke execute on function
  public.treinos_somente_insercao(),
  privado.hoje_local(),
  privado.dia_local(timestamptz),
  privado.intervalo_caixa(int),
  privado.cartao_id(uuid, uuid, text),
  privado.questoes_treinaveis(uuid, uuid),
  privado.alternativas_do_cartao(uuid, jsonb),
  privado.conceitos_da_questao(uuid, text),
  public.revisao_baralho(uuid, uuid, int, date),
  public.revisao_responder(uuid, uuid, text, text, date),
  public.revisao_resumo(uuid, uuid, date),
  public.treino_turma(uuid, date),
  public.treino_aluno_conceito(uuid, text, text)
  from public, anon, authenticated;
grant execute on function
  privado.hoje_local(),
  privado.dia_local(timestamptz),
  privado.intervalo_caixa(int),
  privado.cartao_id(uuid, uuid, text),
  privado.questoes_treinaveis(uuid, uuid),
  privado.alternativas_do_cartao(uuid, jsonb),
  privado.conceitos_da_questao(uuid, text),
  public.revisao_baralho(uuid, uuid, int, date),
  public.revisao_responder(uuid, uuid, text, text, date),
  public.revisao_resumo(uuid, uuid, date),
  public.treino_turma(uuid, date),
  public.treino_aluno_conceito(uuid, text, text)
  to service_role;
