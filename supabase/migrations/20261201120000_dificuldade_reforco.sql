-- Fase 5: "Ver alunos com dificuldade" e "Gerar atividade de reforço" (painel do conceito no mapa da turma).
--
-- 1. alunos_do_conceito(turma, conceito): os alunos REAIS da turma naquele conceito, os piores primeiro, com
--    domínio estimado, nº de respostas, erros com certeza, acertos no chute e treinos com flashcards
--    (mesma contagem de historico_aluno_conceito e treino_aluno_conceito).
-- 2. Reforço: a atividade é gerada a partir do PDF de onde o conceito veio. "Origem" de um conceito = a
--    primeira atividade publicada na turma (publicada_em, id) cuja geração tem esse conceito, comparando pelo
--    nome normalizado e seguindo as fusões (um conceito fundido herda os nomes que foram juntados nele), ou
--    cuja questão o cita. PDFs "diferentes" = hash diferente (duas gerações do mesmo PDF são o mesmo PDF).
--    contexto_reforco devolve o PDF do conceito principal e os enunciados já publicados desses conceitos
--    (para a IA não repetir). Só conteúdo do professor; nada de aluno (RN13).
-- 3. Cache (RN12): geracoes ganha foco (conceitos) e foco_chave (sha256 dos conceitos normalizados + dos
--    enunciados já publicados, calculado na gerar-atividade). Gerações comuns: os dois nulos. Publicado um
--    reforço, o próximo pedido com os mesmos conceitos tem outra chave e gera questões novas.
--
-- Tudo só para a service_role (turma-api e gerar-atividade conferem o dono da turma antes).

alter table public.geracoes
  add column foco text[],
  add column foco_chave text check (foco_chave is null or foco_chave ~ '^[0-9a-f]{64}$'),
  add constraint geracoes_foco_check check ((foco is null) = (foco_chave is null));

drop index public.geracoes_busca_idx;
create index geracoes_busca_idx on public.geracoes (hash_pdf, quantidade, formato, dificuldade, foco_chave, criado_em desc);

-- Nomes normalizados que um conceito da turma teve (ele mesmo + os que foram fundidos nele, em cadeia).
create function privado.nomes_do_conceito(turma uuid, conceito text) returns text[]
language sql
stable
set search_path = ''
as $$
  with recursive nomes (nome) as (
    select nomes_do_conceito.conceito
    union
    select f.origem from public.conceitos_fusoes f join nomes n on f.destino = n.nome
     where f.turma_id = nomes_do_conceito.turma
  )
  select array_agg(distinct public.normalizar_conceito(nome)) from nomes;
$$;

-- Atividade (e geração/PDF) de onde veio cada conceito da turma; conceitos = null → todos.
-- Sem origem conhecida (não deveria acontecer): colunas nulas.
create function public.origem_dos_conceitos(turma uuid, conceitos text[] default null)
returns table (conceito text, geracao_id bigint, hash_pdf text, atividade_id uuid, numero int, titulo text)
language sql
stable
set search_path = ''
as $$
  select ct.nome, o.geracao_id, o.hash_pdf, o.id, case when o.id is null then null else public.numero_da_atividade(o.id) end, o.titulo
    from public.conceitos_turma ct
    left join lateral (
      select a.id, a.titulo, a.geracao_id, g.hash_pdf
        from public.atividades a
        join public.geracoes g on g.id = a.geracao_id
       where a.turma_id = ct.turma_id
         and (exists (select 1 from jsonb_array_elements(g.resultado -> 'conceitos') c
                       where public.normalizar_conceito(c ->> 'nome') = any (privado.nomes_do_conceito(ct.turma_id, ct.nome)))
              or exists (select 1 from jsonb_array_elements(a.questoes) q, jsonb_array_elements_text(q -> 'conceitos') qc
                          where trim(qc) = ct.nome))
       order by a.publicada_em, a.id
       limit 1
    ) o on true
   where ct.turma_id = origem_dos_conceitos.turma
     and (origem_dos_conceitos.conceitos is null or ct.nome = any (origem_dos_conceitos.conceitos))
   order by ct.criado_em, ct.nome;
$$;

-- Contexto para gerar o reforço de [conceitos] (o 1º é o principal). Erros: LU404 conceito que não é da
-- turma; LU409 sem material de origem para o principal.
--   principal, geracao_id, hash_pdf, atividade {id, numero, titulo}  → o PDF usado
--   conceitos: [{conceito, atividade {numero, titulo} | null, mesmo_material}]
--   outros_materiais: conceitos cuja origem é outro PDF (a tela avisa que vale o do principal)
--   enunciados: enunciados já publicados na turma com algum desses conceitos (em ordem, até 40)
create function public.contexto_reforco(turma uuid, conceitos text[]) returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  desconhecidos text[];
  principal record;
  resultado jsonb;
