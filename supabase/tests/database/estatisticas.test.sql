-- Estatísticas da turma (UC13) e mapa/histórico do aluno (UC11/UC12), fase 3.
-- O BKT refeito pelas respostas até agora bate com a tabela dominio; a evolução usa o BKT refeito até
-- o fim de cada atividade encerrada; reais × simulação.
-- Rodar: npx supabase test db --linked supabase/tests/database
begin;
set local role postgres;
set local search_path = public, extensions;

select plan(30);

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-4000-8000-0000000000fa', 'prof@exemplo.com', '{"full_name": "Prof"}'),
  ('00000000-0000-4000-8000-000000000f01', 'ana@exemplo.com', '{"full_name": "Ana"}'),
  ('00000000-0000-4000-8000-000000000f02', 'bia@exemplo.com', '{"full_name": "Bia"}'),
  ('00000000-0000-4000-8000-000000000f03', 'caio@exemplo.com', '{"full_name": "Caio"}');
insert into turmas (id, nome, codigo, professor_id) values
  ('00000000-0000-4000-8000-00000000cf01', 'IA', 'A', '00000000-0000-4000-8000-0000000000fa'),
  ('00000000-0000-4000-8000-00000000cf02', 'IA', 'B', '00000000-0000-4000-8000-0000000000fa');
insert into membros_turma (turma_id, aluno_id) values
  ('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f01'),
  ('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f02'),
  ('00000000-0000-4000-8000-00000000cf01', '00000000-0000-4000-8000-000000000f03'),  -- Caio: sem respostas
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f01'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f02');

