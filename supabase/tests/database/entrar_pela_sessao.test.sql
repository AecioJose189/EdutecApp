-- Entrar na turma pelo QR da sessão (fase 2): matrícula idempotente, só com sessão e entrada abertas,
-- nunca o professor da própria turma, e fora do alcance de anon/authenticated.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(11);

insert into auth.users (id, email) values
  ('00000000-0000-4000-8000-0000000000ba', 'prof@exemplo.com'),
  ('00000000-0000-4000-8000-000000000b01', 'aluno1@exemplo.com'),
  ('00000000-0000-4000-8000-000000000b02', 'aluno2@exemplo.com');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000cb01', 'IA', 'A', '00000000-0000-4000-8000-0000000000ba');

insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-40, repeat('e', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"],
     "alternativas": [{"texto": "1", "correta": true}, {"texto": "2", "correta": false}]}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cb01', -40, true, 10);

insert into sessoes (id, atividade_id)
  select '00000000-0000-4000-8000-000000005b01', id from atividades where geracao_id = -40;

select is(entrar_na_turma_pela_sessao('00000000-0000-4000-8000-000000005b01',
    '00000000-0000-4000-8000-000000000b01'), true, 'quem não é da turma entra pela sessão aberta');
select ok(exists (select 1 from membros_turma where turma_id = '00000000-0000-4000-8000-00000000cb01'
    and aluno_id = '00000000-0000-4000-8000-000000000b01'), 'vira membro da turma da sessão');
select is(entrar_na_turma_pela_sessao('00000000-0000-4000-8000-000000005b01',
    '00000000-0000-4000-8000-000000000b01'), false, 'de novo: já era membro, nada muda');
select is((select count(*)::int from membros_turma where turma_id = '00000000-0000-4000-8000-00000000cb01'),
  1, 'sem matrícula duplicada');

select throws_ok($$ select entrar_na_turma_pela_sessao('00000000-0000-4000-8000-000000005b01',
    '00000000-0000-4000-8000-0000000000ba') $$, 'LU403', null, 'o professor nunca vira aluno da própria turma');
select throws_ok($$ select entrar_na_turma_pela_sessao('00000000-0000-4000-8000-0000000fffff',
    '00000000-0000-4000-8000-000000000b02') $$, 'LU410', null, 'sessão inexistente: encerrada');

update sessoes set entrada_aberta = false where id = '00000000-0000-4000-8000-000000005b01';
select throws_ok($$ select entrar_na_turma_pela_sessao('00000000-0000-4000-8000-000000005b01',
    '00000000-0000-4000-8000-000000000b02') $$, 'LU410', null, 'entrada fechada: não matricula');

update sessoes set aberta = false, encerrada_em = now() where id = '00000000-0000-4000-8000-000000005b01';
select throws_ok($$ select entrar_na_turma_pela_sessao('00000000-0000-4000-8000-000000005b01',
    '00000000-0000-4000-8000-000000000b02') $$, 'LU410', null, 'sessão encerrada: não matricula');
select ok(not exists (select 1 from membros_turma where aluno_id = '00000000-0000-4000-8000-000000000b02'),
  'quem foi barrado não ficou na turma');

select ok(not has_function_privilege('authenticated', 'public.entrar_na_turma_pela_sessao(uuid, uuid)', 'execute'),
  'aluno logado não chama a função direto (só a aluno-api)');
select ok(not has_function_privilege('anon', 'public.entrar_na_turma_pela_sessao(uuid, uuid)', 'execute'),
  'anon também não');

select * from finish();
rollback;
