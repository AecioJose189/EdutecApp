-- Confiança na resposta (fase 2b): gravada no jsonb da tentativa e em respostas, devolvida na revisão,
-- BKT igual, estatísticas da turma (só múltipla escolha).
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(15);

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000ea', 'prof@exemplo.com', '{"full_name": "Prof"}'),
  ('00000000-0000-4000-8000-000000000e01', 'ana@exemplo.com', '{"full_name": "Ana"}'),
  ('00000000-0000-4000-8000-000000000e02', 'bia@exemplo.com', '{"full_name": "Bia"}');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000ce01', 'IA', 'A', '00000000-0000-4000-8000-0000000000ea');

-- Q1 (Base e Meio, certa = a), Q2 (Meio, certa = a), Q3 (Topo, certa = a), Q4 aberta (Topo).
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-70, repeat('e', 64), 4, 'mista', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Meio", "prerequisitos": ["Base"]}, {"nome": "Topo", "prerequisitos": ["Meio"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base", "Meio"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Meio"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q3", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "aberta", "enunciado": "Q4", "conceitos": ["Topo"], "alternativas": null, "rubrica": "r"}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000ce01', -70, true, null);
insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005e71', id from atividades where geracao_id = -70;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000e701', '00000000-0000-4000-8000-000000005e71', '00000000-0000-4000-8000-000000000e01', 'Ana'),
  ('00000000-0000-4000-8000-00000000e702', '00000000-0000-4000-8000-000000005e71', '00000000-0000-4000-8000-000000000e02', 'Bia');

-- Ana: Q1 errada com "certeza", Q2 certa no "chute", Q3 certa com valor inválido, Q4 aberta "acho".
select enviar_tentativa('00000000-0000-4000-8000-00000000e701', '00000000-0000-4000-8000-000000000e01', '[
  {"questao_id": "1", "alternativa_id": "b", "confianca": "certeza"},
  {"questao_id": "2", "alternativa_id": "a", "confianca": "Chute"},
  {"questao_id": "3", "alternativa_id": "a", "confianca": "talvez"},
  {"questao_id": "4", "texto": "t", "confianca": "acho"}]');
-- Bia (site antigo): as mesmas respostas, sem confiança.
select enviar_tentativa('00000000-0000-4000-8000-00000000e702', '00000000-0000-4000-8000-000000000e02', '[
  {"questao_id": "1", "alternativa_id": "b"}, {"questao_id": "2", "alternativa_id": "a"},
  {"questao_id": "3", "alternativa_id": "a"}, {"questao_id": "4", "texto": "t"}]');

select is((select jsonb_agg(r -> 'confianca') from tentativas t, jsonb_array_elements(t.respostas) r
            where t.id = '00000000-0000-4000-8000-00000000e701'),
  '["certeza", "chute", null, "acho"]'::jsonb, 'jsonb da tentativa: certeza, chute (minúsculas), inválido → null, aberta');
select is((select jsonb_agg(r -> 'confianca') from tentativas t, jsonb_array_elements(t.respostas) r
            where t.id = '00000000-0000-4000-8000-00000000e702'),
  '[null, null, null, null]'::jsonb, 'sem o campo (site antigo): null');
select is((select array_agg(conceito || ':' || coalesce(confianca, '-') order by conceito, confianca) from respostas
            where tentativa_id = '00000000-0000-4000-8000-00000000e701'),
  array['Base:certeza', 'Meio:certeza', 'Meio:chute', 'Topo:-'], 'respostas.confianca em cada conceito');
select ok((select bool_and(confianca is null) from respostas where tentativa_id = '00000000-0000-4000-8000-00000000e702'),
  'respostas sem confiança ficam nulas');

-- BKT não muda: Ana e Bia responderam igual, com e sem confiança.
select is((select array_agg(round(p_dominio, 12) order by conceito) from dominio where aluno_id = '00000000-0000-4000-8000-000000000e01'),
          (select array_agg(round(p_dominio, 12) order by conceito) from dominio where aluno_id = '00000000-0000-4000-8000-000000000e02'),
  'BKT igual com e sem confiança');
select ok(abs((select p_dominio from dominio where aluno_id = '00000000-0000-4000-8000-000000000e01' and conceito = 'Topo')
              - 142.0/205) < 1e-9, 'BKT: um acerto a partir de L0 continua 142/205');

-- Revisão devolve a confiança.
select is((select jsonb_agg(q -> 'confianca') from jsonb_array_elements(
             revisao_tentativa('00000000-0000-4000-8000-00000000e701', '00000000-0000-4000-8000-000000000e01') -> 'questoes') q),
  '["certeza", "chute", null, "acho"]'::jsonb, 'revisao_tentativa: confianca de cada questão');
select ok((select bool_and(q ? 'confianca') from jsonb_array_elements(
             revisao_tentativa('00000000-0000-4000-8000-00000000e702', '00000000-0000-4000-8000-000000000e02') -> 'questoes') q),
  'sem confiança: o campo vem (null)');

-- Estatísticas.
create temp table est as select confianca_turma('00000000-0000-4000-8000-00000000ce01') as j;
select is((select j -> 'turma' from est),
  '{"n_respostas": 6, "n_com_confianca": 2, "erros_com_certeza": 1, "acertos_no_chute": 1}'::jsonb,
  'turma: 6 de múltipla escolha (abertas fora), 2 com confiança, 1 erro com certeza, 1 acerto no chute');
select is((select c from est, jsonb_array_elements(j -> 'conceitos') c where c ->> 'conceito' = 'Meio'),
  '{"conceito": "Meio", "n_respostas": 4, "n_com_confianca": 2, "erros_com_certeza": 1, "acertos_no_chute": 1}'::jsonb,
  'conceito Meio (Q1 e Q2 de cada aluna)');
select is((select c from est, jsonb_array_elements(j -> 'conceitos') c where c ->> 'conceito' = 'Topo'),
  '{"conceito": "Topo", "n_respostas": 2, "n_com_confianca": 0, "erros_com_certeza": 0, "acertos_no_chute": 0}'::jsonb,
  'conceito Topo: a aberta não conta');
select is((select a from est, jsonb_array_elements(j -> 'alunos') a where a ->> 'nome' = 'Ana'),
  '{"aluno_id": "00000000-0000-4000-8000-000000000e01", "nome": "Ana", "n_respostas": 3, "n_com_confianca": 2,
    "erros_com_certeza": 1, "acertos_no_chute": 1}'::jsonb, 'por aluno: Ana');
select is(confianca_turma(gen_random_uuid()),
  '{"turma": {"n_respostas": 0, "n_com_confianca": 0, "erros_com_certeza": 0, "acertos_no_chute": 0}, "conceitos": [], "alunos": []}'::jsonb,
  'turma sem respostas: zeros e listas vazias');

select lives_ok($$ select registrar_resposta('sim-x-01', 'Base', true, '00000000-0000-4000-8000-00000000ce01') $$,
  'chamada antiga de registrar_resposta (simulação) continua valendo');
select ok(not has_function_privilege('authenticated', 'public.confianca_turma(uuid)', 'execute')
      and not has_function_privilege('anon', 'public.confianca_turma(uuid)', 'execute'),
  'confianca_turma: só a service_role (via turma-api)');

select * from finish();
rollback;
