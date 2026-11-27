-- Alternativas embaralhadas por tentativa (fase 2b): ordem própria e estável de cada tentativa, ids
-- originais (a correção não muda), questões na mesma ordem, revisão na ordem que o aluno viu.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(12);

insert into auth.users (id, email) values
  ('00000000-0000-4000-8000-0000000000da', 'prof@exemplo.com'),
  ('00000000-0000-4000-8000-000000000d01', 'aluno1@exemplo.com'),
  ('00000000-0000-4000-8000-000000000d02', 'aluno2@exemplo.com');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000cd01', 'IA', 'A', '00000000-0000-4000-8000-0000000000da');

-- Q1 MC com 4 alternativas (certa = c), Q2 MC com 3 (certa = a), Q3 aberta.
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-60, repeat('d', 64), 3, 'mista', 'media', '{"conceitos": [{"nome": "Base", "prerequisitos": []}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"],
     "alternativas": [{"texto": "w", "correta": false, "feedback": "f"}, {"texto": "x", "correta": false, "feedback": "f"},
                      {"texto": "y", "correta": true, "feedback": "f"}, {"texto": "z", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Base"],
     "alternativas": [{"texto": "p", "correta": true, "feedback": "f"}, {"texto": "q", "correta": false, "feedback": "f"},
                      {"texto": "r", "correta": false, "feedback": "f"}]},
    {"tipo": "aberta", "enunciado": "Q3", "conceitos": ["Base"], "alternativas": null, "rubrica": "r"}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cd01', -60, true, null);
insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005d01', id from atividades where geracao_id = -60;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000d001', '00000000-0000-4000-8000-000000005d01', '00000000-0000-4000-8000-000000000d01', 'Um'),
  ('00000000-0000-4000-8000-00000000d002', '00000000-0000-4000-8000-000000005d01', '00000000-0000-4000-8000-000000000d02', 'Dois');

create temp table q1 as select questoes_da_tentativa('00000000-0000-4000-8000-00000000d001') as j;

select is(questoes_da_tentativa('00000000-0000-4000-8000-00000000d001'), (select j from q1),
  'mesma tentativa, mesma ordem (recarregar a página)');
select is((select jsonb_agg(q -> 'id') from q1, jsonb_array_elements(j) q), '["1", "2", "3"]'::jsonb,
  'a ordem das questões não muda');
select is((select array_agg(a ->> 'id' order by a ->> 'id') from q1, jsonb_array_elements(j -> 0 -> 'alternativas') a),
  array['a', 'b', 'c', 'd'], 'ids originais: as mesmas alternativas, só em outra ordem');
select is((select array_agg(a ->> 'texto' order by a ->> 'id') from q1, jsonb_array_elements(j -> 0 -> 'alternativas') a),
  array['w', 'x', 'y', 'z'], 'cada id continua com o seu texto');
select is((select j -> 2 -> 'alternativas' from q1), 'null'::jsonb, 'aberta continua sem alternativas');
select ok((select j::text !~ '"(correta|feedback|rubrica)"' from q1), 'sem gabarito');
select ok(questoes_da_tentativa(gen_random_uuid()) is null, 'tentativa inexistente → null');

-- Ordem sorteada de verdade: em 300 tentativas fictícias (uuids distintos e fixos, teste reproduzível),
-- a ordem original (a, b, c, d) aparece pouco (esperado 1/24) e há muitas ordens diferentes.
create temp table sorteios as
  select g, (select string_agg(x, '' order by chave_alternativa(md5(g::text)::uuid, '1', x))
               from unnest(array['a','b','c','d']) x) as ordem
    from generate_series(1, 300) g;
select diag('ordens distintas em 300 tentativas: ' || (select count(distinct ordem) from sorteios) ||
            ' de 24; original (abcd): ' || (select count(*) filter (where ordem = 'abcd') from sorteios));
select ok((select count(*) filter (where ordem = 'abcd') from sorteios) < 45, 'ordem original é rara (< 15%)');
select ok((select count(distinct ordem) from sorteios) >= 18, 'muitas ordens diferentes entre tentativas');

-- Correção pelo id, qualquer que seja a posição: aluno 1 marca o id certo (c, a).
select results_eq($$ select acertos, total_multipla_escolha from enviar_tentativa('00000000-0000-4000-8000-00000000d001',
    '00000000-0000-4000-8000-000000000d01', '[{"questao_id": "1", "alternativa_id": "c"}, {"questao_id": "2", "alternativa_id": "a"}]') $$,
  $$ values (2, 2) $$, 'correção pelo id original, com a ordem trocada');

-- Revisão na ordem que o aluno viu.
select is((select jsonb_agg(a -> 'id') from jsonb_array_elements(
             revisao_tentativa('00000000-0000-4000-8000-00000000d001', '00000000-0000-4000-8000-000000000d01')
             -> 'questoes' -> 0 -> 'alternativas') a),
          (select jsonb_agg(a -> 'id') from q1, jsonb_array_elements(j -> 0 -> 'alternativas') a),
  'revisão mostra as alternativas na ordem da tentativa');

select ok(not has_function_privilege('authenticated', 'public.questoes_da_tentativa(uuid)', 'execute'),
  'questoes_da_tentativa: só a service_role');

select * from finish();
rollback;
