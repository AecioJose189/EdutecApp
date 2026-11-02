-- Knowledge Tracing mínimo: BKT (Bayesian Knowledge Tracing) no Postgres.
--
-- Para cada (aluno, conceito), P(L) = probabilidade estimada de o aluno dominar o conceito
-- ("domínio estimado", não é nota). A cada resposta:
--   acerto: P(L|obs) = P(L)·(1−S) / [P(L)·(1−S) + (1−P(L))·G]
--   erro:   P(L|obs) = P(L)·S     / [P(L)·S     + (1−P(L))·(1−G)]
--   depois, transição (aprendizado): P(L) ← P(L|obs) + (1 − P(L|obs))·T
-- com L0 = P(L) inicial, T = aprender, G = chute, S = deslize.
--
-- Acesso: RLS ligado e nenhuma política; funções e view sem GRANT para anon/authenticated.
-- Só a service_role (Edge Functions) e o SQL Editor (postgres) usam por enquanto.

-- ---------------------------------------------------------------------------
-- Parâmetros
-- ---------------------------------------------------------------------------

create table public.bkt_parametros (
  conceito text primary key,  -- '*' = padrão para conceitos sem parâmetros próprios
  p_l0 numeric not null check (p_l0 > 0 and p_l0 < 1),
  p_t  numeric not null check (p_t >= 0 and p_t < 1),
  p_g  numeric not null check (p_g > 0 and p_g < 1),
  p_s  numeric not null check (p_s > 0 and p_s < 1),
  atualizado_em timestamptz not null default now(),
  -- Sem isso o modelo fica degenerado (acertar seria evidência de NÃO saber).
  check (p_g + p_s < 1)
);

comment on table public.bkt_parametros is
  'Parâmetros do BKT por conceito. A linha ''*'' é o padrão; o ajuste fino (pyBKT) virá depois.';

insert into public.bkt_parametros (conceito, p_l0, p_t, p_g, p_s)
values ('*', 0.3, 0.1, 0.2, 0.1);

-- ---------------------------------------------------------------------------
-- Respostas (só inserção)
-- ---------------------------------------------------------------------------

create table public.respostas (
  id bigint generated always as identity primary key,
  -- RN13: pseudônimo, nunca nome, matrícula ou e-mail. A checagem barra o óbvio (e-mail, espaços).
  aluno_pseudonimo text not null check (aluno_pseudonimo ~ '^[A-Za-z0-9_-]{3,64}$'),
  conceito text not null check (length(trim(conceito)) > 0),
  correta boolean not null,
  tentativa int not null check (tentativa >= 1),  -- 1ª, 2ª... resposta do aluno neste conceito
  criado_em timestamptz not null default now()
);

create index respostas_aluno_conceito_idx on public.respostas (aluno_pseudonimo, conceito, id);

-- Append-only: UPDATE nunca; DELETE só dos dados simulados da demonstração (pseudônimo "demo-..."),
-- e só com a chave de sessão edutec.limpar_demo = 'on' (usada por supabase/tests/bkt_demo.sql).
create function public.respostas_somente_insercao() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'DELETE'
     and old.aluno_pseudonimo like 'demo-%'
     and current_setting('edutec.limpar_demo', true) = 'on' then
    return old;
  end if;
  raise exception 'respostas é só de inserção (% não permitido)', tg_op
    using errcode = 'insufficient_privilege';
end;
$$;

create trigger respostas_somente_insercao
  before update or delete on public.respostas
  for each row execute function public.respostas_somente_insercao();

-- ---------------------------------------------------------------------------
-- Domínio estimado atual por aluno e conceito
-- ---------------------------------------------------------------------------

create table public.dominio (
  aluno_pseudonimo text not null,
  conceito text not null,
  p_dominio numeric not null check (p_dominio >= 0 and p_dominio <= 1),
  n_respostas int not null default 0 check (n_respostas >= 0),
  atualizado_em timestamptz not null default now(),
  primary key (aluno_pseudonimo, conceito)
);

