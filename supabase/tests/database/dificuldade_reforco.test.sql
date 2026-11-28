-- Fase 5: alunos com dificuldade num conceito e contexto da atividade de reforço (origem do conceito,
-- PDFs diferentes, enunciados já publicados, fusões) e as colunas de foco do cache (RN12).
-- Rodar: npx supabase test db supabase/tests/database   (local)  ou  --linked
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(26);

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000da', 'prof@exemplo.com', '{"full_name": "Prof"}'),
  ('00000000-0000-4000-8000-000000000d01', 'ana@exemplo.com', '{"full_name": "Ana"}'),
  ('00000000-0000-4000-8000-000000000d02', 'bia@exemplo.com', '{"full_name": "Bia"}'),
  ('00000000-0000-4000-8000-000000000d03', 'caio@exemplo.com', '{"full_name": "Caio"}');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000cd01', 'IA', 'A', '00000000-0000-4000-8000-0000000000da');
insert into membros_turma (turma_id, aluno_id) values
  ('00000000-0000-4000-8000-00000000cd01', '00000000-0000-4000-8000-000000000d01'),
  ('00000000-0000-4000-8000-00000000cd01', '00000000-0000-4000-8000-000000000d02'),
  ('00000000-0000-4000-8000-00000000cd01', '00000000-0000-4000-8000-000000000d03');

