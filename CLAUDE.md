# Lumini (projeto EDUTEC) — contexto para o Claude Code

**Lumini** é uma plataforma para o ensino superior que combina **Knowledge Tracing (KT)** e **Learning Analytics**
para o professor. O professor envia material em PDF, a IA gera atividades, os alunos respondem e o KT estima o
domínio de cada conceito. O professor vê o grafo de conhecimento da turma e de cada aluno.

Nome: em todo texto visível o app se chama **"Lumini"**. Pacotes (`com.edutec.app`), classes (`EdutecApp`,
`EdutecTheme`...), tabelas, projeto Supabase e repositório continuam com "edutec": não renomear.

A especificação completa (casos de uso, regras de negócio, telas) fica fora do repositório. Este arquivo
resume o que importa para o código. Na dúvida sobre uma regra, pergunte antes de inventar.

## Stack (decisões fechadas — não trocar sem perguntar)

Plataforma decidida com o professor da disciplina:

- **Professor: app desktop** em **Kotlin + Compose Multiplatform (Desktop/JVM, Material 3)**, módulo `:desktop`
  (Linux, Windows, macOS). Usa o layout de tela larga (o antigo de tablet) e se adapta ao redimensionar a janela.
- **Aluno: site web para o celular** em **Vite + React + TypeScript + supabase-js**, pasta `web-aluno/`
  (branch `web-aluno`, feito em paralelo pelo Aécio; briefing em `docs/briefing-web-aluno.md`).
- **Login com Google** (Supabase Auth, configurado no painel) e **sessão com QR code rotativo**: back-end
  pronto (ver "MVP com login"); falta o login no desktop.
- **Papel por turma:** quem cria a turma é o professor dela; quem aceita o convite é aluno dela. A mesma
  conta pode ser professor numa turma e aluno em outra (nunca as duas na mesma).
- **Android congelado** na tag `demo-nivel2` (módulo `:app`, Kotlin + Jetpack Compose). Não evoluir; só manter
  o build funcionando. O código do desktop começou como cópia adaptada dele.
- **Back-end: 100% Supabase** — Auth, Postgres (com RLS por turma), Storage (PDFs), Realtime e
  **Edge Functions em TypeScript**. Não existe servidor Python nesta versão.
- **IA: API do Gemini, plano gratuito** (modelos Flash), chamada **somente** de Edge Functions.
  Todas as chamadas ao Gemini ficam isoladas num único módulo, para poder trocar de provedor depois.
- **KT:** BKT implementado como função no Postgres (PL/pgSQL), com parâmetros guardados numa tabela.
- **Cliente Supabase:** supabase-kt no desktop (e no Android congelado); supabase-js no site do aluno.
- **Offline (futuro):** era Room + WorkManager no Android; rever para o desktop.

## Regras que valem para todo código

- **Contrato da API do aluno:** `docs/contrato-api-mvp.md` (ações, campos e códigos de erro da `aluno-api`).
  Campos novos podem ser acrescentados; renomear ou remover, não — combinar antes com quem faz o site.
- **Nenhuma chave secreta no app, no site nem no git.** A chave do Gemini e a `service_role` do Supabase ficam
  só como secrets das Edge Functions. No desktop, apenas a URL e a chave pública (publishable/anon) do Supabase,
  lidas de variáveis de ambiente ou de `lumini.properties` fora do git (`data/Configuracao.kt`; ver README).
  O Android lia de `local.properties` via `BuildConfig`.
- **Privacidade (RN13):** nenhum dado pessoal de aluno (nome, matrícula, e-mail) é enviado ao Gemini.
- **RN04:** nada gerado por IA chega ao aluno sem aprovação do professor.
- **RN07:** toda questão precisa estar ligada a pelo menos um conceito.
- Limites do plano gratuito: Edge Functions com 2 s de CPU por pedido e 150 s de duração; Gemini com ~10
  pedidos/min. Tarefas longas são divididas (ex.: gerar uma questão por chamada).

## Convenções do código

- Nomes de classes, funções, variáveis e textos da interface em **português**, como no código existente
  (`Turma`, `Conceito`, `EstadoConceito`, `NovaAtividadeScreen`...).
- Pacote base `com.edutec.app`: `data/` (modelos e acesso a dados) e `ui/` (telas, componentes, `theme/`).
  Desktop em `desktop/src/main/kotlin/`, Android (congelado) em `app/src/main/java/`.
