-- RN12: cada PDF é processado uma vez. Guarda o resultado de cada geração por hash do PDF + parâmetros,
-- para devolver na hora quando o mesmo material for pedido de novo.
create table public.geracoes (
  id bigint generated always as identity primary key,
  hash_pdf text not null check (hash_pdf ~ '^[0-9a-f]{64}$'),  -- SHA-256 do arquivo, em hexadecimal
  quantidade int not null check (quantidade between 1 and 10),
  formato text not null check (formato in ('multipla_escolha', 'aberta', 'mista')),
  dificuldade text not null check (dificuldade in ('facil', 'media', 'dificil')),
  resultado jsonb not null,  -- { conceitos, questoes, nivel_raciocinio, tokens }
  modelo text,
  criado_em timestamptz not null default now()
);

-- Busca do resultado mais recente para o mesmo material e parâmetros.
create index geracoes_busca_idx
  on public.geracoes (hash_pdf, quantidade, formato, dificuldade, criado_em desc);

-- RLS ligado e nenhuma política: anon e authenticated não leem nem escrevem.
-- Só a Edge Function, com a service_role (que ignora RLS), acessa a tabela.
alter table public.geracoes enable row level security;
revoke all on table public.geracoes from anon, authenticated;
