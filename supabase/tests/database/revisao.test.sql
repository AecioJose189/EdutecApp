-- Revisão do aluno (fase 2): minhas_atividades e revisao_tentativa. O gabarito (correta, feedback,
-- rubrica) NUNCA sai antes de a sessão ser encerrada; um aluno não lê a tentativa de outro.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(26);

insert into auth.users (id, email) values
  ('00000000-0000-4000-8000-0000000000ca', 'prof@exemplo.com'),
  ('00000000-0000-4000-8000-000000000c01', 'aluno1@exemplo.com'),
  ('00000000-0000-4000-8000-000000000c02', 'aluno2@exemplo.com');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000cc01', 'IA', 'Turma A', '00000000-0000-4000-8000-0000000000ca');

-- Geração A (atividade 1) e B (atividade 2): Q1 MC (certa = c), Q2 MC (certa = a), Q3 aberta.
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values
 (-50, repeat('a', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [{"nome": "Base", "prerequisitos": []}],
   "questoes": [{"tipo": "multipla_escolha", "enunciado": "Primeira", "conceitos": ["Base"],
     "alternativas": [{"texto": "um", "correta": true, "feedback": "FB-A1"}, {"texto": "dois", "correta": false, "feedback": "FB-A2"}]}]}'),
 (-51, repeat('b', 64), 3, 'mista', 'media', '{"conceitos": [{"nome": "Base", "prerequisitos": []}, {"nome": "Topo", "prerequisitos": ["Base"]}],
   "questoes": [
     {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"],
      "alternativas": [{"texto": "x", "correta": false, "feedback": "FB-1a"}, {"texto": "y", "correta": false, "feedback": "FB-1b"},
                       {"texto": "z", "correta": true, "feedback": "FB-1c"}]},
     {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Topo"],
      "alternativas": [{"texto": "p", "correta": true, "feedback": "FB-2a"}, {"texto": "q", "correta": false, "feedback": "FB-2b"}]},
     {"tipo": "aberta", "enunciado": "Q3", "conceitos": ["Topo"], "alternativas": null, "rubrica": "RUBRICA-SECRETA"}]}');

select publicar_atividade('00000000-0000-4000-8000-00000000cc01', -50, true, null, 'Primeira atividade');
select publicar_atividade('00000000-0000-4000-8000-00000000cc01', -51, true, null, 'Probabilidade');
update atividades set publicada_em = now() - interval '1 day' where geracao_id = -50;

insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005c01', id from atividades where geracao_id = -51;
insert into sessoes (id, atividade_id, aberta, entrada_aberta, encerrada_em)
  select '00000000-0000-4000-8000-000000005c00', id, false, false, now() from atividades where geracao_id = -50;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000f001', '00000000-0000-4000-8000-000000005c01', '00000000-0000-4000-8000-000000000c01', 'Um'),
  ('00000000-0000-4000-8000-00000000f002', '00000000-0000-4000-8000-000000005c01', '00000000-0000-4000-8000-000000000c02', 'Dois');

-- Aluno 1: Q1 certa (c), Q2 em branco, Q3 com texto.
select enviar_tentativa('00000000-0000-4000-8000-00000000f001', '00000000-0000-4000-8000-000000000c01',
  '[{"questao_id": "1", "alternativa_id": "c"}, {"questao_id": "3", "texto": "minha resposta"}]');

-- Tentativa antiga, já enviada, na sessão encerrada da atividade 1 (para a ordem e o gabarito liberado).
insert into tentativas (id, sessao_id, aluno_id, aluno_nome, enviada_em, acertos, total_multipla_escolha,
                        abertas_aguardando_correcao, respostas) values
  ('00000000-0000-4000-8000-00000000f000', '00000000-0000-4000-8000-000000005c00', '00000000-0000-4000-8000-000000000c01',
   'Um', now() - interval '1 day', 0, 1, 0,
   '[{"questao_id": "1", "tipo": "multipla_escolha", "alternativa_id": "b", "correta": false, "situacao": "corrigida"}]');

-- ---------- sessão ABERTA: sem gabarito ----------
create temp table rev as select revisao_tentativa('00000000-0000-4000-8000-00000000f001',
  '00000000-0000-4000-8000-000000000c01') as j;

select is((select j -> 'gabarito_liberado' from rev), 'false'::jsonb, 'sessão aberta: gabarito_liberado false');
select ok((select j::text !~ '"(correta|feedback|rubrica)"' from rev), 'sessão aberta: nenhum campo correta/feedback/rubrica');
select ok((select j::text !~ '(FB-|RUBRICA-SECRETA)' from rev), 'sessão aberta: nenhum texto de feedback ou rubrica');
select is((select jsonb_agg(q -> 'acertou') from rev, jsonb_array_elements(j -> 'questoes') q),
  '[true, false, null]'::jsonb, 'acertou: certa, em branco (false), aberta (null)');
select is((select jsonb_agg(q -> 'minha_resposta') from rev, jsonb_array_elements(j -> 'questoes') q),
  '[{"alternativa_id": "c"}, {}, {"texto": "minha resposta"}]'::jsonb, 'minha_resposta ({} quando em branco)');
select is((select array(select jsonb_object_keys(j -> 'questoes' -> 0 -> 'alternativas' -> 0)) from rev),
  array['id', 'texto'], 'alternativas só com id e texto');
select ok((select not (j -> 'questoes' -> 2 ? 'rubrica') and not (j -> 'questoes' -> 2 ? 'alternativas') from rev),
  'aberta sem rubrica (o campo some) e sem alternativas');
select is((select j -> 'questoes' -> 0 -> 'conceitos' from rev), '["Base"]'::jsonb, 'conceitos da questão');
select is((select j -> 'atividade' from rev),
  jsonb_build_object('id', (select id from atividades where geracao_id = -51), 'numero', 2, 'titulo', 'Probabilidade'),
  'atividade {id, numero 2, titulo}');
select is((select (j ->> 'acertos')::int || '/' || (j ->> 'total_multipla_escolha') from rev), '1/2', 'acertos e total');

-- ---------- quem pode ler ----------
select throws_ok($$ select revisao_tentativa('00000000-0000-4000-8000-00000000f001', '00000000-0000-4000-8000-000000000c02') $$,
  'LU403', null, 'um aluno não lê a tentativa de outro');
select throws_ok($$ select revisao_tentativa('00000000-0000-4000-8000-00000000f002', '00000000-0000-4000-8000-000000000c02') $$,
  'LU403', null, 'tentativa ainda não enviada não tem revisão');
select throws_ok($$ select revisao_tentativa('00000000-0000-4000-8000-00000000ffff', '00000000-0000-4000-8000-000000000c01') $$,
  'LU403', null, 'tentativa inexistente');

-- ---------- minhas_atividades ----------
create temp table minhas as select minhas_atividades('00000000-0000-4000-8000-000000000c01') as j;
select is((select jsonb_agg(a -> 'tentativa_id') from minhas, jsonb_array_elements(j -> 'atividades') a),
  '["00000000-0000-4000-8000-00000000f001", "00000000-0000-4000-8000-00000000f000"]'::jsonb,
  'da mais recente para a mais antiga');
select is((select ((j -> 'atividades' -> 0) - 'enviada_em') - 'tentativa_id' from minhas), jsonb_build_object(
    'atividade', jsonb_build_object('id', (select id from atividades where geracao_id = -51), 'numero', 2, 'titulo', 'Probabilidade'),
    'turma', '{"id": "00000000-0000-4000-8000-00000000cc01", "nome": "IA", "codigo": "Turma A"}'::jsonb,
    'acertos', 1, 'total_multipla_escolha', 2, 'gabarito_liberado', false),
  'item no formato do contrato (sessão aberta → gabarito_liberado false)');
select ok((select (j -> 'atividades' -> 0 ->> 'enviada_em') ~ '^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$' from minhas),
  'enviada_em em ISO 8601 UTC');
select is((select j -> 'atividades' -> 1 -> 'gabarito_liberado' from minhas), 'true'::jsonb,
  'sessão encerrada → gabarito_liberado true');
select is((select j -> 'atividades' -> 1 -> 'atividade' ->> 'numero' from minhas), '1', 'atividade mais antiga é a nº 1');
select is(minhas_atividades('00000000-0000-4000-8000-000000000c02'), '{"atividades": []}'::jsonb,
  'tentativa não enviada não aparece');
select is(jsonb_array_length(minhas_atividades('00000000-0000-4000-8000-000000000c01', gen_random_uuid()) -> 'atividades'), 0,
  'filtro por turma');

-- ---------- sessão ENCERRADA: gabarito completo ----------
update sessoes set aberta = false, entrada_aberta = false, encerrada_em = now() where id = '00000000-0000-4000-8000-000000005c01';
create temp table rev2 as select revisao_tentativa('00000000-0000-4000-8000-00000000f001',
  '00000000-0000-4000-8000-000000000c01') as j;
select is((select j -> 'gabarito_liberado' from rev2), 'true'::jsonb, 'encerrada: gabarito_liberado true');
select is((select j -> 'questoes' -> 0 -> 'alternativas' -> 2 from rev2),
  '{"id": "c", "texto": "z", "correta": true, "feedback": "FB-1c"}'::jsonb, 'encerrada: correta e feedback por alternativa');
select is((select j -> 'questoes' -> 2 ->> 'rubrica' from rev2), 'RUBRICA-SECRETA', 'encerrada: rubrica da aberta');
select is((select jsonb_agg(q -> 'acertou') from rev2, jsonb_array_elements(j -> 'questoes') q),
  '[true, false, null]'::jsonb, 'acertou continua igual');

-- ---------- privilégios ----------
select ok(not has_function_privilege('authenticated', 'public.revisao_tentativa(uuid, uuid)', 'execute')
      and not has_function_privilege('anon', 'public.revisao_tentativa(uuid, uuid)', 'execute'),
  'revisao_tentativa: só a service_role (via aluno-api)');
select ok(not has_function_privilege('authenticated', 'public.minhas_atividades(uuid, uuid)', 'execute')
      and not has_function_privilege('anon', 'public.minhas_atividades(uuid, uuid)', 'execute'),
  'minhas_atividades: só a service_role (via aluno-api)');

select * from finish();
rollback;