begin
  if conceitos is null or cardinality(conceitos) = 0 then
    raise exception 'Escolha ao menos um conceito.' using errcode = 'LU404';
  end if;
  select array_agg(c) into desconhecidos from unnest(conceitos) c
   where not exists (select 1 from public.conceitos_turma ct where ct.turma_id = contexto_reforco.turma and ct.nome = c);
  if desconhecidos is not null then
    raise exception 'Conceito fora da turma: %', array_to_string(desconhecidos, ', ') using errcode = 'LU404';
  end if;

  select * into principal from public.origem_dos_conceitos(turma, array[conceitos[1]]);
  if principal.geracao_id is null then
    raise exception 'Não encontrei o material de origem de "%".', conceitos[1] using errcode = 'LU409';
  end if;

  with o as (select * from public.origem_dos_conceitos(contexto_reforco.turma, contexto_reforco.conceitos)),
  ordem as (select c, i from unnest(contexto_reforco.conceitos) with ordinality u (c, i)),
  alvo as (select distinct unnest(privado.nomes_do_conceito(contexto_reforco.turma, c)) as n from unnest(contexto_reforco.conceitos) c),
  enunciados as (
    select q ->> 'enunciado' as enunciado, a.publicada_em, a.id, qi
      from public.atividades a
      cross join lateral jsonb_array_elements(a.questoes) with ordinality as e (q, qi)
     where a.turma_id = contexto_reforco.turma
       and exists (select 1 from jsonb_array_elements_text(q -> 'conceitos') qc
                    where public.normalizar_conceito(qc) in (select n from alvo))
     order by a.publicada_em, a.id, qi
     limit 40
  )
  select jsonb_build_object(
    'principal', conceitos[1],
    'geracao_id', principal.geracao_id,
    'hash_pdf', principal.hash_pdf,
    'atividade', jsonb_build_object('id', principal.atividade_id, 'numero', principal.numero, 'titulo', principal.titulo),
    'conceitos', (select jsonb_agg(jsonb_build_object(
        'conceito', o.conceito,
        'atividade', case when o.atividade_id is null then null
                          else jsonb_build_object('numero', o.numero, 'titulo', o.titulo) end,
        'mesmo_material', o.hash_pdf is not distinct from principal.hash_pdf) order by ordem.i)
        from o join ordem on ordem.c = o.conceito),
    'outros_materiais', coalesce((select jsonb_agg(o.conceito order by ordem.i) from o join ordem on ordem.c = o.conceito
                                   where o.hash_pdf is distinct from principal.hash_pdf), '[]'::jsonb),
    'enunciados', coalesce((select jsonb_agg(e.enunciado order by e.publicada_em, e.id, e.qi) from enunciados e), '[]'::jsonb)
  ) into resultado;
  return resultado;
end;
$$;

-- Alunos reais (membros) da turma num conceito, os piores primeiro (sem dados no fim).
create function public.alunos_do_conceito(turma uuid, conceito text) returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'conceito', alunos_do_conceito.conceito,
    'alunos', coalesce(jsonb_agg(x.item order by x.p asc nulls last, x.nome, x.aluno_id), '[]'::jsonb))
    from (
      select m.aluno_id, coalesce(p.nome, 'Aluno') as nome, d.p_dominio as p,
             jsonb_build_object(
               'aluno_id', m.aluno_id,
               'nome', coalesce(p.nome, 'Aluno'),
               'p_dominio', round(d.p_dominio, 4),
               'estado', case when d.p_dominio is null then null else public.estado_dominio(d.p_dominio) end,
               'n_respostas', coalesce(d.n_respostas, 0),
               'erros_com_certeza', (select count(*) from public.respostas r
                                      where r.turma_id = alunos_do_conceito.turma and r.aluno_id = m.aluno_id
                                        and r.conceito = alunos_do_conceito.conceito
                                        and r.confianca = 'certeza' and not r.correta),
               'acertos_no_chute', (select count(*) from public.respostas r
                                     where r.turma_id = alunos_do_conceito.turma and r.aluno_id = m.aluno_id
                                       and r.conceito = alunos_do_conceito.conceito
                                       and r.confianca = 'chute' and r.correta),
               'treino', public.treino_aluno_conceito(alunos_do_conceito.turma, m.aluno_id::text, alunos_do_conceito.conceito)
             ) as item
        from public.membros_turma m
        left join public.perfis p on p.id = m.aluno_id
        left join public.dominio d on d.turma_id = m.turma_id and d.aluno_id = m.aluno_id
                                  and d.conceito = alunos_do_conceito.conceito
       where m.turma_id = alunos_do_conceito.turma
    ) x;
$$;

revoke execute on function
  privado.nomes_do_conceito(uuid, text),
  public.origem_dos_conceitos(uuid, text[]),
  public.contexto_reforco(uuid, text[]),
  public.alunos_do_conceito(uuid, text)
  from public, anon, authenticated;
grant execute on function
  privado.nomes_do_conceito(uuid, text),
  public.origem_dos_conceitos(uuid, text[]),
  public.contexto_reforco(uuid, text[]),
  public.alunos_do_conceito(uuid, text)
  to service_role;