-- PDF A (hash a…): Base, Meio, Aux. PDF B (hash b…): "meio" (mesmo nome normalizado), Topo. PDF A de novo
-- com outros parâmetros (geração -112): mesmo PDF.
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values
 (-110, repeat('a', 64), 3, 'multipla_escolha', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Meio", "prerequisitos": ["Base"]}, {"nome": "Aux", "prerequisitos": []}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "QA1 base", "conceitos": ["Base"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "QA2 meio", "conceitos": ["Meio"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "QA3 aux", "conceitos": ["Aux"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}'),
 (-111, repeat('b', 64), 2, 'multipla_escolha', 'media', '{"conceitos": [
    {"nome": "meio", "prerequisitos": []}, {"nome": "Topo", "prerequisitos": ["meio"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "QB1 meio", "conceitos": ["meio"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "QB2 topo", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}'),
 (-112, repeat('a', 64), 1, 'multipla_escolha', 'dificil', '{"conceitos": [{"nome": "Topo", "prerequisitos": []}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "QA4 topo", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cd01', -110, true, null, 'Material A');
select publicar_atividade('00000000-0000-4000-8000-00000000cd01', -111, true, null, 'Material B');
-- Mesma transação = mesmo now(): datas explícitas para a ordem ser a da publicação.
update atividades set publicada_em = '2026-11-01' where geracao_id = -110;
update atividades set publicada_em = '2026-11-02' where geracao_id = -111;

create temp table o as select * from origem_dos_conceitos('00000000-0000-4000-8000-00000000cd01');
select is((select array_agg(conceito || ':' || coalesce(titulo, '-') || ':' || coalesce(numero::text, '-') order by conceito) from o),
  array['Aux:Material A:1', 'Base:Material A:1', 'Meio:Material A:1', 'Topo:Material B:2'],
  'origem = primeira atividade com o conceito (Meio vem do A, mesmo aparecendo como "meio" no B)');
select is((select hash_pdf from o where conceito = 'Topo'), repeat('b', 64), 'origem traz o hash do PDF');
select is((select count(*)::int from origem_dos_conceitos('00000000-0000-4000-8000-00000000cd01', array['Base'])), 1,
  'filtro por conceitos');

-- ---------------------------------------------------------------------------
-- Contexto do reforço
-- ---------------------------------------------------------------------------

create temp table cx as select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Meio', 'Base']) as j;
select is((select j - 'atividade' - 'conceitos' - 'enunciados' from cx),
  jsonb_build_object('principal', 'Meio', 'geracao_id', -110, 'hash_pdf', repeat('a', 64), 'outros_materiais', '[]'::jsonb),
  'mesmo PDF: principal Meio, PDF A, sem outros materiais');
select is((select j -> 'enunciados' from cx), '["QA1 base", "QA2 meio", "QB1 meio"]'::jsonb,
  'enunciados já publicados desses conceitos, de todas as atividades, em ordem');
select is((select j -> 'conceitos' -> 0 from cx),
  '{"conceito": "Meio", "atividade": {"numero": 1, "titulo": "Material A"}, "mesmo_material": true}'::jsonb,
  'conceitos na ordem pedida (principal primeiro)');

create temp table cx2 as select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Topo', 'Base']) as j;
select is((select j ->> 'hash_pdf' from cx2), repeat('b', 64), 'PDFs diferentes: vale o do principal (Topo → B)');
select is((select j -> 'outros_materiais' from cx2), '["Base"]'::jsonb, 'outros_materiais lista quem veio de outro PDF');
select is((select j -> 'conceitos' -> 1 -> 'mesmo_material' from cx2), 'false'::jsonb, 'Base marcado como de outro material');

-- Outra atividade do mesmo PDF A (outra geração): Topo também está nela, mas a origem continua a primeira.
select publicar_atividade('00000000-0000-4000-8000-00000000cd01', -112, true, null, 'Material A difícil');
update atividades set publicada_em = '2026-11-03' where geracao_id = -112;
select is((select j -> 'enunciados' from (select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Topo']) j) x),
  '["QB2 topo", "QA4 topo"]'::jsonb, 'enunciados de Topo nas duas atividades');
select is((select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Topo', 'Aux']) -> 'outros_materiais'),
  '["Aux"]'::jsonb, 'Aux (PDF A) é de outro material que Topo (PDF B)');

-- Fusão: Aux fundido em Topo → Topo herda o nome "Aux", que aparece primeiro no PDF A.
select privado.fundir_conceitos_interno('00000000-0000-4000-8000-00000000cd01', 'Aux', 'Topo',
                                        '00000000-0000-4000-8000-0000000000da', false);
select is((select titulo from origem_dos_conceitos('00000000-0000-4000-8000-00000000cd01', array['Topo'])), 'Material A',
  'fusão: origem segue os nomes fundidos (Aux veio do A)');
select is((select j -> 'enunciados' from (select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Topo']) j) x),
  '["QA3 aux", "QB2 topo", "QA4 topo"]'::jsonb, 'fusão: a questão de Aux (agora Topo) entra nos enunciados');

select throws_ok($$ select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Nada']) $$,
  'LU404', null, 'conceito fora da turma: LU404');
select throws_ok($$ select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array[]::text[]) $$,
  'LU404', null, 'nenhum conceito: LU404');
insert into conceitos_turma (turma_id, nome) values ('00000000-0000-4000-8000-00000000cd01', 'Solto');
select throws_ok($$ select contexto_reforco('00000000-0000-4000-8000-00000000cd01', array['Solto']) $$,
  'LU409', null, 'principal sem material de origem: LU409');

-- ---------------------------------------------------------------------------
-- Alunos com dificuldade
-- ---------------------------------------------------------------------------

-- Ana erra Base com certeza (revisar); Bia acerta no chute (em desenvolvimento); Caio sem respostas.
select registrar_resposta('00000000-0000-4000-8000-000000000d01', 'Base', false, '00000000-0000-4000-8000-00000000cd01',
                          '00000000-0000-4000-8000-000000000d01', null, 'certeza');
select registrar_resposta('00000000-0000-4000-8000-000000000d02', 'Base', true, '00000000-0000-4000-8000-00000000cd01',
                          '00000000-0000-4000-8000-000000000d02', null, 'chute');
-- Simulação na mesma turma: não aparece (só alunos reais).
select registrar_resposta('sim-cd01-01', 'Base', false, '00000000-0000-4000-8000-00000000cd01');

create temp table ad as select alunos_do_conceito('00000000-0000-4000-8000-00000000cd01', 'Base') as j;
select is((select jsonb_agg(a ->> 'nome') from ad, jsonb_array_elements(j -> 'alunos') a), '["Ana", "Bia", "Caio"]'::jsonb,
  'só alunos reais, os piores primeiro, sem dados no fim');
select is((select (j -> 'alunos' -> 0) - 'aluno_id' - 'p_dominio' from ad),
  '{"nome": "Ana", "estado": "revisar", "n_respostas": 1, "erros_com_certeza": 1, "acertos_no_chute": 0,
    "treino": {"n_treinos": 0, "acertos": 0}}'::jsonb, 'Ana: revisar, 1 erro com certeza');
select is((select (j -> 'alunos' -> 1) ->> 'estado' from ad), 'em_desenvolvimento', 'Bia: em desenvolvimento');
select is((select (j -> 'alunos' -> 1) -> 'acertos_no_chute' from ad), '1'::jsonb, 'Bia: 1 acerto no chute');
select is((select (j -> 'alunos' -> 2) - 'aluno_id' from ad),
  '{"nome": "Caio", "estado": null, "p_dominio": null, "n_respostas": 0, "erros_com_certeza": 0, "acertos_no_chute": 0,
    "treino": {"n_treinos": 0, "acertos": 0}}'::jsonb, 'Caio: sem dados');
select ok(abs((select (j -> 'alunos' -> 0 ->> 'p_dominio')::numeric from ad)
              - (select round(p_dominio, 4) from dominio where aluno_id = '00000000-0000-4000-8000-000000000d01' and conceito = 'Base')) = 0,
  'domínio estimado igual ao da tabela dominio (o mesmo do mapa do aluno)');
select is(alunos_do_conceito('00000000-0000-4000-8000-00000000cd01', 'Meio') -> 'alunos' -> 0 -> 'estado', 'null'::jsonb,
  'conceito sem respostas: todos sem dados');

-- ---------------------------------------------------------------------------
-- Cache com foco e acesso
-- ---------------------------------------------------------------------------

select throws_ok($$ insert into geracoes (hash_pdf, quantidade, formato, dificuldade, resultado, foco)
                    values (repeat('a', 64), 1, 'aberta', 'media', '{}', array['Base']) $$,
  '23514', null, 'foco sem foco_chave é recusado');
select lives_ok($$ insert into geracoes (hash_pdf, quantidade, formato, dificuldade, resultado, foco, foco_chave)
                   values (repeat('a', 64), 1, 'aberta', 'media', '{}', array['Base'], repeat('f', 64)) $$,
  'geração de reforço com foco e foco_chave');
select ok(not has_function_privilege('authenticated', 'public.alunos_do_conceito(uuid, text)', 'execute')
      and not has_function_privilege('authenticated', 'public.contexto_reforco(uuid, text[])', 'execute')
      and not has_function_privilege('anon', 'public.origem_dos_conceitos(uuid, text[])', 'execute'),
  'só a service_role (via turma-api e gerar-atividade)');

select * from finish();
rollback;
