-- Entrar na turma pelo QR da sessão (fase 2, docs/contrato-api-fase2.md).
--
-- Nos testes de novembro, quem leu o QR sem ter aceitado o convite ficou sem saída ("nao_matriculado").
-- Agora a aluno-api devolve a turma no erro e, com entrar_na_turma: true, matricula e já entra na sessão.
--
-- O token (HMAC) é conferido na Edge Function (_shared/qr.ts); aqui fica a regra de quem pode entrar:
--   sessão existe, está aberta e com a entrada aberta (senão LU410, sessao_encerrada);
--   o professor da turma nunca vira aluno dela (LU403; o gatilho membro_nao_e_professor também barra).
-- Idempotente: quem já é membro continua membro. Devolve true só quando a matrícula foi criada agora.

create function public.entrar_na_turma_pela_sessao(sessao uuid, aluno uuid) returns boolean
language plpgsql
set search_path = ''
as $$
declare
  v_turma uuid;
  v_professor uuid;
  v_aberta boolean;
  v_entrada boolean;
  v_inseridos int;
begin
  select t.id, t.professor_id, s.aberta, s.entrada_aberta
    into v_turma, v_professor, v_aberta, v_entrada
    from public.sessoes s
    join public.atividades a on a.id = s.atividade_id
    join public.turmas t on t.id = a.turma_id
   where s.id = sessao;

  if v_turma is null or not v_aberta then
    raise exception 'Esta sessão foi encerrada pelo professor.' using errcode = 'LU410';
  end if;
  if v_professor = aluno then
    raise exception 'O professor da turma não pode ser aluno dela.' using errcode = 'LU403';
  end if;
  if not v_entrada then
    raise exception 'O professor já fechou a entrada nesta sessão.' using errcode = 'LU410';
  end if;

  insert into public.membros_turma (turma_id, aluno_id) values (v_turma, aluno)
    on conflict (turma_id, aluno_id) do nothing;
  get diagnostics v_inseridos = row_count;
  return v_inseridos > 0;
end;
$$;

revoke execute on function public.entrar_na_turma_pela_sessao(uuid, uuid) from public, anon, authenticated;
grant execute on function public.entrar_na_turma_pela_sessao(uuid, uuid) to service_role;