- Seguir o visual já existente (cores em `ui/theme/Color.kt`, cantos grandes, abas em pílula). Estado de
  aprendizagem sempre com **símbolo além da cor**: ✓ consolidado, ~ em desenvolvimento, ! revisar, ? sem dados.
- Usar "**refazer**" (nunca "reteste") e "**domínio estimado**" (deixar claro que não é nota).
- Lógica de rede e de estado em **ViewModel**, não dentro dos Composables.
- Manter os `@Preview` nas telas (no desktop, `org.jetbrains.compose.ui.tooling.preview.Preview`, sem tamanho).
- Desktop: avisos por `LocalAvisar` (Snackbar), voltar por `TeclaVoltar { }` (tecla Esc) — ambos em
  `ui/Avisos.kt`; cursor de mão com `Modifier.maoAoPassar()` em itens clicáveis feitos à mão; Enter confirma diálogos.

## Como compilar e rodar

```bash
./gradlew :desktop:run          # professor (desktop); em dev usa SUPABASE_* do local.properties
./gradlew :desktop:test         # testes unitários (layout do grafo, causa raiz, LaTeX, semestres)
./gradlew :desktop:packageDeb   # instalador Linux; no Windows: gradlew.bat :desktop:packageMsi
./gradlew assembleDebug         # Android congelado: app/build/outputs/apk/debug/app-debug.apk
```

Versões presas (não subir uma sem as outras): Kotlin **2.1.0** (por causa do supabase-kt 3.1.4), Compose
Multiplatform **1.8.2** (a última linha que funciona com Kotlin 2.1.0), lifecycle-viewmodel-compose 2.9.1 da
JetBrains. O compilador do Kotlin 2.1.0 **não roda no JDK 25**: o daemon do Gradle usa o JDK 21
(`gradle/gradle-daemon-jvm.properties`) e o `:desktop` tem `jvmToolchain(21)` (o foojay baixa se faltar);
o runtime embutido nos instaladores também é o 21.

## Estado atual (27/10/2026)

Desktop (`:desktop`, branch `desktop`): porta completa do lado do professor, testada de ponta a ponta com
`:desktop:run` (criar turma → Nova atividade → PDF → Gerar → Revisão → Publicar → Simular → grafo) e com o
pacote gerado. Trocas em relação ao Android: janela "Lumini" (1280×800, mínimo 1024×680 para não cair no
layout de celular) em `Main.kt`; diálogo "Abrir arquivo" nativo (AWT `FileDialog`, filtro .pdf) e o PDF é
enviado direto do disco, sem cópia; Snackbar no lugar do Toast; Esc no lugar do BackHandler.
Tudo abaixo vale para os dois, salvo o que fala de Android/emulador.

Lado do professor, caminho completo com dados reais: Minhas turmas → Turma › Grafo → Nova atividade
(Origem/Formato → Gerando → Revisão → Publicar) → Simular respostas (demonstração) → grafo atualizado.
**O app não mostra dados de exemplo**: `DadosExemplo`/`DadosTurmaExemplo` só nos `@Preview`. Resumo do
topo de Minhas turmas só aparece com algo real para contar; semestres vêm da data do aparelho.

Acesso a dados: ver "MVP com login" abaixo (RLS com políticas, funções com `verify_jwt = true`).
**O desktop ainda manda só a chave pública**: até ganhar o login Google, as chamadas dele à turma-api e à
gerar-atividade voltam 401. No app: `data/Supabase.kt` (cliente único), `data/TurmaRepositorio.kt`
(turma-api), `data/GeracaoRepositorio.kt`.
- Turmas persistidas (migration `..180000_turmas.sql`); `ui/NavegacaoViewModel` carrega/cria pela API,
  com carregando/erro + "Tentar de novo" em Minhas turmas.
- Publicar (etapa 5, barra no fim da Revisão): `publicar_atividade` (PL/pgSQL, migration
  `..190000_atividades.sql`) grava `atividades` (questões copiadas de `geracoes`; `permitir_refazer` e
  `tempo_minutos` do formulário; TODO prazo/liberação) e junta os conceitos em `conceitos_turma`
  (sem duplicar), rejeitando ciclo (RN03, erro ED003) — tudo numa transação. Mesma geração duas vezes
  na turma → 409. `gerar-atividade` devolve `geracao_id`. Depois de publicar, volta para a Turma.
