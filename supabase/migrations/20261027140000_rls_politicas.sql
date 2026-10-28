-- RLS com políticas de verdade (MVP com login).
--
-- Papel por turma:
--   professor (turmas.professor_id = auth.uid()) lê e escreve a própria turma e tudo dela;
--   aluno (membro da turma) lê a turma, as atividades publicadas SEM gabarito, as sessões e só os
--   próprios domínios, respostas e tentativas.
-- anon não lê nada. As Edge Functions continuam usando a service_role para o que exige segredo
-- (gabarito, segredo do QR, BKT), sempre depois de conferir o usuário do token.
--
-- Gabarito: atividades.questoes (com "correta", "feedback", "rubrica") NÃO tem GRANT de SELECT
-- para authenticated; o aluno lê atividades.questoes_aluno. Mesma ideia para sessoes.segredo.

-- ---------------------------------------------------------------------------
-- Funções de apoio
-- ---------------------------------------------------------------------------

create function public.eh_professor_da_sessao(sessao uuid) returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.sessoes s
      join public.atividades a on a.id = s.atividade_id
      join public.turmas t on t.id = a.turma_id
     where s.id = sessao and t.professor_id = auth.uid()
  );
$$;

revoke execute on function public.eh_professor_da_sessao(uuid) from public, anon;
grant execute on function
  public.eh_professor(uuid), public.eh_membro(uuid), public.compartilha_turma(uuid),
  public.eh_professor_da_sessao(uuid), public.gerar_codigo_convite()
  to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- perfis
-- ---------------------------------------------------------------------------

grant select on public.perfis to authenticated;
grant update (nome, avatar_url) on public.perfis to authenticated;

create policy perfis_ler on public.perfis for select to authenticated
  using (id = (select auth.uid()) or public.compartilha_turma(id));
create policy perfis_editar_o_proprio on public.perfis for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- turmas
-- ---------------------------------------------------------------------------

grant select, delete on public.turmas to authenticated;
grant insert (nome, codigo, professor_id), update (nome, codigo) on public.turmas to authenticated;

create policy turmas_ler on public.turmas for select to authenticated
  using (professor_id = (select auth.uid()) or public.eh_membro(id));
create policy turmas_criar on public.turmas for insert to authenticated
  with check (professor_id = (select auth.uid()));
create policy turmas_editar on public.turmas for update to authenticated
  using (professor_id = (select auth.uid())) with check (professor_id = (select auth.uid()));
create policy turmas_apagar on public.turmas for delete to authenticated
  using (professor_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- membros_turma (entrar só pelo convite, na aluno-api; sair ou ser removido, sim)
-- ---------------------------------------------------------------------------

grant select, delete on public.membros_turma to authenticated;

create policy membros_ler on public.membros_turma for select to authenticated
  using (aluno_id = (select auth.uid()) or public.eh_professor(turma_id));
create policy membros_sair_ou_remover on public.membros_turma for delete to authenticated
  using (aluno_id = (select auth.uid()) or public.eh_professor(turma_id));

-- ---------------------------------------------------------------------------
-- convites (só o professor da turma; o aluno usa o código pela aluno-api)
-- ---------------------------------------------------------------------------

grant select on public.convites to authenticated;
grant insert (turma_id), update (ativo) on public.convites to authenticated;

create policy convites_professor_ler on public.convites for select to authenticated
  using (public.eh_professor(turma_id));
create policy convites_professor_criar on public.convites for insert to authenticated
  with check (public.eh_professor(turma_id));
create policy convites_professor_editar on public.convites for update to authenticated
  using (public.eh_professor(turma_id)) with check (public.eh_professor(turma_id));

-- ---------------------------------------------------------------------------
-- atividades e grafo da turma (publicar é pela turma-api: copia o gabarito da geração)
-- ---------------------------------------------------------------------------

grant select (id, turma_id, geracao_id, titulo, questoes_aluno, permitir_refazer, tempo_minutos, publicada_em)
  on public.atividades to authenticated;
grant delete on public.atividades to authenticated;

create policy atividades_ler on public.atividades for select to authenticated
  using (public.eh_professor(turma_id) or public.eh_membro(turma_id));
create policy atividades_professor_apagar on public.atividades for delete to authenticated
  using (public.eh_professor(turma_id));

grant select on public.conceitos_turma to authenticated;

create policy conceitos_ler on public.conceitos_turma for select to authenticated
  using (public.eh_professor(turma_id) or public.eh_membro(turma_id));

-- ---------------------------------------------------------------------------
-- sessões e tentativas
-- ---------------------------------------------------------------------------

grant select (id, atividade_id, aberta, entrada_aberta, aberta_em, encerrada_em)
  on public.sessoes to authenticated;

create policy sessoes_ler on public.sessoes for select to authenticated
  using (exists (
    select 1 from public.atividades a
     where a.id = atividade_id and (public.eh_professor(a.turma_id) or public.eh_membro(a.turma_id))
  ));

grant select on public.tentativas to authenticated;

create policy tentativas_ler on public.tentativas for select to authenticated
  using (aluno_id = (select auth.uid()) or public.eh_professor_da_sessao(sessao_id));

-- ---------------------------------------------------------------------------
-- BKT: respostas e domínio (o aluno vê só os próprios; o professor, os da turma)
-- ---------------------------------------------------------------------------

grant select on public.respostas, public.dominio to authenticated;

create policy respostas_ler on public.respostas for select to authenticated
  using (aluno_id = (select auth.uid()) or (turma_id is not null and public.eh_professor(turma_id)));
create policy dominio_ler on public.dominio for select to authenticated
  using (aluno_id = (select auth.uid()) or (turma_id is not null and public.eh_professor(turma_id)));

-- As views são security_invoker: herdam as políticas de dominio (professor vê a turma inteira).
grant select on public.dominio_turma, public.resumo_turmas to authenticated;

-- geracoes e bkt_parametros continuam sem políticas: só a service_role.

-- ---------------------------------------------------------------------------
-- Realtime: o professor acompanha a sessão ao vivo
-- ---------------------------------------------------------------------------

-- INSERT em tentativas = aluno entrou; UPDATE com enviada_em = enviou (acertos etc.).
-- respostas: uma linha por conceito de cada questão de múltipla escolha corrigida.
-- O Realtime aplica as políticas acima a cada assinante.
alter publication supabase_realtime add table public.tentativas, public.respostas;
