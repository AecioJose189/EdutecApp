-- Envio de tentativa: correção da múltipla escolha no servidor, BKT por conceito, abertas fora do KT,
-- envio único, sessão encerrada e prazo.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(13);

insert into auth.users (id, email) values
  ('00000000-0000-4000-8000-0000000000aa', 'prof@exemplo.com'),
  ('00000000-0000-4000-8000-000000000a01', 'aluno1@exemplo.com'),
  ('00000000-0000-4000-8000-000000000a02', 'aluno2@exemplo.com');
insert into turmas (id, nome, codigo, professor_id)
  values ('00000000-0000-4000-8000-00000000ca01', 'IA', 'A', '00000000-0000-4000-8000-0000000000aa');

-- Q1 (MC, Base e Meio, certa = c), Q2 (MC, Topo, certa = a), Q3 (aberta, Topo).
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-30, repeat('f', 64), 3, 'mista', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Meio", "prerequisitos": ["Base"]},
    {"nome": "Topo", "prerequisitos": ["Meio"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base", "Meio"],
     "alternativas": [{"texto": "1", "correta": false}, {"texto": "2", "correta": false}, {"texto": "3", "correta": true}]},
    {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true}, {"texto": "2", "correta": false}]},
    {"tipo": "aberta", "enunciado": "Q3", "conceitos": ["Topo"], "alternativas": null, "rubrica": "r"}]}');

select publicar_atividade('00000000-0000-4000-8000-00000000ca01', -30, true, 10);
select is((select titulo from atividades where geracao_id = -30), 'Base, Meio e outros',
  'título padrão: dois primeiros conceitos');
select is((select questoes_aluno -> 2 ->> 'alternativas' from atividades where geracao_id = -30), null,
  'questão aberta sem alternativas');

insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005e01', id from atividades where geracao_id = -30;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome, prazo_em) values
  ('00000000-0000-4000-8000-00000000e001', '00000000-0000-4000-8000-000000005e01',
   '00000000-0000-4000-8000-000000000a01', 'Aluno Um', now() + interval '10 minutes'),
  ('00000000-0000-4000-8000-00000000e002', '00000000-0000-4000-8000-000000005e01',
   '00000000-0000-4000-8000-000000000a02', 'Aluna Dois', now() - interval '1 minute');

-- Aluno 1: acerta Q1, erra Q2 (alternativa em maiúscula também vale), responde a aberta.
select results_eq($$ select * from enviar_tentativa('00000000-0000-4000-8000-00000000e001',
    '00000000-0000-4000-8000-000000000a01',
    '[{"questao_id": "1", "alternativa_id": "c"}, {"questao_id": "2", "alternativa_id": "B"},
      {"questao_id": "3", "texto": "minha resposta"}]') $$,
  $$ values (1, 2, 1) $$, '1 acerto de 2 de múltipla escolha, 1 aberta aguardando correção');

select is((select array_agg(conceito || ':' || correta order by conceito) from respostas
            where tentativa_id = '00000000-0000-4000-8000-00000000e001'),
  array['Base:true', 'Meio:true', 'Topo:false'], 'cada conceito das questões de múltipla escolha vai para o BKT');
select ok((select bool_and(aluno_id = '00000000-0000-4000-8000-000000000a01'
                           and aluno_pseudonimo = aluno_id::text and turma_id = '00000000-0000-4000-8000-00000000ca01')
             from respostas where tentativa_id = '00000000-0000-4000-8000-00000000e001'),
  'respostas ligadas ao aluno (uuid), à turma e à tentativa');
select ok(abs((select p_dominio from dominio where aluno_id = '00000000-0000-4000-8000-000000000a01'
                and conceito = 'Base') - 142.0/205) < 1e-9, 'BKT: um acerto a partir de L0 dá 142/205');
select is((select n_respostas from dominio where aluno_id = '00000000-0000-4000-8000-000000000a01'
            and conceito = 'Topo'), 1, 'a aberta (também de Topo) não entra no KT');
select is((select respostas -> 2 ->> 'situacao' from tentativas where id = '00000000-0000-4000-8000-00000000e001'),
  'aguardando_correcao', 'aberta guardada como aguardando correção');
select ok((select enviada_em is not null and acertos = 1 from tentativas where id = '00000000-0000-4000-8000-00000000e001'),
  'tentativa marcada como enviada');

select throws_ok($$ select enviar_tentativa('00000000-0000-4000-8000-00000000e001',
    '00000000-0000-4000-8000-000000000a01', '[]') $$, 'LU409', null, 'envio único por tentativa');
select throws_ok($$ select enviar_tentativa('00000000-0000-4000-8000-00000000e002',
    '00000000-0000-4000-8000-000000000a01', '[]') $$, 'LU403', null, 'não envia a tentativa de outro aluno');
select throws_ok($$ select enviar_tentativa('00000000-0000-4000-8000-00000000e002',
    '00000000-0000-4000-8000-000000000a02', '[]') $$, 'LU408', null, 'depois do prazo (+30 s): tempo esgotado');

update sessoes set aberta = false, entrada_aberta = false, encerrada_em = now()
 where id = '00000000-0000-4000-8000-000000005e01';
update tentativas set prazo_em = null where id = '00000000-0000-4000-8000-00000000e002';
select throws_ok($$ select enviar_tentativa('00000000-0000-4000-8000-00000000e002',
    '00000000-0000-4000-8000-000000000a02', '[]') $$, 'LU410', null, 'sessão encerrada não recebe envios');

select * from finish();
rollback;
