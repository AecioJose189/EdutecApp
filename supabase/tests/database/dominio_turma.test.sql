-- Domínio estimado por turma: o mesmo aluno e conceito em turmas diferentes são independentes,
-- e a view dominio_turma agrupa por turma e conceito.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(7);

-- Turmas agora têm professor (perfil criado pelo gatilho do cadastro).
insert into auth.users (id, email) values ('00000000-0000-4000-8000-00000000f001', 'prof-teste@exemplo.com');
insert into turmas (id, nome, codigo, professor_id) values
  ('00000000-0000-4000-8000-0000000000a1', 'Teste', 'A', '00000000-0000-4000-8000-00000000f001'),
  ('00000000-0000-4000-8000-0000000000b2', 'Teste', 'B', '00000000-0000-4000-8000-00000000f001');

-- Turma A: sim-x acerta duas vezes (0,9192 → consolidado); sim-y erra uma (0,1458 → revisar).
select registrar_resposta('sim-x', 'Conceito T', true, '00000000-0000-4000-8000-0000000000a1');
select registrar_resposta('sim-x', 'Conceito T', true, '00000000-0000-4000-8000-0000000000a1');
select registrar_resposta('sim-y', 'Conceito T', false, '00000000-0000-4000-8000-0000000000a1');
-- Turma B: o mesmo pseudônimo e o mesmo conceito, com um erro.
select registrar_resposta('sim-x', 'Conceito T', false, '00000000-0000-4000-8000-0000000000b2');

select is((select n_respostas from dominio where aluno_pseudonimo = 'sim-x'
            and turma_id = '00000000-0000-4000-8000-0000000000a1'), 2, 'turma A conta só as respostas dela');
select ok(abs((select p_dominio from dominio where aluno_pseudonimo = 'sim-x'
            and turma_id = '00000000-0000-4000-8000-0000000000b2') - 43.0/295) < 1e-9,
  'turma B começa do L0, sem herdar a turma A');
select is((select tentativa from respostas where turma_id = '00000000-0000-4000-8000-0000000000b2'), 1,
  'tentativa numerada dentro da turma');

select results_eq(
  $$ select n_alunos::int, n_respostas, n_revisar, estado_moda from dominio_turma
     where turma_id = '00000000-0000-4000-8000-0000000000a1' and conceito = 'Conceito T' $$,
  $$ values (2, 3, 1, 'revisar') $$,
  'view por turma: alunos, respostas, nº em revisar e moda (empate → revisar)');
select ok(abs((select media_dominio from dominio_turma where turma_id = '00000000-0000-4000-8000-0000000000a1')
  - round((239.0/260 + 43.0/295) / 2, 4)) < 1e-9, 'média da turma A');
select results_eq(
  $$ select n_alunos, n_consolidado, n_desenvolvimento, n_revisar from resumo_turmas
     where turma_id = '00000000-0000-4000-8000-0000000000b2' $$,
  $$ values (1, 0, 0, 1) $$, 'resumo da turma B');

-- Sem turma continua igual ao comportamento antigo (dados demo-).
select registrar_resposta('demo-z', 'Conceito sem turma', true);
select is((select count(*)::int from dominio_turma where conceito = 'Conceito sem turma' and turma_id is null), 1,
  'registrar_resposta sem turma grava com turma nula');

select * from finish();
rollback;
