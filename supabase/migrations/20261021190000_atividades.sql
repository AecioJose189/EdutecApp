-- Atividades publicadas e o grafo de conceitos de cada turma.
-- Acesso só pela Edge Function turma-api (service_role); RLS ligado e nenhuma política.

create table public.atividades (
  id uuid primary key default gen_random_uuid(),
  turma_id uuid not null references public.turmas (id) on delete cascade,
  geracao_id bigint not null references public.geracoes (id),
  questoes jsonb not null check (jsonb_typeof(questoes) = 'array' and jsonb_array_length(questoes) > 0),
  -- Configurações de publicação (mínimo da demo). TODO: prazo e data de liberação.
  permitir_refazer boolean not null default true,
  tempo_minutos int check (tempo_minutos > 0),  -- null = sem limite
  publicada_em timestamptz not null default now(),
  -- A mesma geração não é publicada duas vezes na mesma turma (evita toque duplo em "Publicar").
  unique (turma_id, geracao_id)
);

create index atividades_turma_idx on public.atividades (turma_id, publicada_em);

-- Grafo de conhecimento da turma: conceitos e pré-requisitos (por nome, dentro da turma).
create table public.conceitos_turma (
  turma_id uuid not null references public.turmas (id) on delete cascade,
  nome text not null check (length(trim(nome)) > 0),
  prerequisitos text[] not null default '{}',
  criado_em timestamptz not null default now(),
  primary key (turma_id, nome)
);

alter table public.atividades enable row level security;
alter table public.conceitos_turma enable row level security;
revoke all on table public.atividades, public.conceitos_turma from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Publicação
-- ---------------------------------------------------------------------------

-- Publica uma geração aprovada pelo professor (RN04) numa turma, de forma atômica:
--   1. grava a atividade com as questões da geração;
--   2. junta os conceitos da geração aos da turma (mesmo nome = mesmo conceito; pré-requisitos
--      somados, sem duplicar);
--   3. rejeita a publicação se o grafo resultante tiver ciclo (RN03), desfazendo tudo.
-- Erros: ED404 (turma ou geração não existe), ED409 (já publicada), ED003 (ciclo).
create function public.publicar_atividade(
  turma uuid, geracao bigint, permitir_refazer boolean, tempo_minutos int
) returns uuid
language plpgsql
set search_path = ''
as $$
declare
  resultado jsonb;
  nova uuid;
  ciclo text[];
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

  begin
    insert into public.atividades (turma_id, geracao_id, questoes, permitir_refazer, tempo_minutos)
    values (turma, geracao, resultado -> 'questoes', publicar_atividade.permitir_refazer,
            publicar_atividade.tempo_minutos)
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

-- Um ciclo de pré-requisitos da turma (ex.: {A, B, A}), ou null se o grafo não tiver ciclo.
create function public.ciclo_conceitos(turma uuid) returns text[]
language sql
stable
set search_path = ''
as $$
  with recursive
  arestas as (  -- conceito → pré-requisito
    select c.nome, unnest(c.prerequisitos) as pre from public.conceitos_turma c where c.turma_id = turma
  ),
  caminho (origem, atual, visitados) as (
    select a.nome, a.nome, array[a.nome] from (select distinct nome from arestas) a
    union all
    select c.origem, a.pre, c.visitados || a.pre
      from caminho c join arestas a on a.nome = c.atual
     where not a.pre = any (c.visitados)
  )
  select c.visitados || c.origem
    from caminho c join arestas a on a.nome = c.atual and a.pre = c.origem
   limit 1;
$$;

revoke execute on function
  public.publicar_atividade(uuid, bigint, boolean, int),
  public.ciclo_conceitos(uuid)
  from public, anon, authenticated;
grant execute on function
  public.publicar_atividade(uuid, bigint, boolean, int),
  public.ciclo_conceitos(uuid)
  to service_role;
