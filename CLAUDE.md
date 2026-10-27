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
- **Aluno: site web para o celular** em **Vite + React + TypeScript + supabase-js** (ainda não começou).
- **Login com Google** e **sessão com QR code rotativo**: decididos, ainda não implementados (detalhes na
  especificação; perguntar antes de implementar).
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

Acesso a dados: **todas as tabelas com RLS e sem políticas** para anon/authenticated. O app só fala com
Edge Functions (service_role): `gerar-atividade` e `turma-api` (POST JSON `{acao, ...}`; ações
`listar_turmas`, `criar_turma`, `publicar`, `grafo`, `simular`). Ambas com `verify_jwt = false` e a
checagem de chave pública em `supabase/functions/_shared/chave_publica.ts`. No app: `data/Supabase.kt`
(cliente único), `data/TurmaRepositorio.kt` (turma-api), `data/GeracaoRepositorio.kt`.
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
    Lista de alunos só com pseudônimos ("Aluno 01"). Linha do tempo por semana removida (sem histórico).
- Simulação (DEMONSTRAÇÃO, migration `..210000_simular_respostas.sql`): `simular_respostas(turma)`
  cria 25 pseudônimos `sim-<turma>-NN`, semente fixa (reproduzível), substitui a anterior. Modelo:
  habilidade oculta, dificuldade crescente com a profundidade (−3,0 + 4,6·prof/max), penalidade 0,3
  sem pré-requisito, acerto 0,9 / chute 0,2 / aprender 0,1, 3 respostas por questão e conceito. Com o
  teste.pdf (geração reaproveitada): ✓ Jogos e Minimax, ~ Poda e Decisões, ! Quiescência (causa raiz:
  Decisões...). Parâmetros mexidos mudam a história: confira antes de apresentar.
- Apagar turma com respostas simuladas exige `set_config('edutec.limpar_demo','on',true)` (gatilho
  só-inserção de `respostas` libera DELETE só de `demo-%`/`sim-%` com essa chave).
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
- Autenticação: `verify_jwt = false`; a função aceita só a chave pública do projeto no cabeçalho
  `apikey` (publishable ou legacy anon). Qualquer um com o APK pode gastar a cota do Gemini.
- KT (BKT) no Postgres, migration `20261021150000_bkt.sql`: `bkt_parametros` (linha padrão `'*'`:
  L0 0,3 · T 0,1 · G 0,2 · S 0,1; ajuste com pyBKT depois), `respostas` (só inserção por gatilho;
  pseudônimo, RN13), `dominio` (aluno+conceito). `registrar_resposta(aluno, conceito, correta)` aplica
  posterior + transição e devolve p_dominio e estado; `estado_dominio` usa limiares **propostos**
  0,5/0,8; view `dominio_turma` (média, mediana, moda do estado). Nada exposto a anon/authenticated.
  Por turma desde a migration `..200000` (ver acima); hoje só a simulação alimenta (não há lado do aluno).
  - Testes pgTAP (BKT, publicar, domínio por turma, simular): `npx supabase test db --linked
    supabase/tests/database` (rollback).
  - Demo: colar `supabase/tests/bkt_demo.sql` no SQL Editor (refaz os dados `demo-...`).
- Teste: `scripts/testar-geracao.sh [pdf]` (URL e chave pública em `local.properties`:
  `SUPABASE_URL`, `SUPABASE_CHAVE_PUBLICA`) → `amostras/resultado.json` (pasta fora do git).
- Testado de ponta a ponta com PDF real (2,5 MB, 5 questões mistas): ~67 s dentro da função com
  raciocínio `low` (~85 s com o padrão do modelo, `medium`, sem ganho visível de qualidade).
  Perto do limite de 150 s; com mais questões, dividir as chamadas (ver TODO).

Ainda não existe: login Google (e políticas de RLS), sessão com QR rotativo, site do aluno, edição na
Revisão, prazo/liberação na publicação, gerar a partir do grafo, abas Atividades/Materiais/Estatísticas/Turma,
histórico semanal, "Ver alunos com dificuldade".

## Entrega anterior (21/10/2026) — atividade da 1ª VA (feita no Android, tag `demo-nivel2`)

1. Protótipo hi-fi do caminho feliz (feito fora do código, no Figma).
2. Demonstrar a tecnologia: PDF real → Edge Function → Gemini → conceitos e questões.
3. Uma tela com um botão integrada à tecnologia: o botão **"Gerar atividade"** da tela Nova atividade chama a
   Edge Function e mostra as questões geradas.

Prioridade agora: o desktop no lugar do Android. Não implementar login, QR, RLS completo, offline nem o site
do aluno sem combinar antes.
