-- Teste do BKT contra valores calculados à mão (parâmetros padrão: L0=0,3 T=0,1 G=0,2 S=0,1).
-- Rodar: npx supabase test db --linked supabase/tests/database
-- Tudo em transação com ROLLBACK: não deixa dados no banco.
begin;
-- O usuário temporário do CLI (cli_login_postgres) não tem USAGE no schema "extensions", onde fica
-- o pgTAP; dentro da transação ele assume o papel postgres.
set local role postgres;
set local search_path = public, extensions;

select plan(9);

-- Caso 1 — um acerto a partir de L0 = 0,3:
--   posterior = 0,3·0,9 / (0,3·0,9 + 0,7·0,2) = 0,27/0,41 = 0,658537
--   depois de T: 0,658537 + 0,341463·0,1 = 0,692683  (= 142/205)
select ok(abs(bkt_passo(0.3, true, 0.1, 0.2, 0.1) - 142.0/205) < 1e-9,
  'acerto a partir de 0,3 dá 142/205 ≈ 0,692683');

-- Caso 2 — um erro a partir de L0 = 0,3:
--   posterior = 0,3·0,1 / (0,3·0,1 + 0,7·0,8) = 0,03/0,59 = 0,050847
--   depois de T: 0,050847 + 0,949153·0,1 = 0,145763  (= 43/295)
select ok(abs(bkt_passo(0.3, false, 0.1, 0.2, 0.1) - 43.0/295) < 1e-9,
  'erro a partir de 0,3 dá 43/295 ≈ 0,145763');

-- Caso 3 — dois acertos seguidos, via registrar_resposta (usa a tabela e a linha padrão '*'):
--   1º: 0,692683; 2º: posterior = 0,623415/0,684878 = 0,910256 → 0,919231  (= 239/260)
select is((select estado from registrar_resposta('demo-teste', 'Conceito de teste', true)),
  'em_desenvolvimento', '1º acerto: 0,6927 → em_desenvolvimento');
select ok(abs((select p_dominio from registrar_resposta('demo-teste', 'Conceito de teste', true)) - 239.0/260) < 1e-9,
  '2º acerto seguido dá 239/260 ≈ 0,919231');
select is((select n_respostas from dominio where aluno_pseudonimo = 'demo-teste'), 2, 'dominio conta 2 respostas');
select is((select array_agg(tentativa order by id) from respostas where aluno_pseudonimo = 'demo-teste'),
  array[1, 2], 'respostas numeradas como tentativa 1 e 2');

-- Limiares (proposta) e regras de acesso aos dados.
select is(array[estado_dominio(0.49), estado_dominio(0.5), estado_dominio(0.79), estado_dominio(0.8)],
  array['revisar', 'em_desenvolvimento', 'em_desenvolvimento', 'consolidado'], 'limiares 0,5 e 0,8');
select throws_ok($$ update respostas set correta = false where aluno_pseudonimo = 'demo-teste' $$,
  '42501', null, 'respostas não aceita UPDATE (só inserção)');
select throws_ok($$ select registrar_resposta('maria@exemplo.com', 'Conceito de teste', true) $$,
  '23514', null, 'e-mail não é aceito como pseudônimo (RN13)');

select * from finish();
rollback;
