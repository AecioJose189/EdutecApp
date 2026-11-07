-- Conceitos sem duplicata no mapa da turma.
--
-- PDFs diferentes sobre o mesmo assunto faziam o Gemini nomear o mesmo conceito de formas diferentes
-- ("Evento complementar" × "Evento Complementar"), e conceitos_turma só juntava pelo nome exato.
--
--   1. normalizar_conceito(texto): minúsculas, sem acento, espaços colapsados, sem espaço nas pontas.
--      conceitos_turma.nome_normalizado é coluna gerada; o índice único (turma_id, nome_normalizado)
--      vem na migration seguinte, depois de fundir os duplicados que já existem.
--   2. publicar_atividade liga um conceito da geração ao da turma com o mesmo nome normalizado (e
--      reescreve as questões e os pré-requisitos com o nome que a turma já usa).
--   3. possiveis_duplicatas(turma): nomes parecidos mas não iguais (pg_trgm). NÃO são fundidos
--      sozinhos: o professor decide (junta, ou marca como distintos em conceitos_distintos).
--   4. fundir_conceitos(turma, origem, destino) (UC05), só para o dono da turma: move as questões,
--      os pré-requisitos (sem laço; ciclo é recusado), as respostas, e refaz o BKT do destino.
--
-- respostas continua só de inserção. A fusão é a única exceção ao UPDATE, no mesmo padrão da limpeza
-- da demonstração: chave de sessão local à transação (edutec.fundir_conceitos), ligada só dentro da
-- função de fusão, e o gatilho confere o resto (ver respostas_somente_insercao).

create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------------
-- Normalização
-- ---------------------------------------------------------------------------

-- Sem a extensão unaccent: unaccent() não é immutable e não serve para coluna gerada. Os acentos do
-- português (e os de nomes estrangeiros comuns) bastam para nomes de conceitos.
create function public.normalizar_conceito(texto text) returns text
language sql
immutable
strict
parallel safe
set search_path = ''
as $$
  select trim(regexp_replace(
    translate(lower(texto),
      'áàâãäåāçćčéèêëēęíìîïīñńóòôõöøōúùûüūýÿ',
      'aaaaaaaccceeeeeeiiiiinnoooooooouuuuuyy'),
    '\s+', ' ', 'g'));
$$;

comment on function public.normalizar_conceito(text) is
  'Nome do conceito para comparação: minúsculas, sem acento, espaços colapsados, sem espaço nas pontas.';

alter table public.conceitos_turma
  add column nome_normalizado text generated always as (public.normalizar_conceito(nome)) stored;

