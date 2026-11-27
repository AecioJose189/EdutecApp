-- Estatísticas da turma (UC13) e mapa de cada aluno para o professor (UC11/UC12). Fase 3.
--
-- Tudo só de leitura e só para a service_role: a turma-api confere que o professor é o dono da turma.
-- Alunos reais = respostas/domínio com aluno_id; simulação = pseudônimo "sim-..." (sem aluno_id). Por
-- padrão só os reais; incluir_simulacao junta a demonstração.
--
-- "Domínio estimado logo depois de uma atividade": o BKT refeito pelas respostas da turma até o fim da
-- última sessão encerrada daquela atividade, com os mesmos parâmetros (bkt_parametros do conceito ou
-- '*') e a mesma ordem (criado_em, id) do registrar_resposta e da fusão de conceitos. Refeito até agora,
-- bate com a tabela dominio (testado em estatisticas.test.sql).

-- ---------------------------------------------------------------------------
-- BKT refeito (agregado ordenado)
-- ---------------------------------------------------------------------------

-- Um passo do BKT a partir do estado (null = antes da 1ª resposta: começa em L0).
create function privado.bkt_acumular(estado numeric, correta boolean, p_l0 numeric, p_t numeric, p_g numeric, p_s numeric)
returns numeric
language sql
immutable
set search_path = ''
as $$
  select public.bkt_passo(coalesce(estado, p_l0), correta, p_t, p_g, p_s);
$$;

create aggregate privado.bkt_refeito(boolean, numeric, numeric, numeric, numeric) (
  sfunc = privado.bkt_acumular,
  stype = numeric
);

-- Parâmetros do BKT de um conceito (o específico, senão o padrão '*').
create function privado.bkt_parametros_de(conceito text) returns public.bkt_parametros
language sql
stable
set search_path = ''
as $$
  select * from public.bkt_parametros b where b.conceito in (bkt_parametros_de.conceito, '*')
   order by (b.conceito = '*') limit 1;
$$;

-- Aluno conta? Real (aluno_id) sempre; simulação só se pedida.
create function privado.aluno_conta(aluno_id uuid, pseudonimo text, incluir_simulacao boolean) returns boolean
language sql
immutable
set search_path = ''
as $$
  select aluno_id is not null or (incluir_simulacao and pseudonimo like 'sim-%');
$$;

-- Domínio estimado de cada aluno e conceito da turma, refeito com as respostas até [ate].
create function public.dominio_reprocessado(turma uuid, ate timestamptz, incluir_simulacao boolean default false)
returns table (aluno text, conceito text, p_dominio numeric, n_respostas int)
language sql
stable
set search_path = ''
as $$
  select r.aluno_pseudonimo, r.conceito,
         privado.bkt_refeito(r.correta, p.p_l0, p.p_t, p.p_g, p.p_s order by r.criado_em, r.id),
         count(*)::int
    from public.respostas r
    cross join lateral privado.bkt_parametros_de(r.conceito) p
   where r.turma_id = dominio_reprocessado.turma
     and r.criado_em <= dominio_reprocessado.ate
     and privado.aluno_conta(r.aluno_id, r.aluno_pseudonimo, dominio_reprocessado.incluir_simulacao)
   group by r.aluno_pseudonimo, r.conceito;
$$;

-- Moda do estado; no empate, o mais preocupante (como a view dominio_turma).
create function privado.moda_estado(estados text[]) returns text
language sql
immutable
set search_path = ''
as $$
  select (array['revisar', 'em_desenvolvimento', 'consolidado'])[
    (select mode() within group (order by case e when 'revisar' then 1 when 'em_desenvolvimento' then 2 else 3 end)
       from unnest(estados) e)];
$$;

-- ---------------------------------------------------------------------------
-- Resumo e tabela por conceito
-- ---------------------------------------------------------------------------

