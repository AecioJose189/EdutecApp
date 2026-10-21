-- Publicação de atividades: junção dos conceitos da turma e rejeição de ciclo (RN03).
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(7);

insert into turmas (id, nome, codigo) values ('00000000-0000-4000-8000-000000000001', 'Teste', 'Turma T');

-- Geração 1: A ← B ← C (B requer A; C requer B). Geração 2: D requer A, e B passa a requerer D.
-- Geração 3: A requer C → ciclo A → C → B → A.
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values
  (-1, repeat('a', 64), 1, 'mista', 'media', '{"conceitos": [
      {"nome": "A", "prerequisitos": []},
      {"nome": "B", "prerequisitos": ["A"]},
      {"nome": "C", "prerequisitos": ["B"]}],
    "questoes": [{"tipo": "aberta", "enunciado": "?", "conceitos": ["C"]}]}'),
  (-2, repeat('b', 64), 1, 'mista', 'media', '{"conceitos": [
      {"nome": "A", "prerequisitos": []},
      {"nome": "D", "prerequisitos": ["A"]},
      {"nome": "B", "prerequisitos": ["A", "D"]}],
    "questoes": [{"tipo": "aberta", "enunciado": "?", "conceitos": ["D"]}]}'),
  (-3, repeat('c', 64), 1, 'mista', 'media', '{"conceitos": [
      {"nome": "C", "prerequisitos": []},
      {"nome": "A", "prerequisitos": ["C"]}],
    "questoes": [{"tipo": "aberta", "enunciado": "?", "conceitos": ["A"]}]}');

select lives_ok($$ select publicar_atividade('00000000-0000-4000-8000-000000000001', -1, true, 30) $$,
  'publica a primeira atividade');
select lives_ok($$ select publicar_atividade('00000000-0000-4000-8000-000000000001', -2, false, null) $$,
  'publica a segunda, com conceitos em comum');
select is((select count(*)::int from conceitos_turma where turma_id = '00000000-0000-4000-8000-000000000001'), 4,
  'conceitos juntados sem duplicar (A, B, C, D)');
select is((select prerequisitos from conceitos_turma where nome = 'B'), array['A', 'D'],
  'pré-requisitos somados sem repetir');
select throws_ok($$ select publicar_atividade('00000000-0000-4000-8000-000000000001', -1, true, 30) $$,
  'ED409', null, 'a mesma geração não é publicada duas vezes na turma');
select throws_ok($$ select publicar_atividade('00000000-0000-4000-8000-000000000001', -3, true, 30) $$,
  'ED003', null, 'ciclo de pré-requisitos é rejeitado (RN03)');
select is((select count(*)::int from atividades where turma_id = '00000000-0000-4000-8000-000000000001'), 2,
  'a publicação com ciclo foi desfeita por inteiro');

select * from finish();
rollback;
