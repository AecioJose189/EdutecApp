-- Confiança na resposta (fase 2b; docs/contrato-api-fase2.md).
--
-- enviar_respostas aceita, em cada resposta, confianca opcional: "certeza" | "acho" | "chute".
-- Sem o campo (site antigo) ou com valor fora da lista, fica nulo. Fica gravada:
--   - em tentativas.respostas (o jsonb de cada questão, também nas abertas);
--   - em respostas.confianca (uma linha por conceito das questões de múltipla escolha respondidas).
-- O BKT NÃO muda: a confiança só é guardada e lida (revisão do aluno e estatísticas do professor).
-- confianca_turma(turma): leitura para as estatísticas (só múltipla escolha respondida), chamada pela
-- turma-api depois de conferir que o professor é o dono da turma.

alter table public.respostas
  add column confianca text check (confianca in ('certeza', 'acho', 'chute'));

-- registrar_resposta ganha a confiança (opcional, no fim: as chamadas antigas continuam valendo).
drop function public.registrar_resposta(text, text, boolean, uuid, uuid, uuid);

create function public.registrar_resposta(
  aluno text, conceito text, correta boolean, turma uuid default null,
  aluno_id uuid default null, tentativa uuid default null, confianca text default null
)
returns table (p_dominio numeric, estado text)
language plpgsql
set search_path = ''
as $$
declare
  par public.bkt_parametros;
  atual public.dominio;
  novo numeric;
begin
  select * into par from public.bkt_parametros b
   where b.conceito in (registrar_resposta.conceito, '*')
   order by (b.conceito = '*')  -- o específico (false) vem antes do padrão
   limit 1;
  if not found then
    raise exception 'bkt_parametros sem a linha padrão ''*''';
  end if;

  -- Garante a linha e a trava, para respostas simultâneas do mesmo aluno não se perderem.
  insert into public.dominio (turma_id, aluno_pseudonimo, aluno_id, conceito, p_dominio, n_respostas)
  values (turma, aluno, registrar_resposta.aluno_id, registrar_resposta.conceito, par.p_l0, 0)
  on conflict do nothing;

  select * into atual from public.dominio d
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito
   for update;

  insert into public.respostas (turma_id, aluno_pseudonimo, aluno_id, tentativa_id, conceito, correta, tentativa,
                                confianca)
  values (turma, aluno, registrar_resposta.aluno_id, registrar_resposta.tentativa, registrar_resposta.conceito,
          correta, atual.n_respostas + 1, registrar_resposta.confianca);

  novo := public.bkt_passo(atual.p_dominio, correta, par.p_t, par.p_g, par.p_s);

  update public.dominio d
     set p_dominio = novo, n_respostas = atual.n_respostas + 1, atualizado_em = now()
   where d.turma_id is not distinct from turma
     and d.aluno_pseudonimo = aluno and d.conceito = registrar_resposta.conceito;

  return query select novo, public.estado_dominio(novo);
end;
$$;

-- enviar_tentativa: igual à da migration 20261027130000, gravando a confiança.
create or replace function public.enviar_tentativa(tentativa uuid, aluno uuid, respostas jsonb)
returns table (acertos int, total_multipla_escolha int, abertas_aguardando_correcao int)
language plpgsql
set search_path = ''
as $$
declare
  t public.tentativas;
  s public.sessoes;
  atv public.atividades;
  q record;
  resposta jsonb;
  alternativa text;
  indice int;
  correta boolean;
  conceito text;
  n_acertos int := 0;
  n_mc int := 0;
  n_abertas int := 0;
  registro jsonb := '[]'::jsonb;
  conf text;
begin
  select * into t from public.tentativas x where x.id = enviar_tentativa.tentativa for update;
  if not found or t.aluno_id <> aluno then
    raise exception 'Tentativa não encontrada para este aluno.' using errcode = 'LU403';
  end if;
  if t.enviada_em is not null then
    raise exception 'Esta tentativa já foi enviada.' using errcode = 'LU409';
  end if;

  select * into s from public.sessoes x where x.id = t.sessao_id;
  if not s.aberta then
    raise exception 'A sessão foi encerrada pelo professor.' using errcode = 'LU410';
  end if;
  if t.prazo_em is not null and now() > t.prazo_em + interval '30 seconds' then
    raise exception 'O tempo da atividade acabou.' using errcode = 'LU408';
  end if;

  select * into atv from public.atividades x where x.id = s.atividade_id;

  for q in select questao, i::text as id from jsonb_array_elements(atv.questoes) with ordinality as e (questao, i) loop
    select r into resposta
      from jsonb_array_elements(coalesce(respostas, '[]'::jsonb)) r
     where r ->> 'questao_id' = q.id
     limit 1;
    -- Confiança declarada (opcional). Valor fora da lista conta como não informado.
    conf := lower(trim(resposta ->> 'confianca'));
    if conf is null or conf not in ('certeza', 'acho', 'chute') then conf := null; end if;

    if q.questao ->> 'tipo' = 'multipla_escolha' then
      n_mc := n_mc + 1;
      alternativa := lower(trim(resposta ->> 'alternativa_id'));
      indice := case when alternativa ~ '^[a-z]$' then ascii(alternativa) - 96 end;
      if indice is null or indice > jsonb_array_length(q.questao -> 'alternativas') then
        registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'multipla_escolha',
          'alternativa_id', null, 'correta', false, 'situacao', 'sem_resposta', 'confianca', null);
        continue;
      end if;
      correta := coalesce((q.questao -> 'alternativas' -> (indice - 1) ->> 'correta')::boolean, false);
      if correta then n_acertos := n_acertos + 1; end if;
      for conceito in select jsonb_array_elements_text(q.questao -> 'conceitos') loop
        perform public.registrar_resposta(aluno::text, trim(conceito), correta, atv.turma_id, aluno, t.id, conf);
      end loop;
      registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'multipla_escolha',
        'alternativa_id', alternativa, 'correta', correta, 'situacao', 'corrigida', 'confianca', conf);
    else
      n_abertas := n_abertas + 1;
      registro := registro || jsonb_build_object('questao_id', q.id, 'tipo', 'aberta',
        'texto', left(coalesce(resposta ->> 'texto', ''), 10000), 'correta', null,
        'situacao', 'aguardando_correcao', 'confianca', conf);
    end if;
  end loop;

  update public.tentativas x
     set enviada_em = now(), acertos = n_acertos, total_multipla_escolha = n_mc,
         abertas_aguardando_correcao = n_abertas, respostas = registro
   where x.id = t.id;

  return query select n_acertos, n_mc, n_abertas;
