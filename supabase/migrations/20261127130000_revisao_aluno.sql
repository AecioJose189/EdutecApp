-- Fase 2: o aluno revê as próprias atividades (docs/contrato-api-fase2.md).
--
-- Regra de revelação (anti-cola), aplicada AQUI, não na Edge Function:
--   sessão aberta    → acertou por questão, sem alternativa correta, feedback nem rubrica;
--   sessão encerrada → gabarito completo (correta + feedback por alternativa, rubrica das abertas).
-- Os campos escondidos SOMEM do JSON (não vêm null).
--
-- Só a service_role chama (a aluno-api passa o uuid do aluno logado). O aluno só vê tentativas
-- dele e já enviadas; qualquer outra → LU403 (nao_matriculado).
--
-- numero da atividade = ordem de publicação na turma (publicada_em, id), a mesma da turma-api
-- (listar_atividades) que o professor vê no desktop.

create function public.numero_da_atividade(atividade uuid) returns int
language sql
stable
set search_path = ''
as $$
  select n::int from (
    select a.id, row_number() over (partition by a.turma_id order by a.publicada_em, a.id) as n
      from public.atividades a
     where a.turma_id = (select x.turma_id from public.atividades x where x.id = atividade)
  ) o where o.id = atividade;
$$;

-- Tentativas enviadas do aluno, da mais recente para a mais antiga (turma opcional).
create function public.minhas_atividades(aluno uuid, turma uuid default null) returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object('atividades', coalesce(jsonb_agg(item order by enviada_em desc, tentativa_id), '[]'::jsonb))
    from (
      select t.enviada_em, t.id as tentativa_id, jsonb_build_object(
        'tentativa_id', t.id,
        'atividade', jsonb_build_object('id', a.id, 'numero', public.numero_da_atividade(a.id), 'titulo', a.titulo),
        'turma', jsonb_build_object('id', tu.id, 'nome', tu.nome, 'codigo', tu.codigo),
        'enviada_em', to_char(t.enviada_em at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
        'acertos', t.acertos,
        'total_multipla_escolha', t.total_multipla_escolha,
        'gabarito_liberado', not s.aberta
      ) as item
        from public.tentativas t
        join public.sessoes s on s.id = t.sessao_id
        join public.atividades a on a.id = s.atividade_id
        join public.turmas tu on tu.id = a.turma_id
       where t.aluno_id = minhas_atividades.aluno
         and t.enviada_em is not null
         and (minhas_atividades.turma is null or a.turma_id = minhas_atividades.turma)
    ) x;
$$;

-- Revisão de uma tentativa enviada do próprio aluno.
create function public.revisao_tentativa(tentativa uuid, aluno uuid) returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  t public.tentativas;
  s public.sessoes;
  atv public.atividades;
  liberado boolean;
  questoes jsonb := '[]'::jsonb;
  q record;
  r jsonb;
  item jsonb;
  minha jsonb;
begin
  select * into t from public.tentativas x where x.id = revisao_tentativa.tentativa;
  if not found or t.aluno_id <> aluno or t.enviada_em is null then
    raise exception 'Tentativa não encontrada para este aluno.' using errcode = 'LU403';
  end if;
  select * into s from public.sessoes x where x.id = t.sessao_id;
  select * into atv from public.atividades x where x.id = s.atividade_id;
  liberado := not s.aberta;

  for q in select e.questao, e.i::text as id
             from jsonb_array_elements(atv.questoes) with ordinality as e (questao, i) order by e.i loop
    select x into r from jsonb_array_elements(coalesce(t.respostas, '[]'::jsonb)) x
     where x ->> 'questao_id' = q.id limit 1;

    item := jsonb_build_object(
      'questao_id', q.id,
      'tipo', q.questao ->> 'tipo',
      'enunciado', q.questao ->> 'enunciado',
      'conceitos', coalesce(q.questao -> 'conceitos', '[]'::jsonb)
    );

    if q.questao ->> 'tipo' = 'multipla_escolha' then
      minha := case when r ->> 'alternativa_id' is not null
                    then jsonb_build_object('alternativa_id', r ->> 'alternativa_id') else '{}'::jsonb end;
      item := item || jsonb_build_object(
        'minha_resposta', minha,
        'acertou', coalesce((r ->> 'correta')::boolean, false),
        'alternativas', (
          select coalesce(jsonb_agg(
            jsonb_build_object('id', chr(96 + a.j::int), 'texto', a.alternativa ->> 'texto')
              || case when liberado then jsonb_build_object(
                   'correta', coalesce((a.alternativa ->> 'correta')::boolean, false),
                   'feedback', coalesce(a.alternativa ->> 'feedback', ''))
                 else '{}'::jsonb end
            order by a.j), '[]'::jsonb)
            from jsonb_array_elements(q.questao -> 'alternativas') with ordinality as a (alternativa, j)
        )
      );
    else
      minha := case when coalesce(trim(r ->> 'texto'), '') <> ''
                    then jsonb_build_object('texto', r ->> 'texto') else '{}'::jsonb end;
      item := item || jsonb_build_object('minha_resposta', minha, 'acertou', null);
      if liberado then
        item := item || jsonb_build_object('rubrica', coalesce(q.questao ->> 'rubrica', ''));
      end if;
    end if;

    questoes := questoes || jsonb_build_array(item);
  end loop;

  return jsonb_build_object(
    'atividade', jsonb_build_object('id', atv.id, 'numero', public.numero_da_atividade(atv.id), 'titulo', atv.titulo),
    'gabarito_liberado', liberado,
    'acertos', t.acertos,
    'total_multipla_escolha', t.total_multipla_escolha,
    'questoes', questoes
  );
end;
$$;

revoke execute on function
  public.numero_da_atividade(uuid),
  public.minhas_atividades(uuid, uuid),
  public.revisao_tentativa(uuid, uuid)
  from public, anon, authenticated;
grant execute on function
  public.numero_da_atividade(uuid),
  public.minhas_atividades(uuid, uuid),
  public.revisao_tentativa(uuid, uuid)
  to service_role;
