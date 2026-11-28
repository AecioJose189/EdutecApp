-- Revisão espaçada com flashcards (fase 4): baralho só com sessões encerradas, prioridade de "revisar",
-- Leitner (sobe, volta, dois treinos no mesmo dia), BKT e domínio intactos, isolamento entre alunos,
-- nada de gabarito no baralho, resumos do aluno e do professor.
-- Rodar: npx supabase test db supabase/tests/database   (local)  ou  --linked
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(49);

create function pg_temp.como(usuario uuid, consulta text) returns jsonb
language plpgsql as $$
declare
  r jsonb;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', usuario, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  execute format('select coalesce(jsonb_agg(x), ''[]'') from (%s) x', consulta) into r;
  execute 'set local role postgres';
  return r;
end;
$$;

-- Prof; Ana e Bia na turma; Caio fora dela.
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000fa', 'prof@exemplo.com', '{"full_name": "Prof"}'),
  ('00000000-0000-4000-8000-000000000f01', 'ana@exemplo.com', '{"full_name": "Ana"}'),
  ('00000000-0000-4000-8000-000000000f02', 'bia@exemplo.com', '{"full_name": "Bia"}'),
  ('00000000-0000-4000-8000-000000000f03', 'caio@exemplo.com', '{"full_name": "Caio"}');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000cf01', 'IA', 'A', '00000000-0000-4000-8000-0000000000fa');
insert into membros_turma (turma_id, aluno_id) values
  ('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f01'),
  ('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f02');

-- Atividade 1: Q1 Base (certa a), Q2 Meio (certa b), Q3 Topo (a), Q4 aberta Topo, Q5 Topo (a).
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-90, repeat('f', 64), 5, 'mista', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Meio", "prerequisitos": ["Base"]}, {"nome": "Topo", "prerequisitos": ["Meio"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"],
     "alternativas": [{"texto": "um", "correta": true, "feedback": "fa"}, {"texto": "dois", "correta": false, "feedback": "fb"},
                      {"texto": "tres", "correta": false, "feedback": "fc"}, {"texto": "quatro", "correta": false, "feedback": "fd"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Meio"],
     "alternativas": [{"texto": "um", "correta": false, "feedback": "f"}, {"texto": "dois", "correta": true, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q3", "conceitos": ["Topo"],
     "alternativas": [{"texto": "um", "correta": true, "feedback": "f"}, {"texto": "dois", "correta": false, "feedback": "f"}]},
    {"tipo": "aberta", "enunciado": "Q4", "conceitos": ["Topo"], "alternativas": null, "rubrica": "r"},
    {"tipo": "multipla_escolha", "enunciado": "Q5", "conceitos": ["Topo"],
     "alternativas": [{"texto": "um", "correta": true, "feedback": "f"}, {"texto": "dois", "correta": false, "feedback": "f"}]}]}');
-- Atividade 2 (sessão continua aberta): Q1 Base.
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-91, repeat('9', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q6", "conceitos": ["Base"],
     "alternativas": [{"texto": "um", "correta": true, "feedback": "f"}, {"texto": "dois", "correta": false, "feedback": "f"}]}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cf01', -90, true, null);
select publicar_atividade('00000000-0000-4000-8000-00000000cf01', -91, true, null);
insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005f01', id from atividades where geracao_id = -90;
insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005f02', id from atividades where geracao_id = -91;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000f701', '00000000-0000-4000-8000-000000005f01', '00000000-0000-4000-8000-000000000f01', 'Ana');

-- Ana: erra Q1 (Base → revisar), acerta Q2 (Meio → em desenvolvimento), acerta Q3 e Q5 (Topo → consolidado).
select enviar_tentativa('00000000-0000-4000-8000-00000000f701', '00000000-0000-4000-8000-000000000f01', '[
  {"questao_id": "1", "alternativa_id": "b"}, {"questao_id": "2", "alternativa_id": "b"},
  {"questao_id": "3", "alternativa_id": "a"}, {"questao_id": "4", "texto": "t"}, {"questao_id": "5", "alternativa_id": "a"}]');

