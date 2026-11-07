-- Conceitos sem duplicata: normalização, publicação ligando ao conceito existente, possíveis
-- duplicatas, fusão (UC05) com recálculo do BKT e recusa de ciclo, e o gatilho só-inserção.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(41);

-- Usuários: o professor das turmas e outro professor.
insert into auth.users (id, email) values
  ('00000000-0000-4000-8000-00000000c001', 'prof-conceitos@exemplo.com'),
  ('00000000-0000-4000-8000-00000000c002', 'outro-prof@exemplo.com');
insert into turmas (id, nome, codigo, professor_id) values
  ('00000000-0000-4000-8000-0000000000c1', 'Probabilidade', 'T1', '00000000-0000-4000-8000-00000000c001'),
  ('00000000-0000-4000-8000-0000000000c2', 'Fusão', 'T2', '00000000-0000-4000-8000-00000000c001');

-- Chama como o usuário [u] (auth.uid() lê o sub dos claims).
create function pg_temp.como(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
$$;

-- ===========================================================================
-- 1. Normalização
-- ===========================================================================

select is(normalizar_conceito('  Probabilidade   Clássica '), 'probabilidade classica',
  'minúsculas, sem acento, espaços colapsados e sem espaço nas pontas');
select is(normalizar_conceito('Evento complementar'), normalizar_conceito('Evento Complementar'),
  'maiúsculas não distinguem conceitos');
select is(normalizar_conceito(E'Ação\tÇÃO  Ü'), 'acao cao u', 'cedilha, til, trema e tabulação');

-- ===========================================================================
-- 2. Publicação sem duplicar
-- ===========================================================================

-- Geração 1 com uma grafia; geração 2, de outro PDF, com outra grafia dos mesmos conceitos e um novo.
-- Geração 3 traz duas grafias do mesmo conceito dentro dela (uma pré-requisito da outra).
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values
  (-11, repeat('1', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [
      {"nome": "Probabilidade Clássica", "prerequisitos": []},
      {"nome": "Evento Complementar", "prerequisitos": ["Probabilidade Clássica"]}],
    "questoes": [{"tipo": "multipla_escolha", "enunciado": "?", "conceitos": ["Evento Complementar"],
      "alternativas": [{"texto": "x", "correta": true}, {"texto": "y", "correta": false}]}]}'),
  (-12, repeat('2', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [
      {"nome": "Probabilidade clássica", "prerequisitos": []},
      {"nome": "Evento  complementar", "prerequisitos": ["Probabilidade clássica"]},
      {"nome": "Regra da adição", "prerequisitos": ["Probabilidade clássica"]},
      {"nome": "Clássica", "prerequisitos": []}],
    "questoes": [{"tipo": "multipla_escolha", "enunciado": "?",
      "conceitos": ["Evento  complementar", "Probabilidade clássica", "Probabilidade Clássica"],
      "alternativas": [{"texto": "x", "correta": true}, {"texto": "y", "correta": false}]}]}'),
  (-13, repeat('3', 64), 1, 'aberta', 'media', '{"conceitos": [
      {"nome": "Fila", "prerequisitos": []},
      {"nome": "FILA", "prerequisitos": ["Fila"]},
      {"nome": "Fila Circular", "prerequisitos": ["FILA"]}],
    "questoes": [{"tipo": "aberta", "enunciado": "?", "conceitos": ["FILA"]}]}');

select lives_ok($$ select publicar_atividade('00000000-0000-4000-8000-0000000000c1', -11, true, null) $$,
  'publica a primeira geração');
select lives_ok($$ select publicar_atividade('00000000-0000-4000-8000-0000000000c1', -12, true, null) $$,
  'publica a segunda, com outra grafia dos mesmos conceitos');
select is(
  (select array_agg(nome order by nome) from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c1'),
  array['Clássica', 'Evento Complementar', 'Probabilidade Clássica', 'Regra da adição'],
  'a outra grafia é ligada ao conceito existente: só o novo e "Clássica" entram');
select is(
  (select prerequisitos from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c1' and nome = 'Regra da adição'),
  array['Probabilidade Clássica'], 'pré-requisito reescrito com o nome que a turma já usa');
select is(
  (select questoes -> 0 -> 'conceitos' from atividades where geracao_id = -12),
  '["Evento Complementar", "Probabilidade Clássica"]'::jsonb,
  'questão gravada com os nomes da turma, sem repetir');
select is(
  (select questoes_aluno -> 0 -> 'conceitos' from atividades where geracao_id = -12),
  '["Evento Complementar", "Probabilidade Clássica"]'::jsonb, 'e o aluno vê os mesmos nomes');
select is((select titulo from atividades where geracao_id = -12), 'Probabilidade Clássica, Evento Complementar e outros',
  'título padrão com os nomes da turma');
select throws_ok(
  $$ insert into conceitos_turma (turma_id, nome) values ('00000000-0000-4000-8000-0000000000c1', 'evento COMPLEMENTAR') $$,
  '23505', null, 'índice único por nome normalizado barra a duplicata direta');

select lives_ok($$ select publicar_atividade('00000000-0000-4000-8000-0000000000c1', -13, true, null) $$,
  'publica uma geração com duas grafias do mesmo conceito');
select is(
  (select array_agg(nome || ':' || array_to_string(prerequisitos, ',') order by nome)
     from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c1' and nome like 'Fila%'),
  array['Fila:', 'Fila Circular:Fila'], 'dentro da geração vira um conceito só, sem laço');

-- Possíveis duplicatas: parecidos mas não iguais. Nada foi fundido sozinho.
select pg_temp.como('00000000-0000-4000-8000-00000000c001');
select is(
  (select array_agg(conceito_a || ' × ' || conceito_b order by conceito_a, conceito_b)
     from possiveis_duplicatas('00000000-0000-4000-8000-0000000000c1')),
  array['Clássica × Probabilidade Clássica', 'Fila × Fila Circular'],
  'nome contido no outro aparece como possível duplicata');
select lives_ok(
  $$ select marcar_conceitos_distintos('00000000-0000-4000-8000-0000000000c1', 'Fila Circular', 'Fila') $$,
  'o professor marca um par como distinto (em qualquer ordem)');
select is(
  (select array_agg(conceito_a || ' × ' || conceito_b) from possiveis_duplicatas('00000000-0000-4000-8000-0000000000c1')),
  array['Clássica × Probabilidade Clássica'], 'o par marcado sai da lista');
select pg_temp.como('00000000-0000-4000-8000-00000000c002');
select throws_ok(
  $$ select marcar_conceitos_distintos('00000000-0000-4000-8000-0000000000c1', 'Clássica', 'Probabilidade Clássica') $$,
  'ED404', null, 'outro professor não marca na turma alheia');

-- ===========================================================================
-- 3. Fusão
-- ===========================================================================

-- Turma 2: Base ← Origem ← Usa; Base, P ← Destino; Origem ← Dep (Dep depende da origem).
-- Para o ciclo: K ← M (M requer K)... ver adiante.
insert into conceitos_turma (turma_id, nome, prerequisitos) values
  ('00000000-0000-4000-8000-0000000000c2', 'Base', '{}'),
  ('00000000-0000-4000-8000-0000000000c2', 'P', '{}'),
  ('00000000-0000-4000-8000-0000000000c2', 'Origem', '{Base}'),
  ('00000000-0000-4000-8000-0000000000c2', 'Destino', '{Base,P}'),
  ('00000000-0000-4000-8000-0000000000c2', 'Usa', '{Origem,Destino}'),
  ('00000000-0000-4000-8000-0000000000c2', 'Final', '{}'),
  -- Ciclo ao fundir L em K: K requer M, M requer L → K requer M requer K.
  ('00000000-0000-4000-8000-0000000000c2', 'L', '{}'),
  ('00000000-0000-4000-8000-0000000000c2', 'M', '{L}'),
  ('00000000-0000-4000-8000-0000000000c2', 'K', '{M}'),
  -- Laço: fundir Pre em Pos, sendo Pre pré-requisito de Pos.
  ('00000000-0000-4000-8000-0000000000c2', 'Pre', '{}'),
  ('00000000-0000-4000-8000-0000000000c2', 'Pos', '{Pre}');

insert into atividades (id, turma_id, geracao_id, questoes) values
  ('00000000-0000-4000-8000-0000000000a2', '00000000-0000-4000-8000-0000000000c2', -11,
   '[{"tipo": "multipla_escolha", "enunciado": "?", "conceitos": ["Origem", "Destino"],
      "alternativas": [{"texto": "x", "correta": true}]},
     {"tipo": "aberta", "enunciado": "?", "conceitos": ["Origem"]},
     {"tipo": "aberta", "enunciado": "?", "conceitos": ["Base"]}]');

-- Respostas de um aluno, intercaladas no tempo, inseridas fora de ordem (ids não seguem o tempo):
-- em ordem de tempo: Origem ✓ (t1), Destino ✗ (t2), Origem ✗ (t3), Destino ✓ (t4).
insert into respostas (turma_id, aluno_pseudonimo, conceito, correta, tentativa, criado_em) values
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Destino', false, 1, '2026-11-01 10:02'),
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Destino', true,  2, '2026-11-01 10:04'),
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Origem',  true,  1, '2026-11-01 10:01'),
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Origem',  false, 2, '2026-11-01 10:03'),
  -- Outro aluno só na origem.
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-02', 'Origem',  true,  1, '2026-11-01 10:05');
insert into dominio (turma_id, aluno_pseudonimo, conceito, p_dominio, n_respostas) values
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Origem', 0.5, 2),
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-01', 'Destino', 0.5, 2),
  ('00000000-0000-4000-8000-0000000000c2', 'sim-c2-02', 'Origem', 0.5, 1);

-- Gatilho só-inserção: sem a chave, UPDATE continua barrado; com a chave à mão, também.
select throws_ok(
  $$ update respostas set conceito = 'Destino' where aluno_pseudonimo = 'sim-c2-02' $$,
  '42501', null, 'UPDATE em respostas sem a chave continua barrado');
select set_config('edutec.fundir_conceitos', 'on', true);
select throws_ok(
  $$ update respostas set correta = not correta where aluno_pseudonimo = 'sim-c2-02' $$,
  '42501', 'Fusão de conceitos só pode mudar o conceito da resposta.',
  'com a chave, mudar outra coluna além de conceito é barrado');
select throws_ok(
  $$ update respostas set conceito = 'Destino', conceito_original = 'Origem', tentativa = 9 where aluno_pseudonimo = 'sim-c2-02' $$,
  '42501', 'Fusão de conceitos só pode mudar o conceito da resposta.',
  'com a chave, mudar conceito junto com outra coluna também é barrado');
select throws_ok(
  $$ update respostas set conceito = 'Inexistente', conceito_original = 'Origem' where aluno_pseudonimo = 'sim-c2-02' $$,
  '42501', 'O conceito "Inexistente" não existe na turma da resposta.',
  'com a chave, o novo conceito precisa existir na turma da resposta');
select throws_ok(
  $$ update respostas set conceito = 'Destino', conceito_original = 'Origem' where aluno_pseudonimo = 'sim-c2-02' $$,
  '42501', 'Mudança de conceito sem fusão registrada nesta transação.',
  'com a chave ligada à mão, sem fusão registrada, é barrado');
select set_config('edutec.fundir_conceitos', 'off', true);

-- Quem pode fundir.
select pg_temp.como('00000000-0000-4000-8000-00000000c002');
select throws_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Origem', 'Destino') $$,
  'ED404', null, 'só o dono da turma funde');
select pg_temp.como('00000000-0000-4000-8000-00000000c001');
select throws_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Origem', 'Origem') $$,
  'ED400', null, 'origem e destino iguais são recusados');
select throws_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Nada', 'Destino') $$,
  'ED404', null, 'conceito inexistente é recusado');

select is(
  fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Origem', 'Destino'),
  '{"origem": "Origem", "destino": "Destino", "n_respostas": 3, "n_alunos": 2}'::jsonb,
  'o dono funde Origem em Destino');
select is((select count(*)::int from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c2' and nome = 'Origem'), 0,
  'a origem sai do mapa');
select is((select prerequisitos from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c2' and nome = 'Destino'),
  array['Base', 'P'], 'o destino fica com os pré-requisitos dos dois, sem repetir');
select is((select prerequisitos from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c2' and nome = 'Usa'),
  array['Destino'], 'quem dependia da origem passa a depender do destino');
select is(
  (select jsonb_agg(q -> 'conceitos') from atividades a, jsonb_array_elements(a.questoes) q
    where a.id = '00000000-0000-4000-8000-0000000000a2'),
  '[["Destino"], ["Destino"], ["Base"]]'::jsonb, 'as questões são religadas ao destino, sem repetir');
select is(
  (select questoes_aluno -> 1 -> 'conceitos' from atividades where id = '00000000-0000-4000-8000-0000000000a2'),
  '["Destino"]'::jsonb, 'e as questões do aluno acompanham');
select is(
  (select array_agg(coalesce(conceito_original, '-') order by criado_em) from respostas
    where turma_id = '00000000-0000-4000-8000-0000000000c2' and conceito = 'Destino'),
  array['Origem', '-', 'Origem', '-', 'Origem'], 'respostas movidas guardam o nome original');

-- BKT refeito em ordem de tempo: ✓ ✗ ✗ ✓ (em ordem de id seria ✗ ✓ ✓ ✗, outro resultado).
select is(
  (select p_dominio from dominio where turma_id = '00000000-0000-4000-8000-0000000000c2'
      and aluno_pseudonimo = 'sim-c2-01' and conceito = 'Destino'),
  bkt_passo(bkt_passo(bkt_passo(bkt_passo(0.3, true, 0.1, 0.2, 0.1), false, 0.1, 0.2, 0.1),
            false, 0.1, 0.2, 0.1), true, 0.1, 0.2, 0.1),
  'domínio do destino refeito pelo BKT com as respostas em ordem de tempo');
select is(
  (select array_agg(aluno_pseudonimo || ':' || conceito || ':' || n_respostas order by aluno_pseudonimo)
     from dominio where turma_id = '00000000-0000-4000-8000-0000000000c2'),
  array['sim-c2-01:Destino:4', 'sim-c2-02:Destino:1'], 'sem domínio da origem; contagens refeitas');
select is(
  (select row(origem, destino, n_respostas, fundido_por, automatica)::text from conceitos_fusoes
    where turma_id = '00000000-0000-4000-8000-0000000000c2'),
  row('Origem', 'Destino', 3, '00000000-0000-4000-8000-00000000c001'::uuid, false)::text,
  'a fusão fica registrada com quem fez e quantas respostas moveu');

-- Segunda fusão: conceito_original guarda o nome de verdade, não o intermediário.
select lives_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Destino', 'Final') $$,
  'funde de novo (Destino em Final)');
select is(
  (select array_agg(conceito_original order by criado_em) from respostas
    where turma_id = '00000000-0000-4000-8000-0000000000c2' and conceito = 'Final'),
  array['Origem', 'Destino', 'Origem', 'Destino', 'Origem'], 'conceito_original não é sobrescrito');

-- Laço e ciclo.
select lives_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'Pre', 'Pos') $$,
  'funde um pré-requisito no conceito que depende dele');
select is((select prerequisitos from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c2' and nome = 'Pos'),
  '{}'::text[], 'sem laço (o conceito não vira pré-requisito de si mesmo)');
select throws_ok($$ select fundir_conceitos('00000000-0000-4000-8000-0000000000c2', 'L', 'K') $$,
  'ED003', null, 'fusão que formaria ciclo é recusada');
select is((select count(*)::int from conceitos_turma where turma_id = '00000000-0000-4000-8000-0000000000c2' and nome in ('L', 'K', 'M')), 3,
  'e nada muda');

select * from finish();
rollback;
