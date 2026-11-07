-- Prévia da migration 20261106130000_fundir_conceitos_iguais.sql: o que será fundido, sem alterar nada.
-- Roda antes da migration 20261106120000 (repete a normalização aqui, sem depender da coluna gerada).
-- Uso: colar no SQL Editor (ou execute_sql) do projeto.
with c as (
  select ct.turma_id, ct.nome, ct.criado_em,
         trim(regexp_replace(
           translate(lower(ct.nome),
             'áàâãäåāçćčéèêëēęíìîïīñńóòôõöøōúùûüūýÿ',
             'aaaaaaaccceeeeeeiiiiinnoooooooouuuuuyy'),
           '\s+', ' ', 'g')) as normalizado
    from public.conceitos_turma ct
),
g as (
  select c.*, first_value(c.nome) over w as destino, row_number() over w as posicao,
         count(*) over (partition by c.turma_id, c.normalizado) as n_grupo
    from c
  window w as (partition by c.turma_id, c.normalizado order by c.criado_em, c.nome)
)
select t.nome as turma, t.codigo, g.nome as origem, g.destino,
       (select count(*) from public.respostas r where r.turma_id = g.turma_id and r.conceito = g.nome) as respostas_movidas,
       (select count(*) from public.respostas r where r.turma_id = g.turma_id and r.conceito = g.nome and r.aluno_id is not null) as de_alunos_reais,
       (select count(*) from public.atividades a, jsonb_array_elements(a.questoes) q,
               jsonb_array_elements_text(q -> 'conceitos') x
         where a.turma_id = g.turma_id and trim(x) = g.nome) as questoes_religadas
  from g join public.turmas t on t.id = g.turma_id
 where g.posicao > 1
 order by t.nome, g.turma_id, g.destino, g.nome;