-- Só os treinos desta turma: o banco (local ou de produção) pode ter outros.
create temp view treinos_da_turma as select * from treinos where turma_id = '00000000-0000-4000-8000-00000000cf01';

create temp table ids as select
  (select id from atividades where geracao_id = -90) as a1,
  (select id from atividades where geracao_id = -91) as a2;
create temp table c as select
  privado.cartao_id('00000000-0000-4000-8000-000000000f01', (select a1 from ids), '1') as ana_q1,
  privado.cartao_id('00000000-0000-4000-8000-000000000f01', (select a1 from ids), '2') as ana_q2,
  privado.cartao_id('00000000-0000-4000-8000-000000000f01', (select a1 from ids), '3') as ana_q3;

select is((select array_agg(conceito || ':' || estado_dominio(p_dominio) order by conceito) from dominio
            where aluno_id = '00000000-0000-4000-8000-000000000f01'),
  array['Base:revisar', 'Meio:em_desenvolvimento', 'Topo:consolidado'], 'cenário: domínio estimado da Ana');

-- ---------------------------------------------------------------------------
-- Só sessões encerradas
-- ---------------------------------------------------------------------------

select is(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-01'),
  '{"cartoes": [], "pendentes_hoje": 0, "total_cartoes": 0}'::jsonb, 'sessão aberta: baralho vazio');
select throws_ok(format($$ select revisao_responder('00000000-0000-4000-8000-000000000f01', %L, 'a', null, '2026-12-01') $$,
                        (select ana_q1 from c)),
  'LU403', null, 'sessão aberta: não dá para responder o cartão');

update sessoes set aberta = false, entrada_aberta = false, encerrada_em = now() where id = '00000000-0000-4000-8000-000000005f01';

create temp table b1 as select revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-01') as j;
select is((select jsonb_agg(x -> 'enunciado') from b1, jsonb_array_elements(j -> 'cartoes') x),
  '["Q1", "Q2"]'::jsonb, 'encerrada: novos de "revisar" (Base) antes de "em desenvolvimento" (Meio); consolidado e aberta fora');
select is((select j -> 'pendentes_hoje' from b1), '2'::jsonb, 'pendentes_hoje = 2');
select is((select j -> 'total_cartoes' from b1), '0'::jsonb, 'total_cartoes = 0 antes de responder');
select ok((select j::text !~ '"(correta|feedback|rubrica)"' from b1), 'baralho sem correta, feedback nem rubrica');
select is((select j -> 'cartoes' -> 0 -> 'cartao_id' from b1), to_jsonb((select ana_q1 from c)), 'cartao_id determinístico');
select is((select (j -> 'cartoes' -> 0) - 'cartao_id' - 'alternativas' - 'turma' - 'atividade' from b1),
  '{"questao_id": "1", "enunciado": "Q1", "conceitos": ["Base"], "caixa": 1, "novo": true}'::jsonb, 'campos do cartão');
select is((select j -> 'cartoes' -> 0 -> 'atividade' from b1),
  jsonb_build_object('id', (select a1 from ids), 'numero', numero_da_atividade((select a1 from ids)), 'titulo', (select titulo from atividades where geracao_id = -90)),
  'atividade com número e título');
select is((select jsonb_agg(x ->> 'id' order by x ->> 'id') from b1, jsonb_array_elements(j -> 'cartoes' -> 0 -> 'alternativas') x),
  '["a", "b", "c", "d"]'::jsonb, 'as 4 alternativas, com os ids originais');