end;
$$;

-- Revisão: devolve a confiança de cada questão (null se não informada).
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
        'confianca', r -> 'confianca',
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
      item := item || jsonb_build_object('minha_resposta', minha, 'confianca', r -> 'confianca', 'acertou', null);
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


-- Estatísticas de confiança da turma: total, por conceito e por aluno. Cada questão de múltipla
-- escolha respondida conta uma vez no total e no aluno, e uma vez em cada conceito dela.
--   n_respostas        questões de múltipla escolha respondidas
--   n_com_confianca    das quais o aluno informou a confiança
--   erros_com_certeza  errou dizendo "certeza" (equívoco: o aluno acha que sabe)
--   acertos_no_chute   acertou dizendo "chute" (acerto que não indica domínio)
create function public.confianca_turma(turma uuid) returns jsonb
language sql
stable
set search_path = ''
as $$
  with itens as (
    select t.aluno_id, coalesce(p.nome, t.aluno_nome) as nome,
           (r ->> 'correta')::boolean as correta, r ->> 'confianca' as confianca,
           coalesce(a.questoes -> ((r ->> 'questao_id')::int - 1) -> 'conceitos', '[]'::jsonb) as conceitos
      from public.atividades a
      join public.sessoes s on s.atividade_id = a.id
      join public.tentativas t on t.sessao_id = s.id and t.enviada_em is not null
      left join public.perfis p on p.id = t.aluno_id
      cross join lateral jsonb_array_elements(coalesce(t.respostas, '[]'::jsonb)) r
     where a.turma_id = confianca_turma.turma
       and r ->> 'tipo' = 'multipla_escolha' and r ->> 'situacao' = 'corrigida'
  ),
  por_conceito as (
    select trim(c) as conceito, i.* from itens i cross join lateral jsonb_array_elements_text(i.conceitos) c
  )
  select jsonb_build_object(
    'turma', (select jsonb_build_object(
        'n_respostas', count(*),
        'n_com_confianca', count(confianca),
        'erros_com_certeza', count(*) filter (where confianca = 'certeza' and not correta),
        'acertos_no_chute', count(*) filter (where confianca = 'chute' and correta)) from itens),
    'conceitos', coalesce((select jsonb_agg(x order by x ->> 'conceito') from (
        select jsonb_build_object('conceito', conceito,
          'n_respostas', count(*),
          'n_com_confianca', count(confianca),
          'erros_com_certeza', count(*) filter (where confianca = 'certeza' and not correta),
          'acertos_no_chute', count(*) filter (where confianca = 'chute' and correta)) as x
          from por_conceito group by conceito) g), '[]'::jsonb),
    'alunos', coalesce((select jsonb_agg(x order by x ->> 'nome', x ->> 'aluno_id') from (
        select jsonb_build_object('aluno_id', aluno_id, 'nome', min(nome),
          'n_respostas', count(*),
          'n_com_confianca', count(confianca),
          'erros_com_certeza', count(*) filter (where confianca = 'certeza' and not correta),
          'acertos_no_chute', count(*) filter (where confianca = 'chute' and correta)) as x
          from itens group by aluno_id) g), '[]'::jsonb)
  );
$$;

revoke execute on function
  public.registrar_resposta(text, text, boolean, uuid, uuid, uuid, text),
  public.enviar_tentativa(uuid, uuid, jsonb),
  public.confianca_turma(uuid)
  from public, anon, authenticated;
grant execute on function
  public.registrar_resposta(text, text, boolean, uuid, uuid, uuid, text),
  public.enviar_tentativa(uuid, uuid, jsonb),
  public.confianca_turma(uuid)
  to service_role;
