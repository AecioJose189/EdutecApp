-- Políticas de RLS do MVP: professor só vê as próprias turmas; aluno só vê turmas de que é membro,
-- atividades sem gabarito e os próprios dados.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(26);

-- Executa [consulta] como o usuário [usuario] (papel authenticated, ou anon se usuario for nulo)
-- e devolve as linhas em jsonb. A troca de papel vale só dentro da chamada, para o pgTAP (cujas
-- tabelas temporárias são do postgres) continuar funcionando.
create function pg_temp.como(usuario uuid, consulta text) returns jsonb
language plpgsql as $$
declare
  r jsonb;
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', usuario, 'role', case when usuario is null then 'anon' else 'authenticated' end)::text, true);
  execute case when usuario is null then 'set local role anon' else 'set local role authenticated' end;
  execute format('select coalesce(jsonb_agg(x), ''[]'') from (%s) x', consulta) into r;
  execute 'set local role postgres';
  return r;
end;
$$;

-- Comando de escrita como [usuario]; devolve o nº de linhas afetadas.
create function pg_temp.como_exec(usuario uuid, comando text) returns int
language plpgsql as $$
declare
  n int;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', usuario, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  execute comando;
  get diagnostics n = row_count;
  execute 'set local role postgres';
  return n;
end;
$$;

-- ---------------------------------------------------------------------------
-- Cenário: professores A e B, cada um com uma turma; alunos 1 e 2 na turma A.
-- ---------------------------------------------------------------------------

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000aa', 'prof-a@exemplo.com', '{"full_name": "Professora A"}'),
  ('00000000-0000-4000-8000-0000000000bb', 'prof-b@exemplo.com', '{"full_name": "Professor B"}'),
  ('00000000-0000-4000-8000-000000000a01', 'aluno1@exemplo.com', '{"full_name": "Aluno Um"}'),
  ('00000000-0000-4000-8000-000000000a02', 'aluno2@exemplo.com', '{"name": "Aluna Dois"}');

insert into turmas (id, nome, codigo, professor_id) values
  ('00000000-0000-4000-8000-00000000ca01', 'IA', 'A', '00000000-0000-4000-8000-0000000000aa'),
  ('00000000-0000-4000-8000-00000000cb01', 'Cálculo', 'B', '00000000-0000-4000-8000-0000000000bb');
insert into membros_turma (turma_id, aluno_id) values
  ('00000000-0000-4000-8000-00000000ca01', '00000000-0000-4000-8000-000000000a01'),
  ('00000000-0000-4000-8000-00000000ca01', '00000000-0000-4000-8000-000000000a02');

insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-20, repeat('e', 64), 2, 'mista', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Topo", "prerequisitos": ["Base"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"], "rubrica": null,
     "alternativas": [{"texto": "x", "correta": false, "feedback": "não"}, {"texto": "y", "correta": true, "feedback": "sim"}]},
    {"tipo": "aberta", "enunciado": "Q2", "conceitos": ["Topo"], "alternativas": null, "rubrica": "explicar Topo"}]}');

select publicar_atividade('00000000-0000-4000-8000-00000000ca01', -20, true, 30);
insert into sessoes (id, atividade_id)
  select '00000000-0000-4000-8000-000000005e01', id from atividades where geracao_id = -20;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000e001', '00000000-0000-4000-8000-000000005e01', '00000000-0000-4000-8000-000000000a01', 'Aluno Um'),
  ('00000000-0000-4000-8000-00000000e002', '00000000-0000-4000-8000-000000005e01', '00000000-0000-4000-8000-000000000a02', 'Aluna Dois');
select enviar_tentativa('00000000-0000-4000-8000-00000000e001', '00000000-0000-4000-8000-000000000a01',
  '[{"questao_id": "1", "alternativa_id": "b"}, {"questao_id": "2", "texto": "resposta"}]');
select enviar_tentativa('00000000-0000-4000-8000-00000000e002', '00000000-0000-4000-8000-000000000a02',
  '[{"questao_id": "1", "alternativa_id": "a"}]');

-- ---------------------------------------------------------------------------
-- Perfis
-- ---------------------------------------------------------------------------

select is((select nome from perfis where id = '00000000-0000-4000-8000-000000000a02'), 'Aluna Dois',
  'gatilho do cadastro cria o perfil com o nome do Google');

-- ---------------------------------------------------------------------------
-- Professor
-- ---------------------------------------------------------------------------

select is(pg_temp.como('00000000-0000-4000-8000-0000000000aa', 'select id from turmas'),
  '[{"id": "00000000-0000-4000-8000-00000000ca01"}]', 'professor A vê só a própria turma');
select is(pg_temp.como('00000000-0000-4000-8000-0000000000bb', $q$
  select (select count(*) from turmas where id = '00000000-0000-4000-8000-00000000ca01') +
         (select count(*) from atividades) + (select count(*) from conceitos_turma) +
         (select count(*) from sessoes) + (select count(*) from tentativas) + (select count(*) from respostas) +
         (select count(*) from dominio) + (select count(*) from membros_turma) + (select count(*) from dominio_turma) as n $q$),
  '[{"n": 0}]', 'professor B não vê nada da turma do professor A');