-- resumo: n_alunos (reais = membros da turma; + pseudônimos da simulação se incluída), n_atividades,
--   n_atividades_encerradas, n_respostas (registros do BKT: cada questão conta uma vez por conceito),
--   media_dominio / mediana_dominio (sobre os pares aluno × conceito com dados), estado_moda.
-- conceitos (piores primeiro; sem dados no fim): media, mediana, estado_moda, n_respostas e quantos
--   alunos em cada estado (sem_dados = alunos da turma sem resposta naquele conceito).
create function public.estatisticas_turma(turma uuid, incluir_simulacao boolean default false) returns jsonb
language sql
stable
set search_path = ''
as $$
  with alunos as (
    select m.aluno_id::text as aluno from public.membros_turma m where m.turma_id = estatisticas_turma.turma
    union
    select distinct d.aluno_pseudonimo from public.dominio d
     where d.turma_id = estatisticas_turma.turma and estatisticas_turma.incluir_simulacao
       and d.aluno_id is null and d.aluno_pseudonimo like 'sim-%'
  ),
  dom as (
    select d.aluno_pseudonimo as aluno, d.conceito, d.p_dominio, d.n_respostas, public.estado_dominio(d.p_dominio) as estado
      from public.dominio d
     where d.turma_id = estatisticas_turma.turma
       and privado.aluno_conta(d.aluno_id, d.aluno_pseudonimo, estatisticas_turma.incluir_simulacao)
  ),
  n_alunos as (select count(*)::int as n from alunos),
  por_conceito as (
    select ct.nome as conceito, ct.criado_em,
           round(avg(dom.p_dominio), 4) as media,
           round((percentile_cont(0.5) within group (order by dom.p_dominio))::numeric, 4) as mediana,
           privado.moda_estado(array_agg(dom.estado) filter (where dom.estado is not null)) as moda,
           coalesce(sum(dom.n_respostas), 0)::int as n_respostas,
           count(dom.aluno)::int as com_dados,
           count(*) filter (where dom.estado = 'consolidado')::int as consolidado,
           count(*) filter (where dom.estado = 'em_desenvolvimento')::int as em_desenvolvimento,
           count(*) filter (where dom.estado = 'revisar')::int as revisar
      from public.conceitos_turma ct
      left join dom on dom.conceito = ct.nome
     where ct.turma_id = estatisticas_turma.turma
     group by ct.nome, ct.criado_em
  )
  select jsonb_build_object(
    'tem_simulacao', exists (select 1 from public.dominio d where d.turma_id = estatisticas_turma.turma
                              and d.aluno_id is null and d.aluno_pseudonimo like 'sim-%'),
    'incluir_simulacao', estatisticas_turma.incluir_simulacao,
    'resumo', (select jsonb_build_object(
        'n_alunos', (select n from n_alunos),
        'n_atividades', (select count(*) from public.atividades a where a.turma_id = estatisticas_turma.turma),
        'n_atividades_encerradas', (select count(distinct a.id) from public.atividades a
             join public.sessoes s on s.atividade_id = a.id
            where a.turma_id = estatisticas_turma.turma and s.encerrada_em is not null),
        'n_respostas', coalesce(sum(dom.n_respostas), 0),
        'media_dominio', round(avg(dom.p_dominio), 4),
        'mediana_dominio', round((percentile_cont(0.5) within group (order by dom.p_dominio))::numeric, 4),
        'estado_moda', privado.moda_estado(array_agg(dom.estado))) from dom),
    'conceitos', coalesce((select jsonb_agg(jsonb_build_object(
        'conceito', c.conceito, 'media_dominio', c.media, 'mediana_dominio', c.mediana, 'estado_moda', c.moda,
        'n_respostas', c.n_respostas,
        'alunos_por_estado', jsonb_build_object(
          'consolidado', c.consolidado, 'em_desenvolvimento', c.em_desenvolvimento, 'revisar', c.revisar,
          'sem_dados', greatest((select n from n_alunos) - c.com_dados, 0)))
        order by c.media asc nulls last, c.criado_em, c.conceito) from por_conceito c), '[]'::jsonb)
  );
$$;

-- ---------------------------------------------------------------------------
-- Evolução por atividade encerrada
-- ---------------------------------------------------------------------------

-- Para cada atividade com sessão encerrada, na ordem da turma (numero): taxa de acerto da turma nas
-- tentativas enviadas (só alunos reais: a simulação não tem sessões), domínio estimado médio logo
-- depois (BKT refeito até a última sessão encerrada dela) e, por conceito, a média logo depois e a
-- média no ponto anterior (null na primeira atividade ou se o conceito ainda não tinha dados).
create function public.evolucao_turma(turma uuid, incluir_simulacao boolean default false) returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  a record;
  resultado jsonb := '[]'::jsonb;
  anterior jsonb := '{}'::jsonb;  -- conceito → média no ponto anterior
  atual jsonb;
  conceitos jsonb;
  media_geral numeric;
