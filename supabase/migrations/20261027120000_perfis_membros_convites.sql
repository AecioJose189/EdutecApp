-- MVP com login (Google via Supabase Auth): perfis, dono da turma (professor), membros (alunos)
-- e convites. O papel é POR TURMA: quem criou a turma é o professor dela; quem aceitou o convite é
-- aluno dela. A mesma conta pode ser professor numa turma e aluno em outra.
--
-- As políticas de RLS ficam na migration ..140000_rls_politicas.sql.

-- ---------------------------------------------------------------------------
-- Perfis (um por usuário do Auth, criado por gatilho no cadastro)
-- ---------------------------------------------------------------------------

create table public.perfis (
  id uuid primary key references auth.users (id) on delete cascade,
  nome text not null,
  email text,
  avatar_url text,
  criado_em timestamptz not null default now()
);

comment on table public.perfis is
  'Dados pessoais vindos do login Google. RN13: nunca enviados ao Gemini; nas respostas e no BKT o aluno é só o uuid.';

create function public.criar_perfil() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.perfis (id, nome, email, avatar_url)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
             nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
             split_part(coalesce(new.email, 'Usuário'), '@', 1)),
    new.email,
    coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger criar_perfil
  after insert on auth.users
  for each row execute function public.criar_perfil();

-- Usuários que já existam antes desta migration.
insert into public.perfis (id, nome, email, avatar_url)
select u.id,
       coalesce(nullif(trim(u.raw_user_meta_data ->> 'full_name'), ''),
                nullif(trim(u.raw_user_meta_data ->> 'name'), ''),
                split_part(coalesce(u.email, 'Usuário'), '@', 1)),
       u.email,
       coalesce(u.raw_user_meta_data ->> 'avatar_url', u.raw_user_meta_data ->> 'picture')
  from auth.users u
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Turmas ganham dono
-- ---------------------------------------------------------------------------

-- As turmas de teste da demo sem login não têm professor: saem (com atividades, conceitos e as
-- respostas simuladas "sim-..."). O gatilho só-inserção de respostas exige a chave de sessão.
-- Ficam as geracoes (RN12) e os dados "demo-..." do BKT, que não têm turma.
select set_config('edutec.limpar_demo', 'on', true);
delete from public.turmas;

alter table public.turmas
  add column professor_id uuid not null default auth.uid() references public.perfis (id) on delete cascade;

create index turmas_professor_idx on public.turmas (professor_id, criado_em);

-- ---------------------------------------------------------------------------
-- Membros (alunos da turma)
-- ---------------------------------------------------------------------------

create table public.membros_turma (
  turma_id uuid not null references public.turmas (id) on delete cascade,
  aluno_id uuid not null references public.perfis (id) on delete cascade,
  entrou_em timestamptz not null default now(),
  primary key (turma_id, aluno_id)
);

create index membros_turma_aluno_idx on public.membros_turma (aluno_id);

-- O professor não é aluno da própria turma.
create function public.membro_nao_e_professor() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if exists (select 1 from public.turmas t where t.id = new.turma_id and t.professor_id = new.aluno_id) then
    raise exception 'O professor da turma não pode ser aluno dela.' using errcode = 'LU403';
  end if;
  return new;
end;
$$;

create trigger membro_nao_e_professor
  before insert or update on public.membros_turma
  for each row execute function public.membro_nao_e_professor();

-- ---------------------------------------------------------------------------
-- Convites
-- ---------------------------------------------------------------------------

-- Código curto e legível: 6 caracteres de maiúsculas e dígitos, sem os ambíguos (0/O, 1/I).
create function public.gerar_codigo_convite() returns text
language sql
volatile
set search_path = ''
as $$
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
    from generate_series(1, 6);
$$;

create table public.convites (
  id uuid primary key default gen_random_uuid(),
  turma_id uuid not null references public.turmas (id) on delete cascade,
  codigo text not null unique default public.gerar_codigo_convite()
    check (codigo ~ '^[A-Z0-9]{6}$'),
  ativo boolean not null default true,
  criado_em timestamptz not null default now()
);

-- No máximo um convite ativo por turma.
create unique index convites_um_ativo_idx on public.convites (turma_id) where ativo;

-- ---------------------------------------------------------------------------
-- Funções de apoio às políticas (security definer: evitam recursão de RLS entre tabelas)
-- ---------------------------------------------------------------------------

create function public.eh_professor(turma uuid) returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.turmas t where t.id = turma and t.professor_id = auth.uid());
$$;

create function public.eh_membro(turma uuid) returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.membros_turma m where m.turma_id = turma and m.aluno_id = auth.uid());
$$;

-- O usuário logado e [outro] estão numa mesma turma, um como professor e o outro como aluno.
create function public.compartilha_turma(outro uuid) returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.turmas t join public.membros_turma m on m.turma_id = t.id
     where (t.professor_id = auth.uid() and m.aluno_id = outro)
        or (t.professor_id = outro and m.aluno_id = auth.uid())
  );
$$;

alter table public.perfis enable row level security;
alter table public.membros_turma enable row level security;
alter table public.convites enable row level security;

revoke all on table public.perfis, public.membros_turma, public.convites from anon, authenticated;
revoke execute on function
  public.criar_perfil(), public.membro_nao_e_professor(), public.gerar_codigo_convite(),
  public.eh_professor(uuid), public.eh_membro(uuid), public.compartilha_turma(uuid)
  from public, anon;