- Grafo (migration `..200000_dominio_por_turma.sql`): `respostas`/`dominio` com `turma_id` (nulo = dados
  `demo-` antigos, ainda funcionam); `registrar_resposta(aluno, conceito, correta, turma default null)`;
  `dominio_turma` por turma+conceito (média, mediana, moda, nº respostas, nº em revisar);
  `resumo_turmas` para os cartões. `ui/TurmaViewModel` carrega o grafo.
  - Tela Turma: sem atividade publicada → estado vazio; publicada sem respostas → nós cinza "?".
    Layout em camadas em `ui/LayoutGrafo.kt` (profundidade = maior caminho de pré-requisitos; ordem
    por baricentro), causa raiz em `data/GrafoTurma.kt` (pré-requisito direto/indireto de menor
    média, se não consolidado), ambos com testes unitários. Tamanho do nó = nº de respostas.
    Lista de alunos: simulação por pseudônimo ("Aluno 01"); alunos reais pelo nome (turma-api `grafo`). Linha do tempo por semana removida (sem histórico).
- Simulação (DEMONSTRAÇÃO, migration `..210000_simular_respostas.sql`): `simular_respostas(turma)`
  cria 25 pseudônimos `sim-<turma>-NN`, semente fixa (reproduzível), substitui a anterior. Modelo:
  habilidade oculta, dificuldade crescente com a profundidade (−3,0 + 4,6·prof/max), penalidade 0,3
  sem pré-requisito, acerto 0,9 / chute 0,2 / aprender 0,1, 3 respostas por questão e conceito. Com o
  teste.pdf (geração reaproveitada): ✓ Jogos e Minimax, ~ Poda e Decisões, ! Quiescência (causa raiz:
  Decisões...). Parâmetros mexidos mudam a história: confira antes de apresentar.
- Apagar turma com respostas simuladas exige `set_config('edutec.limpar_demo','on',true)` (gatilho
  só-inserção de `respostas` libera DELETE só de `demo-%`/`sim-%` com essa chave). Turma com respostas
  de alunos reais não pode ser apagada (o gatilho barra a cascata) — de propósito, por enquanto.
- Testes de interface do caminho da demo: `./gradlew connectedDebugAndroidTest` (emulador ligado;
  ao terminar, o AGP desinstala o app — rode `installDebug` de novo).

Nova atividade integrada à função (item 3 da entrega), testada no emulador (celular e tablet):
- "Gerar atividade" envia o PDF de `files/materiais/` via `data/GeracaoRepositorio` (supabase-kt
  3.1.4 functions + Ktor OkHttp; timeout 160 s). Com origem "Conceitos do grafo" fica desabilitado
  com o motivo. Quantidade padrão 5, máximo 10.
- `ui/NovaAtividadeViewModel`: Ocioso → Gerando(segundos) → Concluido | Erro (cota, IA sobrecarregada,
  tempo esgotado, sem conexão, outro). Etapa 3 em `ui/GerandoAtividade.kt`, etapa 4 (Revisão, só
  leitura) em `ui/RevisaoAtividade.kt`; LaTeX vira texto legível em `ui/TextoLatex.kt` (com testes).
- Revisão avisa quando o resultado foi reaproveitado, com "Gerar novas questões" (forcar_nova).
- Girar a tela não perde nada: navegação e turmas no `ui/NavegacaoViewModel`, geração/publicação no
  `NovaAtividadeViewModel`, grafo/simulação no `TurmaViewModel` (escopo da Activity), formulário em
  `rememberSaveable`.
- Turma sem dados (`Turma.semDados`, ex.: criada agora) mostra o estado vazio com primeiros passos;
  a Nova atividade dela abre em "Material em PDF". Sem marcadores "[...]" do wireframe.
- supabase-kt ≥ 3.2 exige Kotlin ≥ 2.2: não atualizar sem subir o Kotlin.

Back-end (Supabase, projeto `vuxnyjkykftecqgznswg`, em `supabase/`):
- Edge Function `gerar-atividade` (deploy feito): recebe PDF multipart (até 20 MB) + `quantidade`,
  `formato`, `dificuldade`, `forcar_nova`; pede conceitos e questões ao Gemini numa única chamada
  (TODO: uma questão por chamada) e valida a resposta (RN07 etc.).
