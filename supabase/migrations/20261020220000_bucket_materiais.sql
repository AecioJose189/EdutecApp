-- Bucket privado para os PDFs enviados pelos professores.
-- Sem políticas em storage.objects por enquanto: só a service_role (Edge Functions) acessa.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('materiais', 'materiais', false, 20971520, array['application/pdf'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
