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
  e desktop prontos (ver "MVP com login" e "Desktop com login"); o site do aluno está em andamento.
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
  só como secrets das Edge Functions. No desktop, apenas a URL e a chave pública (`sb_publishable_`) do Supabase:
  **embutidas como padrão** em `data/Configuracao.kt` (o app instalado funciona sem arquivo), sobrescrevíveis por
  variáveis de ambiente ou `lumini.properties` (ver README).
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
- **Design System:** `docs/design/design-system.md` + `docs/design/tokens.json` (logos em `docs/design/logo/`).
  - **Cores só via tokens.** Cada token tem constante clara e escura em `ui/theme/Color.kt`; o `ColorScheme`
    do Material 3 sai deles em `ui/theme/Theme.kt`. Nas telas use os atalhos de `Theme.kt` (`Primaria`,
    `Tinta`, `TintaSuave`, `Superficie`, `Menta`, `EstadoRevisar`...), que seguem o tema claro/escuro.
    **Nenhuma cor literal (`Color(0x...)`, `Color.White`...) fora desses dois arquivos.** Cor nova = token novo
    no `tokens.json` e no `Color.kt` (combine com o grupo de design).
  - Dentro de `Canvas`/`drawBehind` (não são `@Composable`), leia o token antes: `val cor = TealMedio`.
  - Texto sobre `primaria` em `SobrePrimaria`; sobre `menta` (seleção, aba ativa) sempre `Tinta`.
    Par novo de texto e fundo → acrescente no `ContrasteTest` (4,5:1 nos dois temas).
  - Estado de aprendizagem sempre com **símbolo além da cor** (✓ consolidado, ~ em desenvolvimento, ! revisar,
    ? sem dados): use `SeloEstado(estado)` / `ChipEstado(estado)` de `ui/Componentes.kt` (`null` = sem dados,
    contorno tracejado).
  - Tipografia em `ui/theme/Type.kt`: Sora em títulos e números (`fontFamily = Sora`, `EstiloNumero`,
    `.comNumeros()` para algarismos tabulares; o DM Sans não tem `tnum`), DM Sans no resto (padrão do tema).
  - Forma em `ui/theme/Forma.kt`: espaços `Espaco1..Espaco12` (grade de 4 dp), cantos `FormaSm/Md/Lg/Pilula`;
    cartões comuns em `Superficie` com borda `Borda` de `EspessuraBorda`, sem sombra.
- Usar "**refazer**" (nunca "reteste") e "**domínio estimado**" (deixar claro que não é nota).
- Lógica de rede e de estado em **ViewModel**, não dentro dos Composables.
- Manter os `@Preview` nas telas (no desktop, `org.jetbrains.compose.ui.tooling.preview.Preview`, sem tamanho).
- Desktop: avisos por `LocalAvisar` (Snackbar), voltar por `TeclaVoltar { }` (tecla Esc) — ambos em
  `ui/Avisos.kt`; cursor de mão com `Modifier.maoAoPassar()` em itens clicáveis feitos à mão; Enter confirma diálogos.

## Como compilar e rodar

```bash
./gradlew :desktop:run          # professor (desktop); em dev usa SUPABASE_* do local.properties
./gradlew :desktop:test         # testes unitários (layout do grafo, causa raiz, LaTeX, semestres, contraste)
LUMINI_TEMA=escuro ./gradlew :desktop:run   # força o tema (padrão: o do sistema)
LUMINI_CAPTURAS=/tmp/cap ./gradlew :desktop:test --tests '*CapturasTelasTest*'  # @Preview em PNG, 2 temas
python3 scripts/gerar-icones.py # ícones dos instaladores (desktop/icones) a partir do símbolo
./gradlew :desktop:packageDeb   # instalador Linux; no Windows: gradlew.bat :desktop:packageMsi
./gradlew assembleDebug         # Android congelado: app/build/outputs/apk/debug/app-debug.apk
```

Versões presas (não subir uma sem as outras): Kotlin **2.1.0** (por causa do supabase-kt 3.1.4), Compose
Multiplatform **1.8.2** (a última linha que funciona com Kotlin 2.1.0), lifecycle-viewmodel-compose 2.9.1 da
JetBrains. O compilador do Kotlin 2.1.0 **não roda no JDK 25**: o daemon do Gradle usa o JDK 21
(`gradle/gradle-daemon-jvm.properties`) e o `:desktop` tem `jvmToolchain(21)` (o foojay baixa se faltar);
o runtime embutido nos instaladores também é o 21.

## Estado atual (28/10/2026)

Desktop (`:desktop`, branch `desktop`): porta completa do lado do professor, já com login Google e as
ações do MVP (ver "Desktop com login" abaixo). Trocas em relação ao Android: janela "Lumini" (1280×800, mínimo 1024×680 para não cair no
layout de celular) em `Main.kt`; diálogo "Abrir arquivo" nativo (AWT `FileDialog`, filtro .pdf) e o PDF é
enviado direto do disco, sem cópia; Snackbar no lugar do Toast; Esc no lugar do BackHandler.
Tudo abaixo vale para os dois, salvo o que fala de Android/emulador.

