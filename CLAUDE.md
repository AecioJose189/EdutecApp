# EDUTEC — contexto para o Claude Code

App Android para o ensino superior que combina **Knowledge Tracing (KT)** e **Learning Analytics** para o professor.
O professor envia material em PDF, a IA gera atividades, os alunos respondem e o KT estima o domínio de cada
conceito. O professor vê o grafo de conhecimento da turma e de cada aluno.

A especificação completa (casos de uso, regras de negócio, telas) fica fora do repositório. Este arquivo
resume o que importa para o código. Na dúvida sobre uma regra, pergunte antes de inventar.

## Stack (decisões fechadas — não trocar sem perguntar)

- **App:** um único app Android, **Kotlin + Jetpack Compose (Material 3)**, com dois perfis: professor (tablet,
  horizontal) e aluno (celular, vertical). Layout adaptativo por largura de janela, não por orientação travada.
- **Back-end: 100% Supabase** — Auth, Postgres (com RLS por turma), Storage (PDFs), Realtime e
  **Edge Functions em TypeScript**. Não existe servidor Python nesta versão.
- **IA: API do Gemini, plano gratuito** (modelos Flash), chamada **somente** de Edge Functions.
  Todas as chamadas ao Gemini ficam isoladas num único módulo, para poder trocar de provedor depois.
- **KT:** BKT implementado como função no Postgres (PL/pgSQL), com parâmetros guardados numa tabela.
- **Cliente Supabase no app:** supabase-kt.
- **Offline (futuro):** Room + WorkManager.

## Regras que valem para todo código

- **Nenhuma chave secreta no app nem no git.** A chave do Gemini e a `service_role` do Supabase ficam só como
  secrets das Edge Functions. No app, apenas a URL e a chave `anon` do Supabase, lidas de `local.properties`
  (que está no `.gitignore`) via `BuildConfig`.
- **Privacidade (RN13):** nenhum dado pessoal de aluno (nome, matrícula, e-mail) é enviado ao Gemini.
- **RN04:** nada gerado por IA chega ao aluno sem aprovação do professor.
- **RN07:** toda questão precisa estar ligada a pelo menos um conceito.
- Limites do plano gratuito: Edge Functions com 2 s de CPU por pedido e 150 s de duração; Gemini com ~10
  pedidos/min. Tarefas longas são divididas (ex.: gerar uma questão por chamada).

## Convenções do código

- Nomes de classes, funções, variáveis e textos da interface em **português**, como no código existente
  (`Turma`, `Conceito`, `EstadoConceito`, `NovaAtividadeScreen`...).
- Pacote base `com.edutec.app`: `data/` (modelos e acesso a dados) e `ui/` (telas, componentes, `theme/`).
- Seguir o visual já existente (cores em `ui/theme/Color.kt`, cantos grandes, abas em pílula). Estado de
  aprendizagem sempre com **símbolo além da cor**: ✓ consolidado, ~ em desenvolvimento, ! revisar, ? sem dados.
- Usar "**refazer**" (nunca "reteste") e "**domínio estimado**" (deixar claro que não é nota).
- Lógica de rede e de estado em **ViewModel**, não dentro dos Composables.
- Manter os `@Preview` de tablet e celular nas telas.

## Como compilar e rodar

```bash
./gradlew assembleDebug   # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # instala no emulador/celular conectado
```

## Estado atual (20/10/2026)

Pronto, com dados de exemplo fixos: Minhas turmas, Turma › Grafo e Nova atividade (lado do professor).

Nova atividade integrada à função (item 3 da entrega), testada no emulador (celular e tablet):
- "Gerar atividade" envia o PDF de `files/materiais/` via `data/GeracaoRepositorio` (supabase-kt
  3.1.4 functions + Ktor OkHttp; timeout 160 s). Com origem "Conceitos do grafo" fica desabilitado
  com o motivo. Quantidade padrão 5, máximo 10.
- `ui/NovaAtividadeViewModel`: Ocioso → Gerando(segundos) → Concluido | Erro (cota, IA sobrecarregada,
  tempo esgotado, sem conexão, outro). Etapa 3 em `ui/GerandoAtividade.kt`, etapa 4 (Revisão, só
  leitura) em `ui/RevisaoAtividade.kt`; LaTeX vira texto legível em `ui/TextoLatex.kt` (com testes).
- O estado do formulário vive em `remember` no topo da `NovaAtividadeScreen` (sobrevive a
  Gerando/Erro/Revisão, mas não à recriação da Activity, assim como a navegação do `EdutecApp`).
- supabase-kt ≥ 3.2 exige Kotlin ≥ 2.2: não atualizar sem subir o Kotlin.

Back-end (Supabase, projeto `vuxnyjkykftecqgznswg`, em `supabase/`):
- Edge Function `gerar-atividade` (deploy feito): recebe PDF multipart (até 20 MB) + `quantidade`,
  `formato`, `dificuldade`; salva no bucket privado `materiais`; pede conceitos e questões ao Gemini
  numa única chamada (TODO: uma questão por chamada) e valida a resposta (RN07 etc.).
- Gemini isolado em `supabase/functions/_shared/gemini.ts` (REST `generateContent`, modelo por
  `GEMINI_MODEL`, padrão `gemini-3.8-flash`; raciocínio por `GEMINI_THINKING_LEVEL`, padrão `low`).
- Autenticação: `verify_jwt = false`; a função aceita só a chave pública do projeto no cabeçalho
  `apikey` (publishable ou legacy anon). Qualquer um com o APK pode gastar a cota do Gemini.
- Teste: `scripts/testar-geracao.sh [pdf]` (URL e chave pública em `local.properties`:
  `SUPABASE_URL`, `SUPABASE_CHAVE_PUBLICA`) → `amostras/resultado.json` (pasta fora do git).
- Testado de ponta a ponta com PDF real (2,5 MB, 5 questões mistas): ~67 s dentro da função com
  raciocínio `low` (~85 s com o padrão do modelo, `medium`, sem ganho visível de qualidade).
  Perto do limite de 150 s; com mais questões, dividir as chamadas (ver TODO).

Ainda não existe: login, RLS, lado do aluno, edição na Revisão, etapa Publicar, gerar a partir do grafo.

## Próxima entrega (21/10/2026) — atividade da 1ª VA

1. Protótipo hi-fi do caminho feliz (feito fora do código, no Figma).
2. Demonstrar a tecnologia: PDF real → Edge Function → Gemini → conceitos e questões.
3. Uma tela com um botão integrada à tecnologia: o botão **"Gerar atividade"** da tela Nova atividade chama a
   Edge Function e mostra as questões geradas.

Prioridade: fazer o item 3 funcionar de ponta a ponta com o mínimo necessário. Não implementar login,
RLS completo, offline nem lado do aluno agora.
