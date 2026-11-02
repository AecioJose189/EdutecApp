-- O domínio estimado passa a ser por turma: o mesmo aluno (pseudônimo) pode estar em várias turmas
-- e o mesmo nome de conceito pode existir em turmas diferentes.
-- turma_id nulo = dados antigos da demonstração do BKT (supabase/tests/bkt_demo.sql), que continuam
-- funcionando como antes.

-- ---------------------------------------------------------------------------
-- Tabelas
-- ---------------------------------------------------------------------------

alter table public.respostas
  add column turma_id uuid references public.turmas (id) on delete cascade;

drop index public.respostas_aluno_conceito_idx;
create index respostas_turma_aluno_conceito_idx on public.respostas (turma_id, aluno_pseudonimo, conceito, id);

alter table public.dominio
  add column turma_id uuid references public.turmas (id) on delete cascade,
  drop constraint dominio_pkey;

-- "nulls not distinct": as linhas antigas (turma nula) continuam únicas por aluno e conceito.
alter table public.dominio
  add constraint dominio_turma_aluno_conceito_key unique nulls not distinct (turma_id, aluno_pseudonimo, conceito);

-- Apagar respostas: além da demo do BKT ("demo-..."), a simulação de turma ("sim-...") pode ser
-- refeita. Continua exigindo a chave de sessão edutec.limpar_demo = 'on'.
create or replace function public.respostas_somente_insercao() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'DELETE'
     and (old.aluno_pseudonimo like 'demo-%' or old.aluno_pseudonimo like 'sim-%')
     and current_setting('edutec.limpar_demo', true) = 'on' then
    return old;
  end if;
  raise exception 'respostas é só de inserção (% não permitido)', tg_op
    using errcode = 'insufficient_privilege';
end;
$$;

-- ---------------------------------------------------------------------------
-- registrar_resposta com turma (sem turma = comportamento antigo)
-- ---------------------------------------------------------------------------

drop function public.registrar_resposta(text, text, boolean);

create function public.registrar_resposta(aluno text, conceito text, correta boolean, turma uuid default null)
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
  insert into public.dominio (turma_id, aluno_pseudonimo, conceito, p_dominio, n_respostas)
  values (turma, aluno, registrar_resposta.conceito, par.p_l0, 0)
  on conflict do nothing;

  select * into atual from public.dominio d
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito
   for update;

  insert into public.respostas (turma_id, aluno_pseudonimo, conceito, correta, tentativa)
  values (turma, aluno, registrar_resposta.conceito, correta, atual.n_respostas + 1);

  novo := public.bkt_passo(atual.p_dominio, correta, par.p_t, par.p_g, par.p_s);

  update public.dominio d
     set p_dominio = novo, n_respostas = atual.n_respostas + 1, atualizado_em = now()
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito;

  return query select novo, public.estado_dominio(novo);
end;
$$;

revoke execute on function public.registrar_resposta(text, text, boolean, uuid) from public, anon, authenticated;
grant execute on function public.registrar_resposta(text, text, boolean, uuid) to service_role;

-- ---------------------------------------------------------------------------
-- Visões
-- ---------------------------------------------------------------------------

drop view public.dominio_turma;

-- Por turma e conceito. security_invoker: respeita o RLS de quem consulta.
create view public.dominio_turma
with (security_invoker = true)
as
select
  d.turma_id,
  d.conceito,
  count(*) as n_alunos,
  sum(d.n_respostas)::int as n_respostas,
  round(avg(d.p_dominio), 4) as media_dominio,
  round(percentile_cont(0.5) within group (order by d.p_dominio)::numeric, 4) as mediana_dominio,
  public.estado_dominio(avg(d.p_dominio)) as estado_da_media,
  -- Moda do estado entre os alunos. Em empate, mode() fica com o primeiro na ordem, isto é,
  -- o mais preocupante (revisar → em_desenvolvimento → consolidado).
  (array['revisar', 'em_desenvolvimento', 'consolidado'])[
    mode() within group (order by case public.estado_dominio(d.p_dominio)
      when 'revisar' then 1 when 'em_desenvolvimento' then 2 else 3 end)
  ] as estado_moda,
  count(*) filter (where public.estado_dominio(d.p_dominio) = 'revisar')::int as n_revisar
from public.dominio d
group by d.turma_id, d.conceito;

-- Resumo de cada turma para Minhas turmas: alunos e conceitos por estado (da média da turma).
create view public.resumo_turmas
with (security_invoker = true)
as
select
  t.id as turma_id,
  (select count(distinct d.aluno_pseudonimo) from public.dominio d where d.turma_id = t.id)::int as n_alunos,
  count(dt.conceito) filter (where dt.estado_da_media = 'consolidado')::int as n_consolidado,
  count(dt.conceito) filter (where dt.estado_da_media = 'em_desenvolvimento')::int as n_desenvolvimento,
  count(dt.conceito) filter (where dt.estado_da_media = 'revisar')::int as n_revisar
from public.turmas t
left join public.dominio_turma dt on dt.turma_id = t.id
group by t.id;

revoke all on table public.dominio_turma, public.resumo_turmas from anon, authenticated;
