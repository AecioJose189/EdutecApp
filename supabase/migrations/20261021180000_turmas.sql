-- Turmas do professor. Ainda não há login: todas as turmas são do único professor da demo.
-- Acesso só pela Edge Function turma-api (service_role); RLS ligado e nenhuma política.
create table public.turmas (
  id uuid primary key default gen_random_uuid(),
  nome text not null check (length(trim(nome)) between 1 and 120),    -- disciplina, ex.: "Cálculo I"
  codigo text not null check (length(trim(codigo)) between 1 and 60), -- ex.: "Turma A"
  criado_em timestamptz not null default now()
);

alter table public.turmas enable row level security;
revoke all on table public.turmas from anon, authenticated;