- RN12 (cada PDF é processado uma vez): o PDF é salvo como `materiais/pdfs/<sha256>.pdf` e o
  resultado vai para a tabela `geracoes` (RLS ligado, sem políticas: só a service_role acessa).
  Mesmo hash + parâmetros → devolve o salvo na hora (`reaproveitado: true`, `gerado_em`), sem chamar o
  Gemini (<1 s na função; 1,3–3,4 s no script, quase tudo envio do PDF e arranque a frio).
  `forcar_nova=true` gera de novo (fica a linha mais recente).
- Gemini isolado em `supabase/functions/_shared/gemini.ts` (REST `generateContent`, modelo por
  `GEMINI_MODEL`, padrão `gemini-3.8-flash`; raciocínio por `GEMINI_THINKING_LEVEL`, padrão `low`).
- Autenticação: `verify_jwt = true` e só usuários logados (`_shared/auth.ts`); `geracoes` continua um
  cache por PDF sem dono, acessado pela service_role.
- KT (BKT) no Postgres, migration `20261021150000_bkt.sql`: `bkt_parametros` (linha padrão `'*'`:
  L0 0,3 · T 0,1 · G 0,2 · S 0,1; ajuste com pyBKT depois), `respostas` (só inserção por gatilho;
  pseudônimo, RN13), `dominio` (aluno+conceito). `registrar_resposta(aluno, conceito, correta)` aplica
  posterior + transição e devolve p_dominio e estado; `estado_dominio` usa limiares **propostos**
  0,5/0,8; view `dominio_turma` (média, mediana, moda do estado). Nada exposto a anon/authenticated.
  Por turma desde a migration `..200000` (ver acima); hoje só a simulação alimenta (não há lado do aluno).
  - Testes pgTAP (BKT, publicar, domínio por turma, simular): `npx supabase test db --linked
    supabase/tests/database` (rollback).
  - Demo: colar `supabase/tests/bkt_demo.sql` no SQL Editor (refaz os dados `demo-...`).
- Teste: `scripts/testar-geracao.sh [pdf]` → `amostras/resultado.json` (pasta fora do git). **Desatualizado
  desde o verify_jwt** (manda só a chave pública → 401); a geração agora é testada pelo
  `scripts/testar-fluxo-mvp.ts`.
- Testado de ponta a ponta com PDF real (2,5 MB, 5 questões mistas): ~67 s dentro da função com
  raciocínio `low` (~85 s com o padrão do modelo, `medium`, sem ganho visível de qualidade).
  Perto do limite de 150 s; com mais questões, dividir as chamadas (ver TODO).

## MVP com login (back-end pronto em 27/10/2026, para a avaliação por pares de 28/10, 18:30)

Fluxo: professor (desktop) loga com Google → cria turma → gera convite → publica atividade → abre sessão
com QR rotativo; aluno (site) loga com Google → aceita convite → lê o QR → responde → BKT atualiza;
professor vê o grafo da turma (e a sessão ao vivo), aluno vê o próprio grafo.

Migrations (`supabase/migrations/20261027*`):
- `..120000_perfis_membros_convites`: `perfis` (id = auth.users; nome, email, avatar; gatilho
  `criar_perfil` no cadastro, nome de `full_name`/`name` do Google); `turmas.professor_id` (padrão
  `auth.uid()`); `membros_turma` (gatilho impede o professor de ser aluno da própria turma, LU403);
  `convites` (código de 6 caracteres sem 0/O/1/I, um ativo por turma). Apagou as turmas de teste sem
  professor; ficaram `geracoes` e os dados `demo-` do BKT.
- `..130000_sessoes_tentativas`: `atividades.titulo` (padrão: dois primeiros conceitos + "e outros") e
  `atividades.questoes_aluno` (coluna gerada, **sem gabarito**: id da questão = posição "1", "2"...,
  alternativas `a`, `b`...); `sessoes` (aberta, entrada_aberta, `segredo` do HMAC, aberta_em,
  encerrada_em; uma aberta por atividade); `tentativas` (uma por aluno e sessão; prazo_em, acertos,
  totais, `respostas` jsonb, `aluno_nome` para o painel ao vivo); `respostas`/`dominio` com `aluno_id`
  (uuid; `aluno_pseudonimo` = o uuid em texto, RN13) e `respostas.tentativa_id`;
  `registrar_resposta(aluno, conceito, correta, turma, aluno_id, tentativa)`;
  `enviar_tentativa(tentativa, aluno, respostas)` corrige no Postgres, numa transação: múltipla escolha →
  `registrar_resposta` por conceito; sem resposta conta no total mas não entra no BKT; abertas ficam
  `aguardando_correcao`, fora do KT. Erros LU403/LU409/LU410/LU408 (prazo + 30 s de tolerância).
