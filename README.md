# Lumini

Plataforma de **Knowledge Tracing** e **Learning Analytics** para o ensino superior (projeto EDUTEC).

- **Instaladores do app do professor** (Windows, Linux, macOS): [Releases](https://github.com/AecioJose189/EdutecApp/releases/latest)
- **Para testar:** [Guia do avaliador](docs/GUIA-AVALIADOR.md) · site do aluno: https://lumini-aluno.vercel.app

- **Professor:** app desktop em Compose Multiplatform, módulo [`desktop/`](#app-do-professor-desktop) (Linux, Windows, macOS).
- **Aluno:** site web para o celular (Vite + React + TypeScript + supabase-js), https://lumini-aluno.vercel.app (branch `web-aluno`).
- **Back-end:** Supabase (Postgres, Edge Functions, Storage), em `supabase/`.
- **Android:** o app original do professor (módulo `app/`) está **congelado** na tag `demo-nivel2`.
  O resto deste README depois da seção do desktop descreve esse app.

## App do professor (desktop)

Precisa de JDK 21 (o Gradle baixa um se não houver). O código fica em
`desktop/src/main/kotlin/com/edutec/app/` (cópia adaptada do Android: mesmas telas, ViewModels e acesso ao Supabase).

### Configuração (URL e chave pública do Supabase)

O app já vem com a URL e a chave **pública** (`sb_publishable_...`) do projeto Lumini
(`data/Configuracao.kt`), então o instalado funciona sem arquivo extra. Para apontar para outro projeto,
o app procura, nesta ordem (o primeiro que tiver o valor vence):

1. variáveis de ambiente `SUPABASE_URL` e `SUPABASE_CHAVE_PUBLICA`;
2. o arquivo indicado na variável `LUMINI_CONFIG`;
3. `lumini.properties` na pasta de configuração do usuário:
   - Linux: `~/.config/lumini/lumini.properties`
   - Windows: `%APPDATA%\Lumini\lumini.properties`
   - macOS: `~/Library/Application Support/Lumini/lumini.properties`
4. `lumini.properties` na pasta atual.

Conteúdo do arquivo (só a chave **pública**, publishable ou anon; nunca a `service_role` nem `sb_secret_`):

```properties
SUPABASE_URL=https://<projeto>.supabase.co
SUPABASE_CHAVE_PUBLICA=sb_publishable_...
```

Em desenvolvimento, `./gradlew :desktop:run` já usa os valores do `local.properties` (o mesmo do Android)
se as variáveis de ambiente não estiverem definidas.

### Login

"Entrar com Google" abre o navegador do sistema; o retorno chega ao Lumini em `http://localhost:3737/callback`
(porta fixa, só no loopback e só durante o login; vale igual no app instalado). No painel do Supabase, **Authentication → URL Configuration →
Redirect URLs** precisa ter `http://localhost:3737/**` (ou exatamente `http://localhost:3737/callback`).
A sessão fica em `~/.config/lumini/sessao.json` (Windows: `%APPDATA%\Lumini\sessao.json`) e é renovada sozinha;
"Sair" (menu do nome no topo de Minhas turmas) apaga o arquivo.

Para ensaiar sem celular: na projeção da sessão, "Copiar link" do QR e, em até ~40 s,
`npx deno run -A scripts/simular-aluno.ts <código-do-convite> <link-do-qr> 3` (precisa de
`SUPABASE_CHAVE_SECRETA` no ambiente; ver o cabeçalho do script).

### Rodar, testar e empacotar

| Ação | Linux / macOS | Windows |
|------|---------------|---------|
| Rodar | `./gradlew :desktop:run` | `gradlew.bat :desktop:run` |
| Testes unitários | `./gradlew :desktop:test` | `gradlew.bat :desktop:test` |
| Instalador | `./gradlew :desktop:packageDeb` (Linux) · `./gradlew :desktop:packageDmg` (macOS) | `gradlew.bat :desktop:packageMsi` |

Os instaladores saem em `desktop/build/compose/binaries/main/<formato>/` (ex.: `deb/lumini_1.0.0_amd64.deb`);
a versão vem de `-PluminiVersao=1.0.1` (padrão 1.0.0).
Cada formato só é gerado no próprio sistema (o `.msi` precisa de Windows com o [WiX Toolset 3](https://wixtoolset.org/),
que o plugin baixa sozinho). No Linux, o `.deb` precisa de `fakeroot` e `dpkg-deb`.

**Release no GitHub:** o workflow [`desktop-instaladores.yml`](.github/workflows/desktop-instaladores.yml)
gera `.msi` (Windows), `.deb` (Ubuntu) e `.dmg` (macOS Apple Silicon e Intel) e publica no
[Release](https://github.com/AecioJose189/EdutecApp/releases/latest). Dispara com uma tag:
`git tag v1.0.0 && git push origin v1.0.0` (ou em Actions → "Instaladores do desktop" → Run workflow,
quando o workflow estiver na `main`). Os instaladores não são assinados (ver o [guia](docs/GUIA-AVALIADOR.md)).

Teclado e mouse: **Esc** volta uma tela (ou cancela a geração), **Enter** confirma o diálogo Nova turma.

---

## App Android (congelado)

App Android em **Kotlin + Jetpack Compose** que reproduz a tela "Minhas turmas" de [`images/image.png`](images/image.png).

### Estrutura

```
app/src/main/java/com/edutec/app/
├── MainActivity.kt              # ponto de entrada
├── data/
│   ├── Turma.kt                 # turmas + dados de exemplo
│   └── Conceito.kt              # conceitos do grafo e alunos de exemplo
└── ui/
    ├── EdutecApp.kt             # navegação: Minhas turmas → Turma → Nova atividade
    ├── MinhasTurmasScreen.kt    # lista de turmas (cards, barras, legenda, diálogo)
    ├── TurmaScreen.kt           # tela da turma (grafo, alunos, painel do conceito)
    ├── NovaAtividadeScreen.kt   # criação de atividade (grafo ou PDF, formato, tentativas)
    ├── Componentes.kt           # selo de estado, botão do topo
    └── theme/                   # cores e tema
```

A tela é responsiva:
- **Tablet / tela larga (≥ 840dp)**: 3 colunas, igual à imagem.
- **Tela média (≥ 600dp)**: 2 colunas.
- **Celular**: 1 coluna.

Interações:
- **Minhas turmas**: seletor de semestre, "Nova turma" (diálogo) e toque num card abre a turma.
- **Turma**: visual do grafo; "Nova atividade" abre a criação (e "Gerar atividade de reforço"
  abre a mesma tela já com "Regra da cadeia" marcada). As outras abas ainda só mostram um aviso.
- **Nova atividade**: escolha entre *Conceitos do grafo* (chips selecionáveis, botão "Selecionar esses")
  e *Material em PDF* (abre o seletor de arquivos do Android; o PDF é copiado para
  `files/materiais/` do app, com barra de progresso). Formato, quantidade, dificuldade, refazer e tempo
  atualizam o resumo "A IA vai gerar". "Gerar atividade" só habilita com conceitos ou PDF enviados;
  as etapas 3–5 (Gerando, Revisão, Publicar) ainda não existem.
- O botão "voltar" do Android volta uma tela.

### Como testar

1. Instale o [Android Studio](https://developer.android.com/studio) (já vem com JDK e Android SDK).
2. **File → Open** e selecione a pasta `EdutecApp`. Espere o *Gradle Sync* terminar.
3. Para ver sem rodar: abra `ui/MinhasTurmasScreen.kt` e clique em **Split** (canto superior direito)
   para ver os previews "Tablet" e "Celular".
4. Para rodar: **Tools → Device Manager → Create Virtual Device**, escolha um tablet
   (ex.: *Pixel Tablet* ou *Medium Tablet*) para ver o layout de 3 colunas, ou um celular.
   Depois clique em **Run ▶** (Shift+F10).
5. Celular físico: ative *Opções do desenvolvedor → Depuração USB*, conecte por cabo e escolha-o no seletor de dispositivos.

Via linha de comando (com JDK 17 ou mais recente e Android SDK configurados). Fora do Android Studio,
indique o SDK em `local.properties` (não vai para o git), por exemplo `sdk.dir=/home/<usuario>/Android/Sdk`,
ou defina a variável `ANDROID_HOME`.

| Ação | Windows | Linux / macOS |
|------|---------|---------------|
| Gerar o APK (`app/build/outputs/apk/debug/app-debug.apk`) | `gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Instalar no emulador/celular conectado | `gradlew.bat installDebug` | `./gradlew installDebug` |