select is(pg_temp.como_exec('00000000-0000-4000-8000-0000000000bb',
  $q$ update turmas set nome = 'invadida' where id = '00000000-0000-4000-8000-00000000ca01' $q$), 0,
  'professor B não altera a turma do professor A');
select is(pg_temp.como_exec('00000000-0000-4000-8000-0000000000bb',
  $q$ delete from membros_turma where turma_id = '00000000-0000-4000-8000-00000000ca01' $q$), 0,
  'professor B não remove alunos da turma do professor A');
select throws_ok($q$ select pg_temp.como_exec('00000000-0000-4000-8000-0000000000bb',
  'insert into convites (turma_id) values (''00000000-0000-4000-8000-00000000ca01'')') $q$,
  '42501', null, 'professor B não cria convite para a turma do professor A');
select throws_ok($q$ select pg_temp.como_exec('00000000-0000-4000-8000-0000000000bb',
  'insert into turmas (nome, codigo, professor_id) values (''x'', ''y'', ''00000000-0000-4000-8000-0000000000aa'')') $q$,
  '42501', null, 'ninguém cria turma em nome de outro professor');

select is(pg_temp.como_exec('00000000-0000-4000-8000-0000000000aa',
  $q$ insert into convites (turma_id) values ('00000000-0000-4000-8000-00000000ca01') $q$), 1,
  'professor A cria convite para a própria turma');
select ok((select codigo ~ '^[A-HJ-NP-Z2-9]{6}$' from convites where turma_id = '00000000-0000-4000-8000-00000000ca01'),
  'código do convite: 6 caracteres legíveis');
select is(pg_temp.como('00000000-0000-4000-8000-0000000000aa',
  'select count(distinct aluno_id) as alunos from dominio'), '[{"alunos": 2}]',
  'professor A vê o domínio estimado dos dois alunos');
select is(pg_temp.como('00000000-0000-4000-8000-0000000000aa',
  'select count(*) as n from tentativas'), '[{"n": 2}]', 'professor A vê as tentativas da sessão');
select is(pg_temp.como('00000000-0000-4000-8000-0000000000aa',
  'select nome from perfis order by nome'),
  '[{"nome": "Aluna Dois"}, {"nome": "Aluno Um"}, {"nome": "Professora A"}]',
  'professor A vê o próprio perfil e o dos alunos da turma');
select throws_ok($q$ select pg_temp.como('00000000-0000-4000-8000-0000000000aa', 'select segredo from sessoes') $q$,
  '42501', null, 'nem o professor lê o segredo do QR (só o servidor)');

-- ---------------------------------------------------------------------------
-- Aluno
-- ---------------------------------------------------------------------------

select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select nome from turmas'),
  '[{"nome": "IA"}]', 'aluno vê só a turma de que é membro');
select throws_ok($q$ select pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select questoes from atividades') $q$,
  '42501', null, 'aluno não lê as questões com gabarito');
select throws_ok($q$ select pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select * from atividades') $q$,
  '42501', null, 'nem com select *');
select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select questoes_aluno from atividades')
          #>> '{0,questoes_aluno,0,alternativas}',
  '[{"id": "a", "texto": "x"}, {"id": "b", "texto": "y"}]', 'aluno lê as alternativas sem "correta" nem feedback');
select ok(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select questoes_aluno from atividades')::text
          !~ '(correta|feedback|rubrica)', 'nada de gabarito, feedback ou rubrica em questoes_aluno');
select throws_ok($q$ select pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select segredo from sessoes') $q$,
  '42501', null, 'aluno não lê o segredo do QR');
select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', $q$
  select (select count(*) from dominio where aluno_id is distinct from '00000000-0000-4000-8000-000000000a01') +
         (select count(*) from respostas where aluno_id is distinct from '00000000-0000-4000-8000-000000000a01') +
         (select count(*) from tentativas where aluno_id <> '00000000-0000-4000-8000-000000000a01') as n $q$),
  '[{"n": 0}]', 'aluno não vê domínio, respostas nem tentativas de outro aluno');
select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select count(*) as n from dominio'),
  '[{"n": 1}]', 'aluno vê o próprio domínio estimado');
select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select nome from perfis order by nome'),
  '[{"nome": "Aluno Um"}, {"nome": "Professora A"}]', 'aluno vê o próprio perfil e o do professor, não o do colega');
select is(pg_temp.como('00000000-0000-4000-8000-000000000a01', 'select count(*) as n from convites'),
  '[{"n": 0}]', 'aluno não vê os convites');
select throws_ok($q$ select pg_temp.como_exec('00000000-0000-4000-8000-000000000a01',
  'insert into membros_turma (turma_id, aluno_id) values (''00000000-0000-4000-8000-00000000cb01'', ''00000000-0000-4000-8000-000000000a01'')') $q$,
  '42501', null, 'aluno não entra numa turma sem convite');
select throws_ok($$ insert into membros_turma (turma_id, aluno_id)
  values ('00000000-0000-4000-8000-00000000ca01', '00000000-0000-4000-8000-0000000000aa') $$,
  'LU403', null, 'o professor não pode ser aluno da própria turma');

-- ---------------------------------------------------------------------------
-- Sem login
-- ---------------------------------------------------------------------------

select throws_ok($q$ select pg_temp.como(null, 'select id from turmas') $q$,
  '42501', null, 'anon não lê turmas');

select * from finish();
rollback;
