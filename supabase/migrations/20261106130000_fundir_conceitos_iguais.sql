-- Dados existentes: funde automaticamente SÓ os conceitos da mesma turma cujo nome normalizado é
-- IGUAL (ex.: "Evento complementar" e "Evento Complementar"). Nomes só parecidos ficam como estão e
-- aparecem para o professor como possíveis duplicatas.
--
-- Destino de cada grupo: o conceito mais antigo da turma (criado_em; empate: nome). Os outros do grupo
-- são fundidos nele por privado.fundir_conceitos_interno (automatica = true, fundido_por = null),
-- que move questões, pré-requisitos e respostas e refaz o BKT do destino.
-- Prévia (o que esta migration faz), sem alterar nada: supabase/tests/previa_fundir_conceitos.sql.
--
-- Depois, o índice único (turma_id, nome_normalizado) impede novas duplicatas.

do $$
declare
  g record;
  resultado jsonb;
begin
  for g in
    select ct.turma_id, ct.nome as origem,
           first_value(ct.nome) over w as destino,
           row_number() over w as posicao
      from public.conceitos_turma ct
    window w as (partition by ct.turma_id, ct.nome_normalizado order by ct.criado_em, ct.nome)
     order by ct.turma_id, ct.nome_normalizado, posicao
  loop
    continue when g.posicao = 1;
    resultado := privado.fundir_conceitos_interno(g.turma_id, g.origem, g.destino, null, true);
    raise notice 'Turma %: "%" → "%" (% respostas, % alunos)',
      g.turma_id, g.origem, g.destino, resultado ->> 'n_respostas', resultado ->> 'n_alunos';
  end loop;
end;
$$;

create unique index conceitos_turma_nome_normalizado_key
  on public.conceitos_turma (turma_id, nome_normalizado);
