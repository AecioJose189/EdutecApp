# Lumini

Plataforma de **Knowledge Tracing** e **Learning Analytics** para o ensino superior (projeto EDUTEC).

- **Professor:** app desktop em Compose Multiplatform, módulo [`desktop/`](#app-do-professor-desktop) (Linux, Windows, macOS).
- **Aluno:** site web para o celular (Vite + React + TypeScript + supabase-js), ainda não começou.
- **Back-end:** Supabase (Postgres, Edge Functions, Storage), em `supabase/`.
- **Android:** o app original do professor (módulo `app/`) está **congelado** na tag `demo-nivel2`.
  O resto deste README depois da seção do desktop descreve esse app.

## App do professor (desktop)

Precisa de JDK 21 (o Gradle baixa um se não houver). O código fica em
`desktop/src/main/kotlin/com/edutec/app/` (cópia adaptada do Android: mesmas telas, ViewModels e acesso ao Supabase).

### Configuração (URL e chave pública do Supabase)

Nada disso vai para o git. O app procura, nesta ordem:

1. variáveis de ambiente `SUPABASE_URL` e `SUPABASE_CHAVE_PUBLICA`;
2. o arquivo indicado na variável `LUMINI_CONFIG`;
3. `lumini.properties` na pasta de configuração do usuário:
   - Linux: `~/.config/lumini/lumini.properties`
   - Windows: `%APPDATA%\Lumini\lumini.properties`
   - macOS: `~/Library/Application Support/Lumini/lumini.properties`
4. `lumini.properties` na pasta atual.

Conteúdo do arquivo (só a chave **pública**, publishable ou anon; nunca a `service_role`):

```properties
SUPABASE_URL=https://<projeto>.supabase.co
SUPABASE_CHAVE_PUBLICA=sb_publishable_...
```

Em desenvolvimento, `./gradlew :desktop:run` já usa os valores do `local.properties` (o mesmo do Android)
se as variáveis de ambiente não estiverem definidas. Sem configuração, Minhas turmas mostra onde criar o arquivo.

### Login

"Entrar com Google" abre o navegador do sistema; o retorno chega ao Lumini em `http://localhost:3737/callback`
(porta fixa, aberta só durante o login). No painel do Supabase, **Authentication → URL Configuration →
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

Os instaladores saem em `desktop/build/compose/binaries/main/<formato>/` (ex.: `deb/lumini_1.0.0_amd64.deb`).
Cada formato só é gerado no próprio sistema (o `.msi` precisa de Windows com o [WiX Toolset 3](https://wixtoolset.org/),
que o plugin baixa sozinho). No Linux, o `.deb` precisa de `fakeroot` e `dpkg-deb`.

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
