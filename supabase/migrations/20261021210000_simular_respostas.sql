-- DEMONSTRAÇÃO: simula 25 alunos (pseudônimos) respondendo às questões publicadas numa turma.
--
-- Não é dado real. Serve para mostrar o grafo da turma, o BKT e a "causa raiz provável" sem alunos
-- de verdade. Cada execução apaga a simulação anterior da turma (pseudônimos "sim-<turma>-NN") e
-- refaz tudo com a mesma semente: o resultado é reproduzível para as mesmas questões publicadas.
--
-- Modelo (a história que os dados contam):
--   - cada aluno tem uma habilidade oculta h ~ Normal(0, 1);
--   - cada conceito tem uma dificuldade d que cresce com a profundidade no grafo (conceitos-base são
--     fáceis, os que dependem de muitos outros são difíceis), com um pequeno ruído;
--   - o aluno domina o conceito com probabilidade sigmoide(1,8·(h − d)); se não dominar algum
--     pré-requisito direto, essa chance cai para 30% (quem não domina a base erra mais o que depende dela);
--   - para cada questão publicada e cada conceito dela, o aluno responde algumas vezes (tentativa e
--     "refazer"): acerta com 90% se domina (deslize 10%) e 20% se não domina (chute) — os mesmos S e G
--     do BKT; depois de um erro, pode aprender (10%, o T do BKT), se dominar os pré-requisitos.
-- As respostas passam por registrar_resposta, como as de alunos reais.

create or replace function public.simular_respostas(turma uuid, semente double precision default 0.42)
returns table (n_alunos int, n_respostas int)
language plpgsql
set search_path = ''
as $$
declare
  ALUNOS constant int := 25;
  TENTATIVAS constant int := 3;      -- respostas por questão e conceito (1ª + 2 "refazer")
  P_ACERTO_DOMINA constant float := 0.9;
  P_ACERTO_CHUTE constant float := 0.2;
  P_APRENDER constant float := 0.1;
  PENALIDADE_PRE constant float := 0.3;
  max_prof int;
  a int;
  t int;
  aluno text;
  hab float;
  c record;
  q record;
  sabe boolean;
  correta boolean;
  total int := 0;
begin
  perform 1 from public.turmas tt where tt.id = turma for update;
  if not found then
    raise exception 'Turma não encontrada.' using errcode = 'ED404';
  end if;

  -- 1. Apaga a simulação anterior desta turma (o gatilho de respostas exige a chave de sessão).
  perform set_config('edutec.limpar_demo', 'on', true);
  delete from public.respostas r where r.turma_id = turma and r.aluno_pseudonimo like 'sim-%';
  delete from public.dominio d where d.turma_id = turma and d.aluno_pseudonimo like 'sim-%';

  -- 2. Semente fixa: tudo o que vem de random() abaixo se repete a cada execução.
  perform setseed(semente);

  -- 3. Conceitos da turma com profundidade (maior caminho de pré-requisitos) e dificuldade.
  create temp table if not exists pg_temp.sim_conceitos (nome text primary key, prof int, prerequisitos text[], dificuldade float)
    on commit drop;
  truncate pg_temp.sim_conceitos;
  insert into pg_temp.sim_conceitos (nome, prof, prerequisitos)
  with recursive caminho (nome, prof, visitados) as (
    select ct.nome, 0, array[ct.nome] from public.conceitos_turma ct where ct.turma_id = turma
    union all
    select ct.nome, c2.prof + 1, c2.visitados || ct.nome
      from caminho c2
      join public.conceitos_turma ct on ct.turma_id = turma and c2.nome = any (ct.prerequisitos)
     where not ct.nome = any (c2.visitados)
  )
  select cm.nome, max(cm.prof), (select ct.prerequisitos from public.conceitos_turma ct
                                  where ct.turma_id = turma and ct.nome = cm.nome)
    from caminho cm group by cm.nome;

  select greatest(max(sc.prof), 1) into max_prof from pg_temp.sim_conceitos sc;
  -- Base ≈ −3 (quase todos dominam) até ≈ +1,6 nos mais profundos; ruído de ±0,25 por conceito.
  for c in select * from pg_temp.sim_conceitos order by prof, nome loop
    update pg_temp.sim_conceitos set dificuldade = -3.0 + 4.6 * c.prof / max_prof + (random() - 0.5) * 0.5
     where nome = c.nome;
  end loop;

  -- 4. Para cada aluno: quais conceitos ele domina (em ordem de profundidade) e as respostas.
  create temp table if not exists pg_temp.sim_domina (conceito text primary key, sabe boolean) on commit drop;

  for a in 1 .. ALUNOS loop
    aluno := 'sim-' || turma || '-' || lpad(a::text, 2, '0');
    -- Normal(0, 1) por Box-Muller.
    hab := sqrt(-2 * ln(1 - random())) * cos(2 * pi() * random());

    truncate pg_temp.sim_domina;
    for c in select * from pg_temp.sim_conceitos order by prof, nome loop
      insert into pg_temp.sim_domina values (
        c.nome,
        random() < (1 / (1 + exp(-1.8 * (hab - c.dificuldade))))
          * case when exists (select 1 from pg_temp.sim_domina sd where sd.conceito = any (c.prerequisitos) and not sd.sabe)
                 then PENALIDADE_PRE else 1 end
      );
    end loop;

    -- Questões publicadas, na ordem de publicação; cada conceito da questão (que esteja no grafo).
    for q in
      select qc.conceito
        from public.atividades atv
        cross join lateral jsonb_array_elements(atv.questoes) with ordinality as qs (questao, i)
        cross join lateral jsonb_array_elements_text(qs.questao -> 'conceitos') with ordinality as qc (conceito, j)
       where atv.turma_id = turma and qc.conceito in (select sc.nome from pg_temp.sim_conceitos sc)
       order by atv.publicada_em, atv.id, qs.i, qc.j
    loop
      for t in 1 .. TENTATIVAS loop
        select sd.sabe into sabe from pg_temp.sim_domina sd where sd.conceito = q.conceito;
        correta := random() < case when sabe then P_ACERTO_DOMINA else P_ACERTO_CHUTE end;
        perform public.registrar_resposta(aluno, q.conceito, correta, turma);
        total := total + 1;
        -- Depois de errar, pode aprender ao refazer, se já domina os pré-requisitos diretos.
        if not sabe and not correta and random() < P_APRENDER and not exists (
             select 1 from pg_temp.sim_domina sd join pg_temp.sim_conceitos sc on sc.nome = q.conceito
              where sd.conceito = any (sc.prerequisitos) and not sd.sabe) then
          update pg_temp.sim_domina set sabe = true where conceito = q.conceito;
        end if;
      end loop;
    end loop;
  end loop;

  return query select ALUNOS, total;
end;
$$;

revoke execute on function public.simular_respostas(uuid, double precision) from public, anon, authenticated;
grant execute on function public.simular_respostas(uuid, double precision) to service_role;