-- ---------------------------------------------------------------------------
-- Funções
-- ---------------------------------------------------------------------------

-- PROPOSTA de limiares (a validar com a equipe/professores):
--   < 0,5 → revisar · 0,5 a < 0,8 → em_desenvolvimento · ≥ 0,8 → consolidado
create function public.estado_dominio(p numeric) returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p is null then null
    when p < 0.5 then 'revisar'
    when p < 0.8 then 'em_desenvolvimento'
    else 'consolidado'
  end;
$$;

-- Um passo do BKT, isolado para poder ser testado: P(L) antes → P(L) depois da resposta.
create function public.bkt_passo(p_l numeric, correta boolean, p_t numeric, p_g numeric, p_s numeric)
returns numeric
language sql
immutable
set search_path = ''
as $$
  with posterior as (
    select case
      when correta then (p_l * (1 - p_s)) / (p_l * (1 - p_s) + (1 - p_l) * p_g)
      else (p_l * p_s) / (p_l * p_s + (1 - p_l) * (1 - p_g))
    end as p
  )
  select p + (1 - p) * p_t from posterior;
$$;

-- Registra uma resposta e atualiza o domínio estimado do aluno no conceito.
create function public.registrar_resposta(aluno text, conceito text, correta boolean)
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
  insert into public.dominio (aluno_pseudonimo, conceito, p_dominio, n_respostas)
  values (aluno, registrar_resposta.conceito, par.p_l0, 0)
  on conflict do nothing;

  select * into atual from public.dominio d
   where d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito
   for update;

  insert into public.respostas (aluno_pseudonimo, conceito, correta, tentativa)
  values (aluno, registrar_resposta.conceito, correta, atual.n_respostas + 1);

  novo := public.bkt_passo(atual.p_dominio, correta, par.p_t, par.p_g, par.p_s);

  update public.dominio d
     set p_dominio = novo, n_respostas = atual.n_respostas + 1, atualizado_em = now()
   where d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito;

  return query select novo, public.estado_dominio(novo);
end;
$$;

-- ---------------------------------------------------------------------------
-- Visão da turma (por conceito)
-- ---------------------------------------------------------------------------

-- security_invoker: a view respeita o RLS de quem consulta (sem isso, rodaria como dono e furaria o RLS).
create view public.dominio_turma
with (security_invoker = true)
as
select
  d.conceito,
  count(*) as n_alunos,
  round(avg(d.p_dominio), 4) as media_dominio,
  round(percentile_cont(0.5) within group (order by d.p_dominio)::numeric, 4) as mediana_dominio,
  public.estado_dominio(avg(d.p_dominio)) as estado_da_media,
  -- Moda do estado entre os alunos. Em empate, mode() fica com o primeiro na ordem, isto é,
  -- o mais preocupante (revisar → em_desenvolvimento → consolidado).
  (array['revisar', 'em_desenvolvimento', 'consolidado'])[
    mode() within group (order by case public.estado_dominio(d.p_dominio)
      when 'revisar' then 1 when 'em_desenvolvimento' then 2 else 3 end)
  ] as estado_moda
from public.dominio d
group by d.conceito;

-- ---------------------------------------------------------------------------
-- Acesso
-- ---------------------------------------------------------------------------

alter table public.bkt_parametros enable row level security;
alter table public.respostas enable row level security;
alter table public.dominio enable row level security;

revoke all on table public.bkt_parametros, public.respostas, public.dominio, public.dominio_turma
  from anon, authenticated;

revoke execute on function
  public.estado_dominio(numeric),
  public.bkt_passo(numeric, boolean, numeric, numeric, numeric),
  public.registrar_resposta(text, text, boolean),
  public.respostas_somente_insercao()
  from public, anon, authenticated;

grant execute on function
  public.estado_dominio(numeric),
  public.bkt_passo(numeric, boolean, numeric, numeric, numeric),
  public.registrar_resposta(text, text, boolean)
  to service_role;