-- =========================================================================
-- Turma 1: caminho de verdade (enviar_tentativa + simulação)
-- =========================================================================
-- Q1 (Base, certa = a), Q2 (Base e Topo, certa = a), Q3 (Topo, certa = a).
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values (-80, repeat('8', 64), 3, 'multipla_escolha', 'media', '{"conceitos": [
    {"nome": "Base", "prerequisitos": []}, {"nome": "Topo", "prerequisitos": ["Base"]}],
  "questoes": [
    {"tipo": "multipla_escolha", "enunciado": "Q1", "conceitos": ["Base"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q2", "conceitos": ["Base", "Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]},
    {"tipo": "multipla_escolha", "enunciado": "Q3", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cf01', -80, true, null, 'Atividade A');
insert into sessoes (id, atividade_id) select '00000000-0000-4000-8000-000000005f01', id from atividades where geracao_id = -80;
insert into tentativas (id, sessao_id, aluno_id, aluno_nome) values
  ('00000000-0000-4000-8000-00000000f801', '00000000-0000-4000-8000-000000005f01', '00000000-0000-4000-8000-000000000f01', 'Ana'),
  ('00000000-0000-4000-8000-00000000f802', '00000000-0000-4000-8000-000000005f01', '00000000-0000-4000-8000-000000000f02', 'Bia');
-- Ana: Q1 errada com certeza, Q2 certa no chute, Q3 certa. Bia: tudo certo.
select enviar_tentativa('00000000-0000-4000-8000-00000000f801', '00000000-0000-4000-8000-000000000f01', '[
  {"questao_id": "1", "alternativa_id": "b", "confianca": "certeza"},
  {"questao_id": "2", "alternativa_id": "a", "confianca": "chute"},
  {"questao_id": "3", "alternativa_id": "a"}]');
select enviar_tentativa('00000000-0000-4000-8000-00000000f802', '00000000-0000-4000-8000-000000000f02', '[
  {"questao_id": "1", "alternativa_id": "a"}, {"questao_id": "2", "alternativa_id": "a"}, {"questao_id": "3", "alternativa_id": "a"}]');
update sessoes set aberta = false, entrada_aberta = false, encerrada_em = now() where id = '00000000-0000-4000-8000-000000005f01';
select simular_respostas('00000000-0000-4000-8000-00000000cf01');

-- ---------- BKT refeito = dominio ----------
select is((select count(*)::int from dominio_reprocessado('00000000-0000-4000-8000-00000000cf01', now(), true) r
             full join (select * from dominio where turma_id = '00000000-0000-4000-8000-00000000cf01') d
               on d.aluno_pseudonimo = r.aluno and d.conceito = r.conceito
            where r.p_dominio is distinct from d.p_dominio or r.n_respostas is distinct from d.n_respostas),
  0, 'BKT refeito até agora (reais + simulação) bate com dominio, par a par (valor e nº de respostas)');
select is((select count(*)::int from dominio_reprocessado('00000000-0000-4000-8000-00000000cf01', now())),
  4, 'só reais: Ana e Bia × Base e Topo');
select ok((select count(*) from dominio where turma_id = '00000000-0000-4000-8000-00000000cf01' and aluno_id is null) > 4,
  'a simulação também tem domínio na turma');

-- ---------- estatisticas_turma ----------
create temp table est as select estatisticas_turma('00000000-0000-4000-8000-00000000cf01') as j;
create temp table est_sim as select estatisticas_turma('00000000-0000-4000-8000-00000000cf01', true) as j;
select is((select j -> 'tem_simulacao' from est), 'true'::jsonb, 'tem_simulacao');
select is((select (j -> 'resumo' ->> 'n_alunos')::int from est), 3, 'reais: 3 alunos (membros, inclusive quem não respondeu)');
select ok((select (j -> 'resumo' ->> 'n_alunos')::int from est_sim) > 3, 'com a simulação, mais alunos');
select is((select (j -> 'resumo' ->> 'n_atividades_encerradas')::int from est), 1, '1 atividade encerrada');
select is((select (j -> 'resumo' ->> 'n_respostas')::int from est), 8, 'reais: 8 registros (Q2 conta em 2 conceitos, × 2 alunas)');
select is((select (j -> 'resumo' ->> 'media_dominio')::numeric from est),
  (select round(avg(p_dominio), 4) from dominio where turma_id = '00000000-0000-4000-8000-00000000cf01' and aluno_id is not null),
  'média do resumo = média dos pares reais');
select is((select (j -> 'resumo' ->> 'mediana_dominio')::numeric from est),
  (select round((percentile_cont(0.5) within group (order by p_dominio))::numeric, 4) from dominio
    where turma_id = '00000000-0000-4000-8000-00000000cf01' and aluno_id is not null), 'mediana do resumo');
select ok((select j -> 'resumo' ->> 'estado_moda' from est) in ('consolidado', 'em_desenvolvimento', 'revisar'), 'moda do estado');
select ok((select bool_and((c -> 'alunos_por_estado' ->> 'consolidado')::int + (c -> 'alunos_por_estado' ->> 'em_desenvolvimento')::int
                         + (c -> 'alunos_por_estado' ->> 'revisar')::int + (c -> 'alunos_por_estado' ->> 'sem_dados')::int = 3)
             from est, jsonb_array_elements(j -> 'conceitos') c), 'cada conceito: os 3 alunos distribuídos pelos estados (Caio = sem dados)');
select ok((select bool_and(coalesce((c ->> 'media_dominio')::numeric, 2) >= coalesce(prev, -1)) from (
             select c, lag((c ->> 'media_dominio')::numeric) over (order by o) as prev
               from est, jsonb_array_elements(j -> 'conceitos') with ordinality e(c, o)) x),
  'conceitos ordenados pela média (piores primeiro)');
select is((select (c ->> 'media_dominio')::numeric from est, jsonb_array_elements(j -> 'conceitos') c where c ->> 'conceito' = 'Base'),
  (select round(avg(p_dominio), 4) from dominio where turma_id = '00000000-0000-4000-8000-00000000cf01'
     and aluno_id is not null and conceito = 'Base'), 'média por conceito (só reais)');

-- ---------- mapa_aluno ----------
select is((select count(*)::int from jsonb_array_elements(mapa_aluno('00000000-0000-4000-8000-00000000cf01',
             '00000000-0000-4000-8000-000000000f03') -> 'conceitos') c where c -> 'p_dominio' = 'null'::jsonb and c -> 'estado' = 'null'::jsonb),
  2, 'aluno sem respostas: todos os conceitos sem dados');
select is((select (c ->> 'p_dominio')::numeric from jsonb_array_elements(mapa_aluno('00000000-0000-4000-8000-00000000cf01',
             '00000000-0000-4000-8000-000000000f01') -> 'conceitos') c where c ->> 'conceito' = 'Base'),
  (select round(p_dominio, 4) from dominio where aluno_pseudonimo = '00000000-0000-4000-8000-000000000f01'
     and turma_id = '00000000-0000-4000-8000-00000000cf01' and conceito = 'Base'), 'mapa do aluno = domínio dele');

-- ---------- histórico ----------
create temp table hist as select historico_aluno_conceito('00000000-0000-4000-8000-00000000cf01',
  '00000000-0000-4000-8000-000000000f01', 'Base') as j;
select is((select jsonb_agg(r -> 'acertou') from hist, jsonb_array_elements(j -> 'respostas') r), '[false, true]'::jsonb,
  'histórico em ordem: Q1 errada, Q2 certa');
select is((select jsonb_agg(r -> 'confianca') from hist, jsonb_array_elements(j -> 'respostas') r), '["certeza", "chute"]'::jsonb,
  'confiança de cada resposta');
select is((select (j -> 'respostas' -> -1 ->> 'p_dominio')::numeric from hist),
  (select round(p_dominio, 4) from dominio where aluno_pseudonimo = '00000000-0000-4000-8000-000000000f01'
     and turma_id = '00000000-0000-4000-8000-00000000cf01' and conceito = 'Base'), 'último ponto do histórico = domínio atual');
select is((select (j ->> 'erros_com_certeza')::int || '/' || (j ->> 'acertos_no_chute') from hist), '1/1', 'erros com certeza e acertos no chute');
select is((select j -> 'respostas' -> 0 -> 'atividade' ->> 'titulo' from hist), 'Atividade A', 'atividade de cada resposta');
select is((select (j ->> 'p_inicial')::numeric from hist), 0.3, 'ponto de partida (L0)');

-- =========================================================================
-- Turma 2: evolução por atividade, com horários explícitos
-- =========================================================================
insert into geracoes (id, hash_pdf, quantidade, formato, dificuldade, resultado)
overriding system value values
 (-81, repeat('9', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [{"nome": "Base", "prerequisitos": []}],
   "questoes": [{"tipo": "multipla_escolha", "enunciado": "Q", "conceitos": ["Base"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}'),
 (-82, repeat('7', 64), 1, 'multipla_escolha', 'media', '{"conceitos": [{"nome": "Topo", "prerequisitos": []}],
   "questoes": [{"tipo": "multipla_escolha", "enunciado": "Q", "conceitos": ["Topo"],
     "alternativas": [{"texto": "1", "correta": true, "feedback": "f"}, {"texto": "2", "correta": false, "feedback": "f"}]}]}');
select publicar_atividade('00000000-0000-4000-8000-00000000cf02', -81, true, null, 'Primeira');
select publicar_atividade('00000000-0000-4000-8000-00000000cf02', -82, true, null, 'Segunda');
update atividades set publicada_em = now() - interval '10 days' where geracao_id = -81;
insert into sessoes (id, atividade_id, aberta, entrada_aberta, aberta_em, encerrada_em)
  select '00000000-0000-4000-8000-000000005f21', id, false, false, now() - interval '9 days 1 hour', now() - interval '9 days'
    from atividades where geracao_id = -81;
insert into sessoes (id, atividade_id, aberta, entrada_aberta, aberta_em, encerrada_em)
  select '00000000-0000-4000-8000-000000005f22', id, false, false, now() - interval '2 days 1 hour', now() - interval '2 days'
    from atividades where geracao_id = -82;
insert into tentativas (sessao_id, aluno_id, aluno_nome, enviada_em, acertos, total_multipla_escolha, abertas_aguardando_correcao) values
  ('00000000-0000-4000-8000-000000005f21', '00000000-0000-4000-8000-000000000f01', 'Ana', now() - interval '9 days 30 minutes', 0, 1, 0),
  ('00000000-0000-4000-8000-000000005f21', '00000000-0000-4000-8000-000000000f02', 'Bia', now() - interval '9 days 30 minutes', 1, 1, 0),
  ('00000000-0000-4000-8000-000000005f22', '00000000-0000-4000-8000-000000000f01', 'Ana', now() - interval '2 days 30 minutes', 1, 1, 0),
  ('00000000-0000-4000-8000-000000005f22', '00000000-0000-4000-8000-000000000f02', 'Bia', now() - interval '2 days 30 minutes', 1, 1, 0);
-- Respostas no BKT: sessão 1 → Ana erra Base, Bia acerta Base; sessão 2 → Ana e Bia acertam Topo e
-- (revisão) Ana acerta Base. Uma resposta depois da última sessão não entra em nenhum ponto.
insert into respostas (turma_id, aluno_pseudonimo, aluno_id, conceito, correta, tentativa, criado_em) values
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f01', '00000000-0000-4000-8000-000000000f01', 'Base', false, 1, now() - interval '9 days 30 minutes'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f02', '00000000-0000-4000-8000-000000000f02', 'Base', true, 1, now() - interval '9 days 30 minutes'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f01', '00000000-0000-4000-8000-000000000f01', 'Topo', true, 1, now() - interval '2 days 30 minutes'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f02', '00000000-0000-4000-8000-000000000f02', 'Topo', true, 1, now() - interval '2 days 30 minutes'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f01', '00000000-0000-4000-8000-000000000f01', 'Base', true, 2, now() - interval '2 days 30 minutes'),
  ('00000000-0000-4000-8000-00000000cf02', '00000000-0000-4000-8000-000000000f01', '00000000-0000-4000-8000-000000000f01', 'Base', false, 3, now() - interval '1 day');

create temp table evo as select evolucao_turma('00000000-0000-4000-8000-00000000cf02') as j;
-- BKT padrão (L0 0,3 · T 0,1 · G 0,2 · S 0,1): acerto a partir de L0 → 142/205; erro → 0,1·… (bkt_passo).
select is((select jsonb_agg(e -> 'atividade' -> 'numero') from evo, jsonb_array_elements(j) e), '[1, 2]'::jsonb,
  'uma linha por atividade encerrada, na ordem da turma');
select is((select jsonb_agg(e -> 'taxa_acerto') from evo, jsonb_array_elements(j) e), '[0.5000, 1.0000]'::jsonb,
  'taxa de acerto: 1 de 2, depois 2 de 2');
select is((select (j -> 0 ->> 'media_dominio')::numeric from evo),
  round((bkt_passo(0.3, false, 0.1, 0.2, 0.1) + bkt_passo(0.3, true, 0.1, 0.2, 0.1)) / 2, 4),
  'domínio médio logo depois da 1ª: só as respostas até o fim da 1ª sessão');
select is((select (c ->> 'media_dominio')::numeric from evo, jsonb_array_elements(j -> 1 -> 'conceitos') c where c ->> 'conceito' = 'Base'),
  round((bkt_passo(bkt_passo(0.3, false, 0.1, 0.2, 0.1), true, 0.1, 0.2, 0.1) + bkt_passo(0.3, true, 0.1, 0.2, 0.1)) / 2, 4),
  'Base logo depois da 2ª: inclui o acerto da Ana na 2ª, não o erro de depois');
select is((select (c ->> 'media_anterior')::numeric from evo, jsonb_array_elements(j -> 1 -> 'conceitos') c where c ->> 'conceito' = 'Base'),
  (select (j -> 0 ->> 'media_dominio')::numeric from evo), 'media_anterior = o ponto da atividade anterior');
select is((select c -> 'media_anterior' from evo, jsonb_array_elements(j -> 1 -> 'conceitos') c where c ->> 'conceito' = 'Topo'),
  'null'::jsonb, 'conceito novo na 2ª: sem média anterior');
select is(evolucao_turma(gen_random_uuid()), '[]'::jsonb, 'turma sem atividade encerrada: lista vazia');

select ok(not has_function_privilege('authenticated', 'public.estatisticas_turma(uuid, boolean)', 'execute')
      and not has_function_privilege('authenticated', 'public.historico_aluno_conceito(uuid, text, text)', 'execute')
      and not has_function_privilege('anon', 'public.mapa_aluno(uuid, text)', 'execute'),
  'funções novas: só a service_role (via turma-api)');

select * from finish();
rollback;
