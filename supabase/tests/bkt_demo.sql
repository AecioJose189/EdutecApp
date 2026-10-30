-- Demonstração do BKT: 5 alunos simulados respondendo a questões de dois conceitos extraídos do
-- PDF de teste (amostras/resultado.json). Pode colar inteiro no SQL Editor do Supabase e rodar
-- quantas vezes quiser: o começo apaga os dados simulados da execução anterior.
--
-- Pseudônimos "demo-..." (RN13: nunca nome ou e-mail). Parâmetros: linha padrão '*' de
-- bkt_parametros (L0=0,3 T=0,1 G=0,2 S=0,1).
--
-- O SQL Editor mostra só o resultado da última consulta, por isso o fim junta tudo numa tabela:
--   bloco "aluno": a evolução do domínio estimado a cada resposta (✓ acerto, ✗ erro);
--   bloco "turma": a view dominio_turma (média, mediana e moda do estado por conceito).
-- Para ver resposta a resposta de um aluno, troque a consulta final pela que está comentada no fim
-- e rode o script inteiro (demo_evolucao é temporária e só existe durante a execução).

-- 1. Apaga a simulação anterior. respostas é só de inserção: o gatilho só libera DELETE de
--    pseudônimos "demo-..." com a chave edutec.limpar_demo ligada nesta transação.
do $$
begin
  perform set_config('edutec.limpar_demo', 'on', true);
  delete from public.respostas where aluno_pseudonimo like 'demo-%';
  delete from public.dominio where aluno_pseudonimo like 'demo-%';
end $$;

-- 2. Sequências de respostas (1 = acerto, 0 = erro), na ordem em que acontecem.
drop table if exists pg_temp.demo_sequencias;
create temp table demo_sequencias (aluno text, perfil text, conceito text, respostas int[]);
insert into demo_sequencias values
  ('demo-ana',    'só acerta',               'Algoritmo Minimax', array[1,1,1,1,1,1]),
  ('demo-ana',    'só acerta',               'Poda Alfa-Beta',    array[1,1,1,1]),
  ('demo-bruno',  'erra e depois aprende',   'Algoritmo Minimax', array[0,0,0,1,1,1,1,1]),
  ('demo-bruno',  'erra e depois aprende',   'Poda Alfa-Beta',    array[0,0,1,1,1]),
  ('demo-carla',  'só erra',                 'Algoritmo Minimax', array[0,0,0,0,0,0]),
  ('demo-carla',  'só erra',                 'Poda Alfa-Beta',    array[0,0,0,0]),
  ('demo-diego',  'oscila',                  'Algoritmo Minimax', array[1,0,1,0,1,1]),
  ('demo-diego',  'oscila',                  'Poda Alfa-Beta',    array[0,1,0,1]),
  ('demo-elisa',  'sabe, mas às vezes erra', 'Algoritmo Minimax', array[1,1,0,1,1,0,1,1]),
  ('demo-elisa',  'sabe, mas às vezes erra', 'Poda Alfa-Beta',    array[1,0,1,1]);

-- 3. Registra as respostas pela função de verdade (registrar_resposta) e guarda a evolução.
drop table if exists pg_temp.demo_evolucao;
create temp table demo_evolucao (
  aluno text, perfil text, conceito text, tentativa int, correta boolean, p_dominio numeric, estado text
);
do $$
declare
  s record;
  r record;
  i int;
begin
  for s in select * from demo_sequencias order by aluno, conceito loop
    for i in 1 .. array_length(s.respostas, 1) loop
      select * into r from public.registrar_resposta(s.aluno, s.conceito, s.respostas[i] = 1);
      insert into demo_evolucao values (s.aluno, s.perfil, s.conceito, i, s.respostas[i] = 1, r.p_dominio, r.estado);
    end loop;
  end loop;
end $$;

-- 4. Resultado: evolução de cada aluno + visão da turma.
select bloco, aluno, perfil, conceito, respostas, evolucao_p_dominio, p_final, estado
from (
  select 1 as ordem, 'aluno' as bloco, e.aluno, e.perfil, e.conceito,
         string_agg(case when e.correta then '✓' else '✗' end, ' ' order by e.tentativa) as respostas,
         '0.300 → ' || string_agg(round(e.p_dominio, 3)::text, ' → ' order by e.tentativa) as evolucao_p_dominio,
         round((array_agg(e.p_dominio order by e.tentativa desc))[1], 3) as p_final,
         (array_agg(e.estado order by e.tentativa desc))[1] as estado
  from demo_evolucao e
  group by e.aluno, e.perfil, e.conceito
  union all
  select 2, 'turma', '(' || t.n_alunos || ' alunos)', 'média ' || t.media_dominio || ' · mediana ' || t.mediana_dominio,
         t.conceito, null, null, t.media_dominio, 'moda: ' || t.estado_moda
  from public.dominio_turma t
  where t.turma_id is null  -- só a demo do BKT (turmas reais têm turma_id)
    and t.conceito in (select conceito from demo_sequencias)
) resultado
order by ordem, conceito, aluno;

-- Resposta a resposta de um aluno (use no lugar da consulta do passo 4):
-- select tentativa, case when correta then '✓ acerto' else '✗ erro' end as resposta,
--        round(p_dominio, 4) as p_dominio, estado
-- from demo_evolucao where aluno = 'demo-bruno' and conceito = 'Algoritmo Minimax' order by tentativa;
