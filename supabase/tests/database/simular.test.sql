-- Simulação de demonstração: 25 pseudônimos, reproduzível e substituindo a simulação anterior.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(5);

insert into turmas (id, nome, codigo) values ('00000000-0000-4000-8000-0000000000c3', 'Teste', 'Sim');
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-10, repeat('d', 64), 2, 'mista', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []},
    {"nome": "Meio", "prerequisitos": ["Base"]},
    {"nome": "Topo", "prerequisitos": ["Meio"]}],
  "questoes": [{"tipo": "aberta", "enunciado": "?", "conceitos": ["Base", "Meio"]},
               {"tipo": "aberta", "enunciado": "?", "conceitos": ["Topo"]}]}');
select publicar_atividade('00000000-0000-4000-8000-0000000000c3', -10, true, null);

select results_eq($$ select * from simular_respostas('00000000-0000-4000-8000-0000000000c3') $$,
  $$ values (25, 225) $$, '25 alunos × 3 conceitos × 3 tentativas');

create temp table primeira as
  select conceito, media_dominio from dominio_turma where turma_id = '00000000-0000-4000-8000-0000000000c3';

select simular_respostas('00000000-0000-4000-8000-0000000000c3');
select is((select count(*)::int from respostas where turma_id = '00000000-0000-4000-8000-0000000000c3'), 225,
  'rodar de novo substitui a simulação anterior');
select set_eq($$ select conceito, media_dominio from dominio_turma
                  where turma_id = '00000000-0000-4000-8000-0000000000c3' $$,
  $$ select * from primeira $$, 'mesma semente, mesmo resultado');
select ok((select bool_and(aluno_pseudonimo ~ '^sim-[0-9a-f-]+-[0-9]{2}$') from dominio
           where turma_id = '00000000-0000-4000-8000-0000000000c3'), 'só pseudônimos (RN13)');
select ok((select media_dominio from dominio_turma where conceito = 'Base' and turma_id = '00000000-0000-4000-8000-0000000000c3')
        > (select media_dominio from dominio_turma where conceito = 'Topo' and turma_id = '00000000-0000-4000-8000-0000000000c3'),
  'conceito-base mais dominado que o do topo');

select * from finish();
rollback;