create index conceitos_turma_nome_trgm_idx on public.conceitos_turma
  using gin (nome_normalizado extensions.gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Registro das fusões
-- ---------------------------------------------------------------------------

create table public.conceitos_fusoes (
  id bigint generated always as identity primary key,
  turma_id uuid not null references public.turmas (id) on delete cascade,
  origem text not null,
  destino text not null,
  n_respostas int not null default 0 check (n_respostas >= 0),
  -- Quem fundiu (null = migration automática dos nomes normalizados iguais).
  fundido_por uuid references public.perfis (id) on delete set null,
  automatica boolean not null default false,
  fundido_em timestamptz not null default now(),
  -- Transação da fusão: o gatilho de respostas só aceita mover para este destino nesta transação.
  transacao xid8 not null default pg_current_xact_id(),
  check (origem <> destino)
);

create index conceitos_fusoes_turma_idx on public.conceitos_fusoes (turma_id, fundido_em);

alter table public.conceitos_fusoes enable row level security;
-- Só as funções de fusão (security definer) escrevem; nem a service_role escreve direto.
revoke all on table public.conceitos_fusoes from anon, authenticated, service_role;
grant select on table public.conceitos_fusoes to authenticated, service_role;

create policy conceitos_fusoes_ler on public.conceitos_fusoes for select to authenticated
  using (privado.eh_professor(turma_id));

-- ---------------------------------------------------------------------------
-- respostas: nome original e a exceção da fusão no gatilho só-inserção
-- ---------------------------------------------------------------------------

-- Nome do conceito com que a resposta foi registrada, preenchido na PRIMEIRA fusão que a move
-- (null = nunca foi movida). Fusões seguintes não sobrescrevem: guarda o nome de verdade.
alter table public.respostas add column conceito_original text;

-- Append-only: UPDATE nunca, com uma exceção; DELETE só dos dados simulados.
--   DELETE: pseudônimos "demo-"/"sim-" com edutec.limpar_demo = 'on' (como antes).
--   UPDATE: só a fusão de conceitos, com edutec.fundir_conceitos = 'on', e só se
--     - nenhuma coluna muda além de conceito e conceito_original;
--     - conceito_original = coalesce(antigo conceito_original, antigo conceito);
--     - o novo conceito existe em conceitos_turma da MESMA turma da resposta;
--     - há uma fusão registrada nesta transação, nesta turma, do conceito antigo para o novo
--       (conceitos_fusoes só é escrita pelas funções de fusão, então ligar a chave à mão não basta).
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

  if tg_op = 'UPDATE' and current_setting('edutec.fundir_conceitos', true) = 'on' then
    if (to_jsonb(new) - 'conceito' - 'conceito_original') is distinct from (to_jsonb(old) - 'conceito' - 'conceito_original') then
      raise exception 'Fusão de conceitos só pode mudar o conceito da resposta.'
        using errcode = 'insufficient_privilege';
    end if;
    if new.conceito_original is distinct from coalesce(old.conceito_original, old.conceito) then
      raise exception 'conceito_original deve guardar o nome com que a resposta foi registrada.'
        using errcode = 'insufficient_privilege';
    end if;
    if not exists (select 1 from public.conceitos_turma ct
                    where ct.turma_id = new.turma_id and ct.nome = new.conceito) then
      raise exception 'O conceito "%" não existe na turma da resposta.', new.conceito
        using errcode = 'insufficient_privilege';
    end if;
    if not exists (select 1 from public.conceitos_fusoes f
                    where f.turma_id = new.turma_id and f.origem = old.conceito and f.destino = new.conceito
                      and f.transacao = pg_current_xact_id()) then
      raise exception 'Mudança de conceito sem fusão registrada nesta transação.'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  raise exception 'respostas é só de inserção (% não permitido)', tg_op
    using errcode = 'insufficient_privilege';
end;
$$;

-- ---------------------------------------------------------------------------
-- Apoio
-- ---------------------------------------------------------------------------

-- Lista jsonb de nomes de conceitos com cada nome trocado pelo de [mapa] (se houver), sem repetir e
-- na ordem da primeira ocorrência. Ex.: (["A", "B", "A2"], {"A2": "A"}) → ["A", "B"].
create function public.reescrever_conceitos(lista jsonb, mapa jsonb) returns jsonb
language sql
immutable
set search_path = ''
as $$
  select coalesce(jsonb_agg(n order by pos), '[]'::jsonb)
    from (select n, min(i) as pos
            from (select coalesce(mapa ->> trim(x), trim(x)) as n, i
                    from jsonb_array_elements_text(coalesce(lista, '[]'::jsonb)) with ordinality as e (x, i)
                   where trim(x) <> '') a
           group by n) b;
$$;

-- Pares que o professor já disse que NÃO são o mesmo conceito (somem das possíveis duplicatas).
create table public.conceitos_distintos (
  turma_id uuid not null references public.turmas (id) on delete cascade,
  conceito_a text not null,
  conceito_b text not null,
  marcado_por uuid references public.perfis (id) on delete set null,
  marcado_em timestamptz not null default now(),
  primary key (turma_id, conceito_a, conceito_b),
  check (conceito_a < conceito_b)
);

alter table public.conceitos_distintos enable row level security;
revoke all on table public.conceitos_distintos from anon, authenticated, service_role;
grant select on table public.conceitos_distintos to authenticated, service_role;
create policy conceitos_distintos_ler on public.conceitos_distintos for select to authenticated
  using (privado.eh_professor(turma_id));

-- Pares de conceitos da turma com nomes parecidos mas não iguais depois de normalizar, para o
-- professor decidir se são o mesmo conceito. Entra o par com
--   - similaridade de trigramas (pg_trgm) >= 0,6 ("evento independente" × "eventos independentes"), ou
--   - um nome inteiro, palavra por palavra, dentro do outro ("classica" × "probabilidade classica").
-- Calibrado com os nomes de produção (06/11/2026): o 2º critério também pega pares distintos
-- ("fila" × "fila circular"); por isso o professor pode marcá-los como distintos.
-- similaridade = a maior das duas medidas (1 quando um nome está dentro do outro).
create function public.possiveis_duplicatas(turma uuid)
returns table (conceito_a text, conceito_b text, similaridade real)
language sql
stable
set search_path = ''
as $$
  select a.nome, b.nome, s.v
    from public.conceitos_turma a
    join public.conceitos_turma b
      on b.turma_id = a.turma_id and a.nome < b.nome and a.nome_normalizado <> b.nome_normalizado
   cross join lateral (select greatest(
            extensions.similarity(a.nome_normalizado, b.nome_normalizado),
            case when extensions.strict_word_similarity(a.nome_normalizado, b.nome_normalizado) = 1
                   or extensions.strict_word_similarity(b.nome_normalizado, a.nome_normalizado) = 1
                 then 1 else 0 end) as v) s
   where a.turma_id = turma
     and (extensions.similarity(a.nome_normalizado, b.nome_normalizado) >= 0.6 or s.v = 1)
     and not exists (select 1 from public.conceitos_distintos d
                      where d.turma_id = turma and d.conceito_a = a.nome and d.conceito_b = b.nome)
   order by s.v desc, a.nome, b.nome;
$$;

-- ---------------------------------------------------------------------------
-- Publicação: liga ao conceito da turma com o mesmo nome normalizado
-- ---------------------------------------------------------------------------

-- Mesmo contrato de antes (ED404, ED409, ED003). Novo: cada nome de conceito da geração (nos conceitos,
-- nos pré-requisitos e nas questões) vira o nome canônico:
--   1. o conceito da turma com o mesmo nome normalizado, se houver;
--   2. senão, o primeiro conceito da própria geração com o mesmo nome normalizado;
--   3. senão, o próprio nome (sem espaços nas pontas).
-- As questões são gravadas já com os nomes canônicos (o BKT registra respostas por esses nomes).
create or replace function public.publicar_atividade(
  turma uuid, geracao bigint, permitir_refazer boolean, tempo_minutos int, titulo text default null
) returns uuid
language plpgsql
set search_path = ''
as $$
declare
  resultado jsonb;
  mapa jsonb;
  conceitos jsonb;
  questoes jsonb;
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

  -- Todos os nomes que aparecem na geração, na ordem: conceitos, pré-requisitos, questões.
  with nomes as (
    select trim(c ->> 'nome') as nome, i::numeric as ordem
      from jsonb_array_elements(resultado -> 'conceitos') with ordinality as e (c, i)
    union all
    select trim(p), 1000 + i
      from jsonb_array_elements(resultado -> 'conceitos') with ordinality as e (c, i)
     cross join lateral jsonb_array_elements_text(coalesce(c -> 'prerequisitos', '[]'::jsonb)) p
    union all
    select trim(x), 100000 + i
      from jsonb_array_elements(resultado -> 'questoes') with ordinality as e (q, i)
     cross join lateral jsonb_array_elements_text(coalesce(q -> 'conceitos', '[]'::jsonb)) x
  ),
  distintos as (
    select n.nome, min(n.ordem) as ordem from nomes n where n.nome <> '' group by n.nome
  ),
  canonicos as (
    select d.nome,
           coalesce(
             (select ct.nome from public.conceitos_turma ct
               where ct.turma_id = turma and ct.nome_normalizado = public.normalizar_conceito(d.nome)
               order by ct.criado_em, ct.nome limit 1),
             first_value(d.nome) over (partition by public.normalizar_conceito(d.nome) order by d.ordem)
           ) as canonico
      from distintos d
  )
  select coalesce(jsonb_object_agg(c.nome, c.canonico), '{}'::jsonb) into mapa from canonicos c;

  -- Conceitos com nome canônico; os que viraram o mesmo juntam os pré-requisitos (sem laço).
  select coalesce(jsonb_agg(jsonb_build_object('nome', x.nome, 'prerequisitos', x.pre) order by x.ordem), '[]'::jsonb)
    into conceitos
    from (select mapa ->> trim(c ->> 'nome') as nome, min(i) as ordem,
                 coalesce(jsonb_agg(distinct p) filter (where p is not null and p <> mapa ->> trim(c ->> 'nome')), '[]'::jsonb) as pre
            from jsonb_array_elements(resultado -> 'conceitos') with ordinality as e (c, i)
            left join lateral jsonb_array_elements_text(
                   public.reescrever_conceitos(c -> 'prerequisitos', mapa)) p on true
           where trim(c ->> 'nome') <> ''
           group by 1) x;

  select coalesce(jsonb_agg(
           jsonb_set(q, '{conceitos}', public.reescrever_conceitos(q -> 'conceitos', mapa)) order by i), '[]'::jsonb)
    into questoes
    from jsonb_array_elements(resultado -> 'questoes') with ordinality as e (q, i);

  titulo_final := left(coalesce(
    nullif(trim(publicar_atividade.titulo), ''),
    (select string_agg(c.nome, ', ' order by c.i)
       from (select x ->> 'nome' as nome, i
               from jsonb_array_elements(conceitos) with ordinality as e (x, i)
              order by i limit 2) c)
      || case when jsonb_array_length(conceitos) > 2 then ' e outros' else '' end,
    'Atividade'), 200);

  begin
    insert into public.atividades (turma_id, geracao_id, questoes, permitir_refazer, tempo_minutos, titulo)
    values (turma, geracao, questoes, publicar_atividade.permitir_refazer,
            publicar_atividade.tempo_minutos, titulo_final)
    returning id into nova;
  exception when unique_violation then
    raise exception 'Esta atividade já foi publicada nesta turma.' using errcode = 'ED409';
  end;

  insert into public.conceitos_turma as ct (turma_id, nome, prerequisitos)
  select turma, c ->> 'nome',
         coalesce(array(select p from jsonb_array_elements_text(c -> 'prerequisitos') p order by 1), '{}')
    from jsonb_array_elements(conceitos) c
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
-- Fusão de conceitos (UC05)
-- ---------------------------------------------------------------------------

-- Funde [origem] em [destino] na turma, numa transação:
--   1. registra a fusão em conceitos_fusoes (por, automática);
--   2. pré-requisitos: o destino fica com os dele + os da origem; quem dependia da origem passa a
--      depender do destino; laços (destino → destino) somem; se o grafo ficar com ciclo, recusa (ED003);
--   3. questões das atividades da turma: origem → destino nas listas de conceitos (sem repetir);
--   4. respostas: conceito origem → destino (conceito_original guarda o primeiro nome);
--   5. domínio: apaga o da origem e o do destino e refaz o BKT do destino com as respostas de cada
--      aluno em ordem de tempo (criado_em, id), com os parâmetros do destino.
-- Erros: ED404 (turma ou conceito não existe), ED400 (origem = destino), ED003 (ciclo).
-- Sem conferência de dono: quem chama confere (fundir_conceitos, ou a migration dos duplicados).
create function privado.fundir_conceitos_interno(
  turma uuid, origem text, destino text, por uuid, automatica boolean
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  pre_origem text[];
  ciclo text[];
  n_mov int;
  par public.bkt_parametros;
  r record;
  aluno_atual text;
  p numeric;
  n int;
  n_alunos int := 0;
begin
  if origem = destino then
    raise exception 'Escolha dois conceitos diferentes para juntar.' using errcode = 'ED400';
  end if;

  perform 1 from public.turmas t where t.id = turma for update;
  if not found then
    raise exception 'Turma não encontrada.' using errcode = 'ED404';
  end if;

  select ct.prerequisitos into pre_origem from public.conceitos_turma ct
   where ct.turma_id = turma and ct.nome = origem for update;
  if not found then
    raise exception 'O conceito "%" não existe nesta turma.', origem using errcode = 'ED404';
  end if;
  perform 1 from public.conceitos_turma ct where ct.turma_id = turma and ct.nome = destino for update;
  if not found then
    raise exception 'O conceito "%" não existe nesta turma.', destino using errcode = 'ED404';
  end if;

  -- 1. Registro (o gatilho de respostas exige esta linha nesta transação).
  insert into public.conceitos_fusoes (turma_id, origem, destino, fundido_por, automatica)
  values (turma, origem, destino, por, automatica);

  -- 2. Pré-requisitos.
  update public.conceitos_turma ct
     set prerequisitos = array(select distinct x from unnest(ct.prerequisitos || pre_origem) x
                                where x not in (origem, destino) order by 1)
   where ct.turma_id = turma and ct.nome = destino;

  delete from public.conceitos_turma ct where ct.turma_id = turma and ct.nome = origem;
  delete from public.conceitos_distintos d where d.turma_id = turma and origem in (d.conceito_a, d.conceito_b);

  update public.conceitos_turma ct
     set prerequisitos = array(select distinct case when x = origem then destino else x end
                                 from unnest(ct.prerequisitos) x
                                where case when x = origem then destino else x end <> ct.nome order by 1)
   where ct.turma_id = turma and origem = any (ct.prerequisitos);

  ciclo := public.ciclo_conceitos(turma);
  if ciclo is not null then
    raise exception 'Não dá para juntar "%" com "%": os pré-requisitos formariam um ciclo (%).',
      origem, destino, array_to_string(ciclo, ' → ')
      using errcode = 'ED003';
  end if;

  -- 3. Questões (questoes_aluno é coluna gerada e acompanha).
  update public.atividades a
     set questoes = (select jsonb_agg(
                       case when q ? 'conceitos'
                            then jsonb_set(q, '{conceitos}', public.reescrever_conceitos(q -> 'conceitos', jsonb_build_object(origem, destino)))
                            else q end order by i)
                       from jsonb_array_elements(a.questoes) with ordinality as e (q, i))
   where a.turma_id = turma
     and exists (select 1 from jsonb_array_elements(a.questoes) q
                  cross join lateral jsonb_array_elements_text(coalesce(q -> 'conceitos', '[]'::jsonb)) x
                  where trim(x) = origem);

  -- 4. Respostas (a única exceção ao só-inserção; a chave vale só nesta transação).
  perform set_config('edutec.fundir_conceitos', 'on', true);
  update public.respostas rs
     set conceito = destino, conceito_original = coalesce(rs.conceito_original, rs.conceito)
   where rs.turma_id = turma and rs.conceito = origem;
  get diagnostics n_mov = row_count;
  perform set_config('edutec.fundir_conceitos', 'off', true);

  update public.conceitos_fusoes f set n_respostas = n_mov
   where f.turma_id = turma and f.origem = fundir_conceitos_interno.origem
     and f.destino = fundir_conceitos_interno.destino and f.transacao = pg_current_xact_id();

  -- 5. Domínio do destino refeito pelo BKT, resposta a resposta.
  delete from public.dominio d where d.turma_id = turma and d.conceito in (origem, destino);

  select * into par from public.bkt_parametros b
   where b.conceito in (destino, '*')
   order by (b.conceito = '*')
   limit 1;

  for r in
    select rs.aluno_pseudonimo, rs.aluno_id, rs.correta, rs.criado_em
      from public.respostas rs
     where rs.turma_id = turma and rs.conceito = destino
     order by rs.aluno_pseudonimo, rs.criado_em, rs.id
  loop
    if aluno_atual is distinct from r.aluno_pseudonimo then
      aluno_atual := r.aluno_pseudonimo;
      n_alunos := n_alunos + 1;
      insert into public.dominio (turma_id, aluno_pseudonimo, aluno_id, conceito, p_dominio, n_respostas)
      values (turma, r.aluno_pseudonimo, r.aluno_id, destino, par.p_l0, 0);
    end if;
    update public.dominio d
       set p_dominio = public.bkt_passo(d.p_dominio, r.correta, par.p_t, par.p_g, par.p_s),
           n_respostas = d.n_respostas + 1,
           atualizado_em = now()
     where d.turma_id = turma and d.aluno_pseudonimo = r.aluno_pseudonimo and d.conceito = destino;
  end loop;

  return jsonb_build_object('origem', origem, 'destino', destino, 'n_respostas', n_mov, 'n_alunos', n_alunos);
end;
$$;

-- Para o professor (pelo JWT dele: auth.uid() tem de ser o dono da turma). Turma de outro professor
-- responde como inexistente (ED404), para não revelar turmas alheias.
create function public.fundir_conceitos(turma uuid, origem text, destino text) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  eu uuid := auth.uid();
begin
  if eu is null or not exists (select 1 from public.turmas t where t.id = turma and t.professor_id = eu) then
    raise exception 'Turma não encontrada.' using errcode = 'ED404';
  end if;
  return privado.fundir_conceitos_interno(turma, trim(origem), trim(destino), eu, false);
end;
$$;

-- O professor diz que dois conceitos parecidos são distintos (não aparecem mais como duplicata).
create function public.marcar_conceitos_distintos(turma uuid, conceito_a text, conceito_b text) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  eu uuid := auth.uid();
  a text := least(trim(conceito_a), trim(conceito_b));
  b text := greatest(trim(conceito_a), trim(conceito_b));
begin
  if eu is null or not exists (select 1 from public.turmas t where t.id = turma and t.professor_id = eu) then
    raise exception 'Turma não encontrada.' using errcode = 'ED404';
  end if;
  if a = b then
    raise exception 'Escolha dois conceitos diferentes.' using errcode = 'ED400';
  end if;
  if (select count(*) from public.conceitos_turma ct where ct.turma_id = turma and ct.nome in (a, b)) < 2 then
    raise exception 'Conceito não encontrado nesta turma.' using errcode = 'ED404';
  end if;
  insert into public.conceitos_distintos (turma_id, conceito_a, conceito_b, marcado_por)
  values (turma, a, b, eu)
  on conflict do nothing;
end;
$$;

-- ---------------------------------------------------------------------------
-- Acesso
-- ---------------------------------------------------------------------------

revoke execute on function
  public.normalizar_conceito(text),
  public.reescrever_conceitos(jsonb, jsonb),
  public.possiveis_duplicatas(uuid),
  public.fundir_conceitos(uuid, text, text),
  public.marcar_conceitos_distintos(uuid, text, text),
  privado.fundir_conceitos_interno(uuid, text, text, uuid, boolean)
  from public, anon, authenticated, service_role;

-- normalizar_conceito é usada pela coluna gerada (inserções do professor pela turma-api/service_role).
grant execute on function public.normalizar_conceito(text) to authenticated, service_role;
grant execute on function
  public.reescrever_conceitos(jsonb, jsonb),
  public.possiveis_duplicatas(uuid)
  to service_role;
-- A turma-api chama com o cliente do professor (JWT), para auth.uid() ser ele.
grant execute on function
  public.fundir_conceitos(uuid, text, text),
  public.marcar_conceitos_distintos(uuid, text, text)
  to authenticated;
