-- Alternativas embaralhadas por tentativa (anti-cola em sala, fase 2b; docs/contrato-api-fase2.md).
--
-- Cada tentativa vê as alternativas de cada questão numa ordem própria, sorteada de forma
-- determinística a partir do id da tentativa: recarregar a página mostra a mesma ordem, e dois alunos
-- quase sempre veem ordens diferentes. Os ids das alternativas continuam os originais ("a", "b"...):
-- enviar_tentativa corrige como antes. O site rotula A, B, C... pela POSIÇÃO, não pelo id.
-- A ordem das questões não muda. revisao_tentativa mostra as alternativas na ordem que o aluno viu.

-- Chave de ordenação de uma alternativa dentro da tentativa (ordenar por ela = a ordem sorteada).
create function public.chave_alternativa(tentativa uuid, questao_id text, alternativa_id text) returns text
language sql
immutable
strict
parallel safe
set search_path = ''
as $$
  select md5(tentativa::text || ':' || questao_id || ':' || alternativa_id);
$$;

-- As questões como o aluno desta tentativa as vê: questoes_aluno (sem gabarito) com as alternativas
-- de cada questão na ordem da tentativa. null se a tentativa não existir.
create function public.questoes_da_tentativa(tentativa uuid) returns jsonb
language sql
stable
set search_path = ''
as $$
  select coalesce(jsonb_agg(
      case when q.questao -> 'alternativas' is null or jsonb_typeof(q.questao -> 'alternativas') <> 'array'
           then q.questao
           else jsonb_set(q.questao, '{alternativas}', (
             select jsonb_agg(alt order by public.chave_alternativa(t.id, q.questao ->> 'id', alt ->> 'id'))
               from jsonb_array_elements(q.questao -> 'alternativas') alt))
      end order by q.i), '[]'::jsonb)
    from public.tentativas t
    join public.sessoes s on s.id = t.sessao_id
    join public.atividades a on a.id = s.atividade_id
    cross join lateral jsonb_array_elements(a.questoes_aluno) with ordinality as q (questao, i)
   where t.id = questoes_da_tentativa.tentativa
  having count(*) > 0;
$$;

-- Revisão: igual à da migration 20261127130000, com as alternativas na ordem da tentativa.
create or replace function public.revisao_tentativa(tentativa uuid, aluno uuid) returns jsonb
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
            order by public.chave_alternativa(t.id, q.id, chr(96 + a.j::int))), '[]'::jsonb)
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
  public.chave_alternativa(uuid, text, text),
  public.questoes_da_tentativa(uuid)
  from public, anon, authenticated;
grant execute on function
  public.chave_alternativa(uuid, text, text),
  public.questoes_da_tentativa(uuid)
  to service_role;