- `..140000_rls_politicas`: professor lê/escreve só as próprias turmas e tudo delas; aluno lê turmas de
  que é membro, atividades **sem a coluna `questoes`** (sem GRANT; lê `questoes_aluno`), sessões (sem a
  coluna `segredo`) e só os próprios domínios/respostas/tentativas; perfis: o próprio e quem divide turma
  (professor↔aluno, não aluno↔aluno). anon: nada. Realtime: `tentativas` e `respostas` na publicação.
- `..150000_funcoes_privadas`: `eh_professor`, `eh_membro`, `compartilha_turma`,
  `eh_professor_da_sessao` (security definer, usadas pelas políticas) no schema `privado`, fora da API.

Edge Functions (todas com `verify_jwt = true`; usuário por `getUser` em `_shared/auth.ts`; a service_role
só depois de conferir o papel na turma):
- `turma-api` (professor; erro `{erro: "texto"}`): `listar_turmas`, `criar_turma`, `criar_convite`
  (link `<SITE_URL>/convite/<codigo>`), `publicar` (+ `titulo` opcional), `listar_atividades`,
  `abrir_sessao`, `token_qr` (URL `<SITE_URL>/s/<token>`, `expira_em`, janela 20 s), `encerrar_entrada`,
  `encerrar_sessao`, `sessao` (participantes), `grafo` (alunos reais pelo nome; simulação "Aluno NN"),
  `simular`. Turma/atividade/sessão de outro professor → 404.
- `aluno-api` (site): exatamente o contrato. Pedido malformado usa o código do contrato mais próximo
  (token ilegível → `qr_expirado`; turma/tentativa inválida → `nao_matriculado`; ação desconhecida →
  `erro_interno` 400). Sem JWT a plataforma responde 401 antes da função (corpo da plataforma, não o do
  contrato): o site deve tratar **qualquer 401** como `nao_autenticado`.
- QR (`_shared/qr.ts`, testes em `qr_test.ts`): token `<sessao_id b64url>.<janela b36>.<HMAC-SHA256 de
  "sessao:janela" truncado em 96 bits>`, ~46 caracteres. Vale a janela atual e a anterior (~40 s) para
  entrar; com tentativa já criada, um token velho válido devolve a mesma tentativa (recarregar a página).
- Secret `SITE_URL` (hoje `http://localhost:5173`; trocar pela URL da Vercel quando houver:
  `npx supabase secrets set SITE_URL=...`).
- Ao vivo no desktop: Realtime `postgres_changes` em `public.tentativas` com filtro `sessao_id=eq.<id>`
  e o JWT do professor (INSERT = entrou; UPDATE com `enviada_em` = enviou).

Testes: pgTAP (6 arquivos, 67 testes, inclusive `rls.test.sql` e `tentativas.test.sql`) e o fluxo
inteiro com dois usuários de teste contra o projeto real:
`SUPABASE_CHAVE_SECRETA=<service_role> npx deno run -A scripts/testar-fluxo-mvp.ts` (o comando
que obtém a chave está no cabeçalho do script). Cada execução cria uma turma na conta
`professor.teste@lumini-teste.example.com`.

Ainda não existe: login Google no desktop (e o desktop usando as ações novas: convite, sessão, QR, ao
vivo), correção das abertas, edição na Revisão, prazo/liberação na publicação, gerar a partir do grafo,
abas Atividades/Materiais/Estatísticas/Turma, histórico semanal, "Ver alunos com dificuldade".

## Entrega anterior (21/10/2026) — atividade da 1ª VA (feita no Android, tag `demo-nivel2`)

1. Protótipo hi-fi do caminho feliz (feito fora do código, no Figma).
2. Demonstrar a tecnologia: PDF real → Edge Function → Gemini → conceitos e questões.
3. Uma tela com um botão integrada à tecnologia: o botão **"Gerar atividade"** da tela Nova atividade chama a
   Edge Function e mostra as questões geradas.

Prioridade agora: MVP com login para a avaliação por pares (28/10, 18:30). Não mexer em `web-aluno/` (é do
Aécio) nem mudar o contrato sem combinar.