select is((select j -> 'cartoes' -> 0 -> 'alternativas' from b1),
  (select revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-01') -> 'cartoes' -> 0 -> 'alternativas'),
  'ordem das alternativas fixa entre chamadas');
select is((select jsonb_agg(x -> 'id') from b1, jsonb_array_elements(j -> 'cartoes' -> 0 -> 'alternativas') x),
  (select jsonb_agg(alt -> 'id' order by chave_alternativa((select ana_q1 from c), '1', alt ->> 'id'))
     from jsonb_array_elements('[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]'::jsonb) alt),
  'ordem = chave_alternativa com o id do cartão');
select is(jsonb_array_length(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 1, '2026-12-01') -> 'cartoes'),
  1, 'limite respeitado');
select is(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 1, '2026-12-01') -> 'pendentes_hoje',
  '2'::jsonb, 'pendentes_hoje não depende do limite');

-- Reabrir a atividade em sala esconde os cartões dela.
insert into sessoes (id, atividade_id) values ('00000000-0000-4000-8000-000000005f03', (select a1 from ids));
select is(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-01') -> 'pendentes_hoje',
  '0'::jsonb, 'atividade reaberta em sala: cartões somem');
update sessoes set aberta = false, entrada_aberta = false, encerrada_em = now() where id = '00000000-0000-4000-8000-000000005f03';

-- ---------------------------------------------------------------------------
-- Leitner
-- ---------------------------------------------------------------------------

create temp table bkt_antes as
  select (select jsonb_agg(to_jsonb(d) order by d.conceito, d.aluno_pseudonimo) from dominio d) as dominio,
         (select count(*) from respostas) as n_respostas;

create temp table r1 as select revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'A', 'Certeza', '2026-12-01') as j;
select is((select j - 'alternativas' from r1),
  '{"acertou": true, "alternativa_correta": "a", "nova_caixa": 2, "proxima_revisao": "2026-12-04", "contou_para_caixa": true}'::jsonb,
  'acerto: caixa 1 → 2, volta em 3 dias');
select is((select jsonb_agg(jsonb_build_array(x ->> 'id', x -> 'correta', x ->> 'feedback') order by x ->> 'id')
             from r1, jsonb_array_elements(j -> 'alternativas') x),
  '[["a", true, "fa"], ["b", false, "fb"], ["c", false, "fc"], ["d", false, "fd"]]'::jsonb, 'resposta traz o feedback de cada alternativa');
select is((select jsonb_agg(x -> 'id') from r1, jsonb_array_elements(j -> 'alternativas') x),
  (select jsonb_agg(x -> 'id') from b1, jsonb_array_elements(j -> 'cartoes' -> 0 -> 'alternativas') x),
  'alternativas na mesma ordem do baralho');

create temp table r2 as select revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'b', null, '2026-12-01') as j;
select is((select j - 'alternativas' from r2),
  '{"acertou": false, "alternativa_correta": "a", "nova_caixa": 2, "proxima_revisao": "2026-12-04", "contou_para_caixa": false}'::jsonb,
  'mesmo dia de novo: não mexe na caixa');
select is((select array_agg(acertou::text || ':' || contou_para_caixa::text || ':' || coalesce(confianca, '-') order by id)
             from treinos where cartao_id = (select ana_q1 from c)),
  array['true:true:certeza', 'false:false:-'], 'os dois treinos ficam registrados');
select is((select row(caixa, acertos, erros, proxima_revisao, ultima_revisao)::text from cartoes where id = (select ana_q1 from c)),
  '(2,1,0,2026-12-04,2026-12-01)', 'cartão: só a primeira conta');

create temp table r3 as select revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q2 from c), 'a', 'chute', '2026-12-01') as j;
select is((select j - 'alternativas' from r3),
  '{"acertou": false, "alternativa_correta": "b", "nova_caixa": 1, "proxima_revisao": "2026-12-02", "contou_para_caixa": true}'::jsonb,
  'erro em cartão novo: fica na caixa 1, volta amanhã');

select is(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-01'),
  '{"cartoes": [], "pendentes_hoje": 0, "total_cartoes": 2}'::jsonb, 'depois de treinar: nada pendente hoje');