Lado do professor, caminho completo com dados reais: Entrar com Google → Minhas turmas → Turma › Grafo /
Atividades / Turma → Convidar alunos → Nova atividade (Origem/Formato → Gerando → Revisão → Publicar) →
Abrir sessão em sala (projeção com QR) → alunos respondem → grafo atualizado. "Simular respostas" (25
pseudônimos) fica no menu Demonstração da Turma.
**O app não mostra dados de exemplo**: `DadosExemplo`/`DadosTurmaExemplo` só nos `@Preview`. Resumo do
topo de Minhas turmas só aparece com algo real para contar; semestres vêm da data do aparelho.

Acesso a dados: ver "MVP com login" abaixo (RLS com políticas, funções com `verify_jwt = true`). No app:
`data/Supabase.kt` (cliente único, com Auth), `data/TurmaRepositorio.kt` (turma-api e membros),
`data/GeracaoRepositorio.kt`; todas as chamadas levam o JWT do professor logado.
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
- **Robustez** (`gerarComReserva`, 27/10/2026): em 503/429/falha de rede/tempo esgotado, espera 2 s e
  repete o principal uma vez; depois usa o **modelo reserva** `GEMINI_MODELO_RESERVA` (padrão
  `gemini-3.5-flash-lite`: Flash-Lite estável, plano gratuito, aceita PDF, raciocínio por
  `GEMINI_THINKING_LEVEL_RESERVA`, padrão `low`; cota gratuita é por modelo). Prazo total de **140 s**
  desde o início do pedido (`PRAZO_TOTAL_MS`): o principal nunca avança sobre os 50 s guardados para o
  reserva, nenhuma tentativa começa com menos de 20 s, e depois de um tempo esgotado não repete o
  principal (vai direto ao reserva). Sem sucesso: 503 "A IA está sobrecarregada agora (...)" (429 se
  todas foram por cota), nunca o estouro de 150 s. Erros não transitórios (chave, modelo inexistente,
  bloqueio, JSON inválido) não repetem. A resposta ganhou `reserva` e `tentativas` [{modelo, ms,
  resultado}] (o desktop ignora); o log tem "Gerado por <modelo> (RESERVA) em N ms; tentativas: ...".
  `GEMINI_FORCAR_RESERVA=true` (secret) pula o principal, só para teste — **não deixar ligado**.
  Testes: `npx deno test -A supabase/functions/_shared/gemini_test.ts` (fetch simulado).
  Medido com o teste.pdf: reserva forçado 6 s (3 questões) e 9 s (10 questões); numa chamada normal
  o principal deu 503 duas vezes (13 s e 33 s) e o reserva salvou em 6 s (56 s no total).
  Logo depois de um deploy a plataforma pode devolver 502 (HTML) uma vez: não fazer deploy durante a
  apresentação.
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
- Teste: `scripts/testar-geracao.sh [pdf]` → `amostras/resultado.json` (pasta fora do git). Usa o JWT da
  sessão do Lumini desktop (`~/.config/lumini/sessao.json`; entre no app antes) ou `SUPABASE_JWT`.
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

Testes: pgTAP (14 arquivos, 277 testes, inclusive `rls.test.sql`, `tentativas.test.sql` e `conceitos.test.sql`) e o fluxo
inteiro com dois usuários de teste contra o projeto real:
`SUPABASE_CHAVE_SECRETA=<service_role> npx deno run -A scripts/testar-fluxo-mvp.ts` (o comando
que obtém a chave está no cabeçalho do script). Cada execução cria uma turma na conta
`professor.teste@lumini-teste.example.com`.

## Desktop com login (27/10/2026, noite)

- **Login Google** (`data/Login.kt`): PKCE do Supabase Auth; o app abre o navegador do sistema
  (`Desktop.browse`, com `xdg-open`/`open`/`rundll32` de reserva e "Copiar o link" na tela) e recebe
  `?code=` num `HttpServer` do JDK na porta **fixa 3737**, em `http://localhost:3737/callback`, só
  durante o login (5 min). Não usa o servidor de retorno do supabase-kt 3.1.4, que sorteia a porta.
  O módulo `jdk.httpserver` vai no pacote.
