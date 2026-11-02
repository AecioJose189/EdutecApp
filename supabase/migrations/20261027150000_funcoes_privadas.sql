-- Funções de apoio às políticas fora do schema exposto pela API (/rest/v1/rpc): as políticas
-- precisam executá-las como authenticated, mas não há motivo para chamá-las direto.
-- ALTER ... SET SCHEMA mantém as políticas apontando para elas (a referência é pelo OID).

create schema if not exists privado;
revoke all on schema privado from public;
grant usage on schema privado to authenticated, service_role;

alter function public.eh_professor(uuid) set schema privado;
alter function public.eh_membro(uuid) set schema privado;
alter function public.compartilha_turma(uuid) set schema privado;
alter function public.eh_professor_da_sessao(uuid) set schema privado;

-- O gatilho do cadastro não precisa de EXECUTE (o Supabase dá a authenticated por padrão).
revoke execute on function public.criar_perfil() from authenticated, service_role;