begin
  for a in
    select at.id, at.titulo, public.numero_da_atividade(at.id) as numero,
           max(s.encerrada_em) as fim,
           sum(t.acertos) filter (where t.enviada_em is not null) as acertos,
           sum(t.total_multipla_escolha) filter (where t.enviada_em is not null) as total,
           count(t.id) filter (where t.enviada_em is not null) as enviadas
      from public.atividades at
      join public.sessoes s on s.atividade_id = at.id
      left join public.tentativas t on t.sessao_id = s.id
     where at.turma_id = evolucao_turma.turma
     group by at.id, at.titulo
    having max(s.encerrada_em) is not null
     order by 3
  loop
    -- Média geral (pares aluno × conceito com dados) e média por conceito, logo depois da atividade.
    select round(avg(d.p_dominio), 4) into media_geral
      from public.dominio_reprocessado(evolucao_turma.turma, a.fim, evolucao_turma.incluir_simulacao) d;
    select coalesce(jsonb_object_agg(x.conceito, x.media), '{}'::jsonb) into atual
      from (select d.conceito, round(avg(d.p_dominio), 4) as media
              from public.dominio_reprocessado(evolucao_turma.turma, a.fim, evolucao_turma.incluir_simulacao) d
             group by d.conceito) x;

    select coalesce(jsonb_agg(jsonb_build_object(
             'conceito', k, 'media_dominio', (atual ->> k)::numeric,
             'media_anterior', (anterior ->> k)::numeric,
             'estado', public.estado_dominio((atual ->> k)::numeric))
           order by k), '[]'::jsonb)
      into conceitos
      from jsonb_object_keys(atual) k;

    resultado := resultado || jsonb_build_array(jsonb_build_object(
      'atividade', jsonb_build_object('id', a.id, 'numero', a.numero, 'titulo', a.titulo),
      'encerrada_em', to_char(a.fim at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
      'n_enviadas', a.enviadas,
      'taxa_acerto', case when coalesce(a.total, 0) > 0 then round(a.acertos::numeric / a.total, 4) end,
      'media_dominio', media_geral,
      'conceitos', conceitos));
    anterior := atual;
  end loop;
  return resultado;
end;
$$;

-- ---------------------------------------------------------------------------
-- Mapa e histórico de um aluno (para o professor)
-- ---------------------------------------------------------------------------

-- Domínio estimado de um aluno (chave = aluno_pseudonimo: o uuid em texto para os reais) em cada
-- conceito da turma; sem respostas → p_dominio null ("sem dados").
create function public.mapa_aluno(turma uuid, aluno text) returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object('conceitos', coalesce(jsonb_agg(jsonb_build_object(
      'conceito', ct.nome,
      'p_dominio', round(d.p_dominio, 4),
      'estado', case when d.p_dominio is null then null else public.estado_dominio(d.p_dominio) end,
      'n_respostas', coalesce(d.n_respostas, 0)) order by ct.criado_em, ct.nome), '[]'::jsonb))
    from public.conceitos_turma ct
    left join public.dominio d on d.turma_id = ct.turma_id and d.conceito = ct.nome and d.aluno_pseudonimo = mapa_aluno.aluno
   where ct.turma_id = mapa_aluno.turma;
$$;

-- Histórico de um aluno num conceito (UC12): cada resposta em ordem (criado_em, id), com a atividade
-- (null na simulação), acertou, confiança e o domínio estimado depois dela; e os totais de erros com
-- certeza e acertos no chute.
create function public.historico_aluno_conceito(turma uuid, aluno text, conceito text) returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  par public.bkt_parametros;
  p numeric;
  r record;
  itens jsonb := '[]'::jsonb;
  certeza int := 0;
  chute int := 0;
begin
  par := privado.bkt_parametros_de(conceito);
  p := par.p_l0;
  for r in
    select rs.id, rs.criado_em, rs.correta, rs.confianca, at.id as atividade_id, at.titulo
      from public.respostas rs
      left join public.tentativas t on t.id = rs.tentativa_id
      left join public.sessoes s on s.id = t.sessao_id
      left join public.atividades at on at.id = s.atividade_id
     where rs.turma_id = historico_aluno_conceito.turma
       and rs.aluno_pseudonimo = historico_aluno_conceito.aluno
       and rs.conceito = historico_aluno_conceito.conceito
     order by rs.criado_em, rs.id
  loop
    p := public.bkt_passo(p, r.correta, par.p_t, par.p_g, par.p_s);
    if r.confianca = 'certeza' and not r.correta then certeza := certeza + 1; end if;
    if r.confianca = 'chute' and r.correta then chute := chute + 1; end if;
    itens := itens || jsonb_build_array(jsonb_build_object(
      'criado_em', to_char(r.criado_em at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
      'atividade', case when r.atividade_id is null then null else jsonb_build_object(
        'id', r.atividade_id, 'numero', public.numero_da_atividade(r.atividade_id), 'titulo', r.titulo) end,
      'acertou', r.correta,
      'confianca', r.confianca,
      'p_dominio', round(p, 4)));
  end loop;
  return jsonb_build_object(
    'conceito', conceito,
    'p_inicial', round(par.p_l0, 4),
    'respostas', itens,
    'erros_com_certeza', certeza,
    'acertos_no_chute', chute);
end;
$$;

revoke execute on function
  privado.bkt_acumular(numeric, boolean, numeric, numeric, numeric, numeric),
  privado.bkt_refeito(boolean, numeric, numeric, numeric, numeric),
  privado.bkt_parametros_de(text),
  privado.aluno_conta(uuid, text, boolean),
  privado.moda_estado(text[]),
  public.dominio_reprocessado(uuid, timestamptz, boolean),
  public.estatisticas_turma(uuid, boolean),
  public.evolucao_turma(uuid, boolean),
  public.mapa_aluno(uuid, text),
  public.historico_aluno_conceito(uuid, text, text)
  from public, anon, authenticated;
grant execute on function
  privado.bkt_acumular(numeric, boolean, numeric, numeric, numeric, numeric),
  privado.bkt_refeito(boolean, numeric, numeric, numeric, numeric),
  privado.bkt_parametros_de(text),
  privado.aluno_conta(uuid, text, boolean),
  privado.moda_estado(text[]),
  public.dominio_reprocessado(uuid, timestamptz, boolean),
  public.estatisticas_turma(uuid, boolean),
  public.evolucao_turma(uuid, boolean),
  public.mapa_aluno(uuid, text),
  public.historico_aluno_conceito(uuid, text, text)
  to service_role;