select is((select jsonb_agg(jsonb_build_array(x ->> 'enunciado', x -> 'caixa', x -> 'novo'))
             from jsonb_array_elements(revisao_baralho('00000000-0000-4000-8000-000000000f01', null, 10, '2026-12-04') -> 'cartoes') x),
  '[["Q2", 1, false], ["Q1", 2, false]]'::jsonb, 'dia 4: vencidos, o mais atrasado primeiro');

select is(revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'c', null, '2026-12-02') -> 'contou_para_caixa',
  'false'::jsonb, 'cartão ainda não vencido: registra, não conta');
select is(revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'a', null, '2026-12-04') - 'alternativas',
  '{"acertou": true, "alternativa_correta": "a", "nova_caixa": 3, "proxima_revisao": "2026-12-11", "contou_para_caixa": true}'::jsonb,
  'caixa 2 → 3, volta em 7 dias');
select is(revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'a', null, '2026-12-11') -> 'nova_caixa',
  '3'::jsonb, 'caixa 3 é o máximo');
select is(revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q1 from c), 'd', null, '2026-12-18') - 'alternativas',
  '{"acertou": false, "alternativa_correta": "a", "nova_caixa": 1, "proxima_revisao": "2026-12-19", "contou_para_caixa": true}'::jsonb,
  'erro na caixa 3: volta para a 1');

select throws_ok(format($$ select revisao_responder('00000000-0000-4000-8000-000000000f01', %L, 'e', null, '2026-12-19') $$,
                        (select ana_q2 from c)), 'LU400', null, 'alternativa que não existe: LU400');
select is(revisao_responder('00000000-0000-4000-8000-000000000f01', (select ana_q3 from c), 'a', null, '2026-12-19') -> 'contou_para_caixa',
  'true'::jsonb, 'cartão novo fora do baralho (conceito consolidado) também pode ser respondido');

-- ---------------------------------------------------------------------------
-- BKT e domínio intactos; treinos só inserção
-- ---------------------------------------------------------------------------

select is((select jsonb_agg(to_jsonb(d) order by d.conceito, d.aluno_pseudonimo) from dominio d), (select dominio from bkt_antes),
  'dominio igual depois dos treinos');
select is((select count(*) from respostas), (select n_respostas from bkt_antes), 'nenhuma linha nova em respostas');
select throws_ok($$ update treinos set acertou = true $$, '42501', null, 'treinos: UPDATE barrado');
select throws_ok($$ delete from treinos $$, '42501', null, 'treinos: DELETE barrado');

-- ---------------------------------------------------------------------------
-- Isolamento entre alunos
-- ---------------------------------------------------------------------------

select throws_ok(format($$ select revisao_responder('00000000-0000-4000-8000-000000000f02', %L, 'a', null, '2026-12-19') $$,
                        (select ana_q1 from c)), 'LU403', null, 'Bia não responde o cartão da Ana');
select throws_ok(format($$ select revisao_responder('00000000-0000-4000-8000-000000000f03', %L, 'a', null, '2026-12-19') $$,
                        privado.cartao_id('00000000-0000-4000-8000-000000000f03', (select a1 from ids), '1')),
  'LU403', null, 'Caio (fora da turma) não tem cartões');
select is(revisao_baralho('00000000-0000-4000-8000-000000000f02', null, 10, '2026-12-01'),
  '{"cartoes": [], "pendentes_hoje": 0, "total_cartoes": 0}'::jsonb, 'Bia sem domínio estimado: sem cartões novos');
select is(pg_temp.como('00000000-0000-4000-8000-000000000f02', 'select id from cartoes union all select cartao_id from treinos'),
  '[]'::jsonb, 'RLS: Bia não vê cartões nem treinos da Ana');
select is(jsonb_array_length(pg_temp.como('00000000-0000-4000-8000-000000000f01', 'select id from cartoes')),
  3, 'RLS: Ana vê os próprios cartões');