- **Sessão** (`data/SessaoEmArquivo.kt`): `~/.config/lumini/sessao.json` (Windows `%APPDATA%\Lumini\`;
  respeita `XDG_CONFIG_HOME`), legível só pelo dono; o Auth renova o token sozinho. "Sair" (menu do
  nome/foto em Minhas turmas) apaga. **401** em qualquer chamada → `SessaoExpirada` + sessão local apagada
  → tela de entrada com aviso; os ViewModels são da janela, então com a mesma conta volta à mesma tela
  (outra conta recomeça em Minhas turmas). `ui/SessaoViewModel` segue o `sessionStatus` do Auth.
- **Turma**: "Convidar alunos" (código grande, link com Copiar, QR do link), aba **Atividades**
  (`listar_atividades`; "Abrir sessão em sala"/"Voltar à sessão"), aba **Turma** (membros com nome e
  e-mail, lidos direto de `membros_turma`+`perfis` pelo RLS, com postgrest-kt — a turma-api não tem ação
  para isso), menu **Demonstração** (simular). Aba aberta fica no `TurmaViewModel`.
- **Projeção** (`ui/ProjecaoScreen.kt` + `ProjecaoViewModel`): tela cheia (`LocalJanela`), QR novo a cada
  15 s (token vale ~40 s) com anel de contagem, "entraram/enviaram" e lista ao vivo (Realtime
  `postgres_changes` em `tentativas` filtrado por `sessao_id`, + consulta a cada 10 s de reserva), sem
  acertos na tela. Encerrar entrada; Encerrar sessão (confirmação, Enter confirma). Esc sai da tela cheia;
  de novo, sai da projeção (a sessão continua aberta até "Encerrar sessão"). "Copiar link" do QR para ensaio.
- **QR** (`ui/Qr.kt`): ZXing `core` gera a matriz, desenhada no Canvas com módulo e origem em pixels
  inteiros (com origem fracionária apareciam linhas cinza e o leitor do ZXing não lia).
- **Nova atividade**: Múltipla escolha por padrão (abertas ficam fora do KT no MVP) e título editável
  (vazio = o servidor usa os primeiros conceitos).
- **Ensaio sem celular**: `scripts/simular-aluno.ts <convite> <url-do-qr> [n] [alternativa]` faz n alunos de
  teste aceitarem o convite, lerem o QR e responderem (precisa de `SUPABASE_CHAVE_SECRETA` no ambiente).
- Testado no Xvfb com sessão de teste importada (o Google em si não dá para automatizar): criar turma →
  convite (QR lido pelo ZXing) → gerar → publicar → projeção → 3 alunos pelo script → contadores ao vivo →
  grafo com nomes → encerrar entrada/sessão → Sair → entrada; sessão revogada no servidor → 401 → entrada
  com aviso. Na noite do teste o Gemini deu 503/timeout para múltipla escolha (antes do modelo reserva):
  **gere a atividade da apresentação antes** (o resultado fica salvo e volta na hora, RN12).
- **IA sobrecarregada** (sobrecarga, tempo esgotado ou cota): a tela de erro da geração explica em
  linguagem simples e oferece "Tentar de novo" e "Gerar menos questões (3)"
  (`NovaAtividadeViewModel.gerarMenos`, `QUESTOES_SE_SOBRECARREGADA`).

## Distribuição (28/10/2026, para a avaliação por pares)

- Workflow `.github/workflows/desktop-instaladores.yml`: tag `v*` (ou à mão, quando estiver na `main`) →
  `.msi` (windows-latest), `.deb` (ubuntu-latest), `.dmg` (macos-latest = Apple Silicon e macos-15-intel)
  → GitHub Release `v<versão>` com notas em português. Versão por `-PluminiVersao` (padrão 1.0.0).
  Instaladores **não assinados** (avisos do Windows/macOS explicados no guia). `.msi` com
  `perUserInstall` (sem administrador).
- O servidor de retorno do login escuta só em `127.0.0.1:3737` (sem aviso de firewall); testado no `.deb`
  extraído e rodado no Xvfb sem `lumini.properties`: turmas e grafo reais, URL do Google gerada, retorno
  recebido (o login Google em si não dá para automatizar).
- Guia de uma página para os avaliadores: `docs/GUIA-AVALIADOR.md` (marcadores `[LINK DOS PDFs DE TESTE]`
  e `[CONTATO]`). Site do aluno: https://lumini-aluno.vercel.app. Os PDFs de teste devem ser gerados antes
  com as opções padrão (5, múltipla escolha, média) para a resposta vir na hora (RN12).

## Conceitos sem duplicata (06/11/2026, branch `fase0-conceitos`)

Problema: PDFs diferentes sobre o mesmo assunto davam o mesmo conceito com nomes diferentes no mapa
("Evento complementar" × "Evento Complementar"); `conceitos_turma` só juntava pelo nome exato.
- **Geração:** `gerar-atividade` aceita `turma_id` (opcional; outra turma → 404). Com ele, o prompt leva
  os conceitos da turma (até 150 nomes; só nomes, RN13) e pede o nome exato quando for o mesmo conceito.
  O desktop envia o da turma aberta. O cache por hash (RN12) não muda: a publicação normaliza.
- **Banco** (migration `20261106120000_conceitos_normalizados`): `normalizar_conceito` (minúsculas, sem
  acento por `translate`, espaços colapsados, trim; sem a extensão unaccent, que não é immutable);
  `conceitos_turma.nome_normalizado` (coluna gerada) + índice único (turma, nome normalizado) (criado na
  `..130000`). `publicar_atividade` troca cada nome da geração (conceitos, pré-requisitos, questões) pelo
  da turma com o mesmo nome normalizado (senão o 1º da própria geração), sem laço.
- **Possíveis duplicatas** (`possiveis_duplicatas(turma)`, pg_trgm): similaridade de trigramas ≥ 0,6 **ou**
  um nome inteiro dentro do outro ("Clássica" × "Probabilidade Clássica"). Calibrado com os nomes de
  produção: o 2º critério também pega "Fila" × "Fila Circular", por isso há `conceitos_distintos`
  ("São diferentes", `marcar_conceitos_distintos`). Nunca funde sozinho.
- **Fusão (UC05)** `fundir_conceitos(turma, origem, destino)`: security definer, confere `auth.uid()` = dono
  (a turma-api chama com o JWT do professor); o trabalho fica em `privado.fundir_conceitos_interno`.
  Move pré-requisitos (destino soma os dois; quem dependia da origem passa ao destino; laço some; ciclo →
  ED003 com o caminho), as questões das atividades (`questoes_aluno` acompanha), as respostas, e refaz o
  BKT do destino aluno a aluno em ordem (criado_em, id) com os parâmetros do destino. Registro em
  `conceitos_fusoes` (origem, destino, n_respostas, fundido_por, automática, transação).
- **Respostas continuam só-inserção**: o gatilho aceita UPDATE só com `edutec.fundir_conceitos = 'on'`
  (ligada só dentro da fusão, local à transação), mudando só `conceito`/`conceito_original`
  (`conceito_original` = o 1º nome, coalesce), para um conceito da MESMA turma e com uma linha de
  `conceitos_fusoes` desta transação (que só as funções de fusão escrevem: ligar a chave à mão não basta).
- **turma-api:** `fundir_conceitos`, `marcar_distintos`; `grafo` e `publicar` ganham `possiveis_duplicatas`.
- **Desktop:** painel do conceito → "Juntar com outro conceito…" (`ui/JuntarConceitos.kt`: parecidos no
  topo, "São diferentes", confirmação com o nome que fica e o que acontece; Enter confirma); aviso
  discreto acima do mapa com "Revisar".
- **Dados existentes** (migration `..130000_fundir_conceitos_iguais`): funde só os de nome normalizado
  IGUAL, no mais antigo do grupo. Prévia sem alterar nada: `supabase/tests/previa_fundir_conceitos.sql`
  (em 06/11: só "Calculo I", 5 fusões, 12 → 7 conceitos).
- **Aplicado em produção em 06/11/2026** (as duas migrations + deploy de `turma-api` e `gerar-atividade`),
  depois de ensaiar a `..130000` numa transação desfeita. Backup antes: `amostras/backup-calculo-i-2026-11-06.json`
  (conceitos, respostas, domínio e atividades da Calculo I). Teste de ponta a ponta numa cópia da Calculo I
  na conta de teste, com o `introducao-probabilidade.pdf` (o PDF que gerou as duplicatas): as gerações
  antigas do cache e uma nova ("difícil", 63 s, modelo reserva) ficaram nos mesmos 7 conceitos; a IA
  reutilizou os 6 nomes da turma. API no ar: fundir em turma alheia → 404, ciclo → 409 com o caminho.
- Testes: `supabase/tests/database/conceitos.test.sql` (41). Rodar local sem tocar na produção:
  `npx supabase db start` (Docker; aplica as migrations) e `npx supabase test db supabase/tests/database`.

## Design System no desktop (07/11/2026, branch `fase1-design`)

- Paleta teal/menta com tema claro e escuro (segue o sistema; `LUMINI_TEMA=claro|escuro` ou
  `-Dlumini.tema=` força). A paleta antiga (azul, roxo, creme, laranja) saiu toda.
- Fontes Sora e DM Sans (`.ttf` estáticos do Google Fonts, com as licenças OFL) em `resources/font`.
- Logo: `ui/Logo.kt` (`LogoLumini`: horizontal primaria no claro, branco no escuro) na entrada e no topo de
  Minhas turmas; símbolo como ícone da janela. Os SVGs **não são copiados**: o `processResources` do
  `:desktop` os pega de `docs/design/logo`. `carregarSvg` põe `width`/`height` do `viewBox` (sem isso o
  Skia assume 100×100 e achata o desenho).
- Ícones dos instaladores em `desktop/icones/` (`.png`/`.ico`/`.icns`, versionados), gerados por
  `scripts/gerar-icones.py` (librsvg + Pillow; símbolo sobre quadrado claro arredondado). Quando o grupo
  de design entregar o símbolo simplificado para 16–32 px: `--pequeno <svg>` e gerar de novo.
- Conferido: `ContrasteTest` (todos os pares de texto ≥ 4,5:1 e controles ≥ 3:1 nos dois temas), capturas
  dos `@Preview` nos dois temas e o `.deb` rodando no Xvfb (logo e fontes carregados do pacote).
- O `:app` Android congelado não foi tocado (continua com a paleta antiga).

## Fase 1 — fluxo do professor (27/11/2026, branch `fase1-fluxo`)

Resposta aos testes de usabilidade de novembro (`docs/feedback-testes-nov.md`).
- **Login:** a espera diz "Continue no navegador. Quando terminar, o Lumini volta sozinho." (com Cancelar).
  A página de `localhost:3737/callback` (`data/PaginaRetorno.kt`) usa o logo (em data URI: o servidor local
  fecha logo depois) e as cores dos tokens, clara/escura por `prefers-color-scheme`. Ao concluir, a janela
  vem para a frente (`trazerParaFrente` em `ui/Avisos.kt`: toFront/requestFocus; sem foco em 300 ms,
  alwaysOnTop por um instante), via `LocalTrazerParaFrente` e `SessaoViewModel.voltasDoNavegador`.
- **Próximos passos** no topo da turma (`data/ProximosPassos.kt` + `ui/PassosTurma.kt`): Convidar (quantos
  entraram) → Criar → Publicar → Abrir sessão → Ver o mapa; atual em destaque com o botão, feitos com ✓;
  some quando tudo está feito. O `TurmaViewModel` carrega atividades e membros ao abrir a turma.
- **Publicar** → aba Atividades com o aviso "Atividade publicada. Para a turma responder, abra a sessão em
  sala." + "Abrir sessão em sala" (`TurmaViewModel.avisarPublicada`; `EstadoGeracao.Publicado` leva o
  `atividadeId`). **Abrir sessão** confere os membros no servidor; sem alunos, `DialogoSemAlunos` só
  avisa ("Convidar alunos" / "Abrir mesmo assim" / "Cancelar"): **não bloqueia**, porque com
  `entrar_na_turma` o aluno entra na turma pelo próprio QR da sessão. **Encerrar sessão** → mapa com
  "Sessão encerrada · N alunos enviaram".
- **Atividades** numeradas com selo Rascunho / Publicada / Sessão aberta / Encerrada e nº de respostas
  (`AtividadePublicada.estado`). `listar_atividades` (turma-api) ganhou `numero`, `n_sessoes`, `n_enviadas`,
  `ultima_encerrada_em`. **Rascunho** = geração concluída e não publicada da turma, só enquanto o app está
  aberto: fechar a Revisão não a descarta ("Revisar e publicar"); "Nova atividade" descarta.
- **Revisão:** barra "Publicar para a turma" fixa no rodapé. **Nova atividade:** "Material da aula (PDF): a IA
  lê o material e cria as questões a partir dele."; título = nome do arquivo sem extensão (editável).
- **"Mapa do conhecimento"** no lugar de "grafo" em todo texto visível (`ABA_GRAFO` = "Mapa do conhecimento";
  nomes internos continuam `Grafo`).
- **Domínio estimado:** cada conceito mostra "baseado em N respostas"; com menos de 5
  (`ConceitoGrafo.poucosDados`) selo "poucos dados" e o estado apagado (nó com alpha, chip sem fundo).
  Legenda em linha própria acima do mapa; "O que é domínio estimado?" abre `DialogoDominioEstimado`.
- Diálogo "Juntar os dois conceitos": véu translúcido (conferido na captura `turma-juntar`).
- `contornoTracejado` não quebra mais com tamanho zero (Skia recusava traço de comprimento 0).
- **aluno-api, entrar na turma pelo QR** (`docs/contrato-api-fase2.md`): quem não é membro recebe
  `nao_matriculado` com `turma: {id, nome, codigo}`; com `entrar_na_turma: true` é matriculado e entra
  (token recente, sessão e entrada abertas) por `entrar_na_turma_pela_sessao` (migration `20261127120000`,
  só service_role; LU410/LU403). O professor da turma recebe `nao_matriculado` sem `turma`.
  pgTAP: `entrar_pela_sessao.test.sql` (8 arquivos, 119 testes). Testado de ponta a ponta com
  `supabase start` + `functions serve` locais (17 verificações, incluindo os campos novos de listar_atividades).
- **Ainda não aplicado em produção:** a migration `20261127120000` e o deploy de `aluno-api` e `turma-api`.
  O desktop funciona com a turma-api antiga (campos novos têm padrão; sem `numero`, sem "Atividade N").
- Testes: `ProximosPassosTest`, `PaginaRetornoTest`, `TurmaViewModelTest` (EDT do Swing, espera com prazo),
  `PoucosDadosTest`; capturas novas `turma-passos`, `turma-atividades`, `turma-juntar`, `passos-turma-*`.

## Fase 2 — back-end da revisão do aluno (27/11/2026, branch `fase2-backend`, sai da `fase1-fluxo`)

Contrato: `docs/contrato-api-fase2.md` (só acréscimos; o modo simulado do site ainda não tem estas ações).
- Migration `20261127130000_revisao_aluno`: `numero_da_atividade(atividade)` (ordem `publicada_em, id` na turma;
  a turma-api `listar_atividades` desempata igual), `minhas_atividades(aluno, turma?)` e
  `revisao_tentativa(tentativa, aluno)` (LU403 se não for do aluno ou não estiver enviada). Só service_role.
  **Regra anti-cola no Postgres:** com a sessão aberta, `correta`/`feedback`/`rubrica` somem do JSON; com a
  sessão encerrada, vêm completos.
- `aluno-api`: `enviar_respostas` + `questoes: [{questao_id, acertou}]` (lido de `tentativas.respostas`; null nas
  abertas); ações `minhas_atividades` e `revisao_tentativa` (LU403 → `nao_matriculado`).
- Testes: pgTAP `revisao.test.sql` (26; total 9 arquivos, 145), `scripts/testar-fluxo-mvp.ts` com o aluno 2
  (`aluno2.teste@lumini-teste.example.com`, entra pelo QR sem convite) e a revisão antes/depois de encerrar.

## Fase 2b — alternativas embaralhadas e confiança (28/11/2026, branch `fase2b-confianca`, sai da `fase2-backend`)

Ideias do grupo inspiradas no LearnHarness; só back-end e contrato (`docs/contrato-api-fase2.md`, seção "Fase 2b").
- **Embaralhar** (migration `20261128120000`): `chave_alternativa(tentativa, questao, alternativa)` = md5 dos três;
  ordenar por ela dá a ordem da tentativa (estável ao recarregar, diferente entre alunos; as 24 ordens de 4
  alternativas aparecem por igual no teste). `questoes_da_tentativa(tentativa)` = `questoes_aluno` com as
  alternativas nessa ordem; a `aluno-api` (`entrar_sessao`) usa ela. **Ids originais**: a correção não muda, e
  o site rotula A, B, C pela POSIÇÃO. `revisao_tentativa` usa a mesma ordem. Questões na ordem original.
- **Confiança** (migration `20261128130000`): `confianca` opcional ("certeza" | "acho" | "chute"; outro valor =
  null) em cada resposta de `enviar_respostas`; vai para `tentativas.respostas` (também nas abertas) e para
  `respostas.confianca` (`registrar_resposta` ganhou o 7º parâmetro opcional; chamadas antigas valem).
  **BKT igual** (testado: mesmas respostas com e sem confiança dão o mesmo domínio). `revisao_tentativa` devolve
  `confianca` por questão. `confianca_turma(turma)` (turma-api `confianca`, só o dono): total, por conceito e
  por aluno — n_respostas, n_com_confianca, erros_com_certeza, acertos_no_chute (só múltipla escolha respondida).
- Testes: pgTAP `embaralhar.test.sql` (12) e `confianca.test.sql` (15) (11 arquivos, 172); script de fluxo confere
  ordem estável, ordens diferentes entre os dois alunos (falha só por azar, ~1 em milhares), correção certa com a
  ordem trocada (gabarito lido pela chave secreta, só no script) e a confiança gravada, devolvida e nas estatísticas.
  jsonb não guarda a ordem das chaves: compare campo a campo.

## Fase 3 — Estatísticas e mapa de cada aluno (29/11/2026, branch `fase3-estatisticas`, sai da `fase2b-confianca`)

- **Banco** (migration `20261129120000_estatisticas`, só leitura, só service_role; a turma-api confere o dono):
  `dominio_reprocessado(turma, ate, incluir_simulacao)` refaz o BKT pelas respostas até `ate` (agregado
  `privado.bkt_refeito`, mesmos parâmetros e ordem `criado_em, id` do `registrar_resposta`; refeito até agora
  bate com `dominio`, testado par a par). `estatisticas_turma` (resumo + tabela por conceito, piores primeiro,
  alunos por estado com "sem dados" = membros sem resposta), `evolucao_turma` (por atividade encerrada: taxa de
  acerto das tentativas, domínio médio logo depois = BKT refeito até a última sessão encerrada dela, e por
  conceito a média e a anterior), `mapa_aluno`, `historico_aluno_conceito` (respostas em ordem com o domínio
  depois de cada uma, confiança, erros com certeza e acertos no chute). Reais = `aluno_id`; simulação =
  `sim-%` só com `incluir_simulacao` (a simulação não tem sessões: fica fora da taxa de acerto).
- **turma-api:** `estatisticas` (junta as três + `confianca_turma`), `mapa_aluno`, `historico_conceito`; `grafo`
  ganhou `alunos[].chave` (uuid do aluno real ou pseudônimo `sim-...`).
- **Desktop:** aba **Estatísticas** (`ui/EstatisticasScreen.kt`): resumo, aviso "poucos dados" com o motivo
  (< 5 alunos, < 2 atividades encerradas, < 5 respostas com confiança; `avisosPoucosDados`), tabela por conceito
  (barra empilhada com cor + símbolo, colunas de confiança com dica ao passar o mouse via `TooltipArea`; clicar
  abre o conceito no mapa), gráfico de evolução no Canvas (duas séries: forma, traço e legenda além da cor;
  valores nos pontos) e destaques por atividade (`destaques`). "Incluir simulação" só aparece se houver.
  **Mapa do aluno** (`ui/MapaAluno.kt`): clicar num aluno da lista pinta o mapa com o domínio dele
  (`GrafoTurma.doAluno`, mesmo layout; "poucos dados" abaixo de 3 respostas dele no conceito, a turma segue com 5:
  `ConceitoGrafo.minimoRespostas`), faixa "Mapa de X" + "Voltar à turma toda" (textos neutros, sem "dele/dela"),
  e o painel vira o histórico
  do conceito (gráfico do domínio resposta a resposta, ✓/✗, confiança). Estado em `TurmaViewModel`
  (`estatisticas`, `incluirSimulacao`, `alunoSelecionado`, `mapaDoAluno`, `historico`, `conceitoEmFoco`).
- Testes: pgTAP `estatisticas.test.sql` (30; 12 arquivos, 202); desktop `EstatisticasTest` e mais casos no
  `TurmaViewModelTest` (50 testes no total); `ContrasteTest` ganhou "superficie sobre tinta" (a dica). Capturas
  em `amostras/capturas/` (fora do git): `turma-estatisticas`, `estatisticas*`, `turma-mapa-do-aluno`,
  `mapa-aluno-historico`.

## Fase 4 — Revisão espaçada com flashcards (27/11/2026, branch `fase4-flashcards`, sai da `fase3-estatisticas`)

Ideia do grupo (inspirada no LearnHarness). O aluno treina no site (do Aécio) com as próprias questões
publicadas; aqui ficam o back-end, o contrato (`docs/contrato-api-fase2.md`, seção "Fase 4") e o pouco que o
professor vê.
- **Regras:** só **múltipla escolha** publicada (RN04), de atividades da turma do aluno com sessão **encerrada**
  e **nenhuma aberta** (reabrir em sala esconde os cartões: nada vaza). **Sem Gemini.** O treino **não** entra no
  BKT nem em `respostas`/`dominio`: vai para `treinos` (só inserção, gatilho como o de `respostas`).
- **Banco** (migration `20261130120000_revisao_espacada`): `cartoes` (aluno, turma, atividade, questão; caixa 1..3,
  `proxima_revisao`/`ultima_revisao` como data, acertos, erros) e `treinos` (conceitos do momento, alternativa,
  acertou, confiança, `contou_para_caixa`, `caixa_depois`). **Leitner:** acerto sobe uma caixa (máx. 3), erro volta
  à 1; intervalos 1, 3 e 7 dias. Só conta para a caixa a 1ª resposta a um cartão que está para hoje (vencido ou
  novo); as outras só são registradas. "Hoje" = dia em `America/Sao_Paulo`; as funções aceitam `hoje` (só testes).
  `cartao_id` determinístico (md5 de aluno:atividade:questão): o baralho é só leitura e a linha nasce na 1ª
  resposta. Alternativas na ordem fixa do cartão (`chave_alternativa` com o id do cartão).
  `revisao_baralho` (vencidos primeiro; depois novos de conceitos em "revisar", então "em desenvolvimento", menor
  domínio primeiro; limite 1..50, padrão 10), `revisao_responder` (LU403 cartão alheio/indisponível, LU400
  alternativa inválida), `revisao_resumo`, `treino_turma`, `treino_aluno_conceito`. Contagens por conceito usam os
  conceitos **atuais** da questão (seguem as fusões). RLS: o aluno lê só as próprias linhas; funções só service_role.
- **aluno-api:** `revisao_baralho`, `revisao_responder` (cartão inválido → `nao_matriculado`; alternativa
  inválida → `erro_interno` 400), `revisao_resumo`. **turma-api:** `treino_turma`; `estatisticas` ganhou `treino`
  e `historico_conceito` ganhou `treino: {n_treinos, acertos}`.
- **Desktop:** seção "Treino fora de sala (flashcards)" no fim da aba Estatísticas (`SecaoTreino`: alunos treinando,
  treinos em 7 dias, acerto, mais treinados, tabela por aluno com as caixas; frase "O treino não altera o domínio
  estimado: acontece fora de sala.") e no histórico do conceito do mapa do aluno a linha "Treinou N vezes (X acertos)"
  (`textoTreinoConceito`). Servidor antigo (sem `treino`) → seção vazia, sem quebrar.
- Testes: pgTAP `revisao_espacada.test.sql` (49; 13 arquivos, 251); desktop `EstatisticasTest` (+3; 54 no total);
  capturas `estatisticas-treino-fora-de-sala` e `mapa-aluno-historico`; script de fluxo etapas 8/9/9b.
  **Rodado contra a pilha local** (`npx supabase start` + `functions serve` com `SITE_URL`; geração do `teste.pdf`
  posta no cache local `geracoes` a partir de `amostras/resultado.json`, sem Gemini): fase 4 inteira ok. As 2
  verificações do Realtime (eventos ao vivo da projeção) **falham só na pilha local**: em produção passam.
  Não use o script local para conferir a projeção ao vivo.
- **Aplicado em produção em 27/11/2026:** migration `20261130120000` e deploy de `aluno-api` e `turma-api`.
  Script de fluxo inteiro contra a produção: "Tudo certo" (Realtime incluído).

## Fase 5 — Alunos com dificuldade e atividade de reforço (27/11/2026, branch `fase5-dificuldade`, sai da `fase4-flashcards`)

Os dois botões do painel do conceito no mapa da turma.
- **"Ver alunos com dificuldade"** (`ui/AlunosConceito.kt`, `DialogoAlunosConceito`): alunos **reais** no conceito,
  os piores primeiro, em "Revisar" e "Em desenvolvimento" (consolidados e sem dados recolhidos em "Ver todos";
  ninguém com dificuldade → "Nenhum aluno em 'revisar' ou 'em desenvolvimento' neste conceito."). Cada linha:
  selo de estado, domínio estimado, respostas ("poucos dados" abaixo de 3, como no mapa do aluno), erros com
  certeza, acertos no chute e treinos com flashcards. Clicar abre o mapa do aluno com o conceito selecionado e o
  histórico (`TurmaViewModel.abrirAlunoNoConceito` → `selecionarAluno` + `conceitoEmFoco`).
  turma-api `alunos_conceito` → `alunos_do_conceito` (mesmas contagens do histórico e de `treino_aluno_conceito`).
- **"Gerar atividade de reforço"**: a origem "Conceitos do mapa" da Nova atividade agora funciona (a tela passou a
  receber os conceitos do mapa; antes era lista vazia; `Conceito.estado` null = sem dados). O 1º conceito escolhido
  é o **principal**: o PDF usado é o de onde ele veio; se os escolhidos vêm de PDFs diferentes, a tela avisa
  (`textoOutroMaterial`). Origem de um conceito = primeira atividade da turma cuja geração tem o conceito (nome
  normalizado, seguindo as fusões em `conceitos_fusoes`) ou cuja questão o cita; PDF diferente = hash diferente.
  turma-api `origem_conceitos` (carregado ao abrir a Nova atividade, `NovaAtividadeViewModel.carregarOrigens`).
- **gerar-atividade** com `conceitos_foco` (JSON, máx. 8) + `turma_id`, **sem PDF**: `contexto_reforco` (Postgres)
  devolve o hash do PDF do principal e os enunciados já publicados desses conceitos (até 40); o PDF vem do Storage
  (`pdfs/<hash>.pdf`; fora dele → 409). O prompt ganha o bloco "ATIVIDADE DE REFORÇO" (foco com os nomes exatos +
  "não repita estas questões"); a validação exige que toda questão cite um conceito do foco e aceita 1–8 conceitos.
  Só nomes de conceitos e enunciados do professor vão para a IA (RN13). Passa pela Revisão e só vai para os alunos
  ao publicar (RN04). Partes puras em `_shared/reforco.ts` (testes `reforco_test.ts`).
- **Cache (RN12):** `geracoes.foco` + `foco_chave` = sha256(conceitos normalizados + enunciados já publicados).
  Geração comum busca `foco_chave is null` (nunca devolve um reforço). Publicado um reforço, o próximo pedido com
  os mesmos conceitos tem outra chave e gera questões novas. Resposta ganha `foco` e `origem`.
- Migration `20261201120000_dificuldade_reforco` (`nomes_do_conceito`, `origem_dos_conceitos`, `contexto_reforco`,
  `alunos_do_conceito`; só service_role). **Ordem do deploy: migration antes da gerar-atividade** (ela filtra por
  `foco_chave`).
- Testes: pgTAP `dificuldade_reforco.test.sql` (26; 14 arquivos, 277); `revisao_espacada.test.sql` passou a
  contar só os treinos da própria turma (falhava com treinos de outras turmas no banco). Deno `reforco_test.ts` (5).
  Desktop `DificuldadeTest`, `ReforcoTextosTest` e mais 2 no `TurmaViewModelTest` (66 no total). Capturas
  `alunos-conceito*` e `nova-atividade` (reforço com PDFs diferentes). Script de fluxo: etapa 10. No local, o
  reforço vem de uma geração posta no cache local com a chave calculada (sem Gemini); a 1ª execução em produção
  chama o Gemini.
- **Honestidade da interface** (nada de botão que só avisa "próxima versão"): saíram a aba "Materiais", o botão
  "Criar questões manualmente" (fora do escopo), o cartão "atividades aguardam sua revisão" de Minhas turmas (o app
  nunca passava dados para ele) e o seletor de semestre (não filtrava nada: turmas não têm semestre no banco;
  `semestresRecentes()` e o teste ficam para quando tiverem; o cartão diz "alunos nas suas turmas"). A chave
  "Permitir refazer" saiu: `atividades.permitir_refazer` é gravado (sempre true pelo desktop) mas nada o lê. O texto
  fixo `TEXTO_REFAZER` diz o que acontece de verdade: um envio por sessão (`unique (sessao_id, aluno_id)`); para
  refazer, o professor abre outra sessão da atividade (mesmas questões, alternativas em outra ordem, respostas de
  novo no domínio estimado); encerrada a sessão, as de múltipla escolha vão para os flashcards. Sem "variantes".
- **Ainda não aplicado em produção:** migration `20261201120000` e deploy de `turma-api` e `gerar-atividade`.

Ainda não existe: correção das abertas, edição na Revisão, prazo/liberação na publicação, aba Materiais,
histórico semanal.

## Entrega anterior (21/10/2026) — atividade da 1ª VA (feita no Android, tag `demo-nivel2`)

1. Protótipo hi-fi do caminho feliz (feito fora do código, no Figma).
2. Demonstrar a tecnologia: PDF real → Edge Function → Gemini → conceitos e questões.
3. Uma tela com um botão integrada à tecnologia: o botão **"Gerar atividade"** da tela Nova atividade chama a
   Edge Function e mostra as questões geradas.

Prioridade agora: MVP com login para a avaliação por pares (28/10, 18:30). Não mexer em `web-aluno/` (é do
Aécio) nem mudar o contrato sem combinar.