select ok(not has_function_privilege('authenticated', 'public.revisao_baralho(uuid, uuid, int, date)', 'execute')
      and not has_function_privilege('authenticated', 'public.revisao_responder(uuid, uuid, text, text, date)', 'execute')
      and not has_function_privilege('anon', 'public.treino_turma(uuid, date)', 'execute')
      and not has_table_privilege('authenticated', 'public.cartoes', 'insert')
      and not has_table_privilege('authenticated', 'public.treinos', 'insert'),
  'nada exposto: só a service_role chama as funções e escreve');

-- ---------------------------------------------------------------------------
-- Resumos
-- ---------------------------------------------------------------------------

create temp table res as select revisao_resumo('00000000-0000-4000-8000-000000000f01', null, '2026-12-19') -> 'turmas' -> 0 as j;
select is((select (j -> 'cartoes_por_caixa') || jsonb_build_object('total', j -> 'total_cartoes', 'pendentes', j -> 'pendentes_hoje') from res),
  '{"1": 2, "2": 1, "3": 0, "total": 3, "pendentes": 2}'::jsonb, 'resumo: caixas, total e pendentes (Q1 e Q2 vencidas em 19/12)');
select is((select j -> 'conceitos_com_mais_cartoes' from res),
  '[{"conceito": "Base", "n_cartoes": 1}, {"conceito": "Meio", "n_cartoes": 1}, {"conceito": "Topo", "n_cartoes": 1}]'::jsonb,
  'resumo: conceitos com mais cartões');
select is(revisao_resumo('00000000-0000-4000-8000-000000000f01') -> 'turmas' -> 0 -> 'treinos_7_dias', (select to_jsonb(count(*)) from treinos_da_turma),
  'resumo (hoje de verdade): treinos nos últimos 7 dias');
select is((select j -> 'treinos_7_dias' from res), '0'::jsonb, 'resumo: treinos de mais de 7 dias antes de "hoje" não contam');

create temp table tt as select treino_turma('00000000-0000-4000-8000-00000000cf01') as j;
select is((select j -> 'resumo' from tt),
  jsonb_build_object('n_alunos', 2, 'n_alunos_treinando', 1, 'treinos_7_dias', (select count(*) from treinos_da_turma),
    'n_treinos', (select count(*) from treinos_da_turma),
    'taxa_acerto', (select round(avg(acertou::int), 4) from treinos_da_turma)), 'professor: resumo do treino');
select is((select jsonb_agg(jsonb_build_array(x ->> 'nome', x -> 'n_treinos', x -> 'cartoes_por_caixa') order by x ->> 'nome')
             from tt, jsonb_array_elements(j -> 'alunos') x),
  jsonb_build_array(jsonb_build_array('Ana', (select count(*) from treinos_da_turma), '{"1": 2, "2": 1, "3": 0}'::jsonb),
                    jsonb_build_array('Bia', 0, '{"1": 0, "2": 0, "3": 0}'::jsonb)), 'professor: por aluno (Bia com zeros)');
select is((select j -> 'conceitos' from tt),
  jsonb_build_array(
    jsonb_build_object('conceito', 'Base', 'n_alunos_treinando', 1, 'treinos_7_dias', (select count(*) from treinos_da_turma where 'Base' = any (conceitos))),
    jsonb_build_object('conceito', 'Meio', 'n_alunos_treinando', 1, 'treinos_7_dias', 1),
    jsonb_build_object('conceito', 'Topo', 'n_alunos_treinando', 1, 'treinos_7_dias', 1)),
  'professor: alunos treinando por conceito');
select is(treino_aluno_conceito('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f01', 'Base'),
  jsonb_build_object('n_treinos', (select count(*) from treinos_da_turma where 'Base' = any (conceitos)),
                     'acertos', (select count(*) from treinos_da_turma where 'Base' = any (conceitos) and acertou)),
  'histórico do conceito: treinou N vezes (X acertos)');

select * from finish();
rollback;
