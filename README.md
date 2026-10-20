# Edutec – Minhas turmas

App Android em **Kotlin + Jetpack Compose** que reproduz a tela "Minhas turmas" de `../imagens_se_basear/image.png`.

## Estrutura

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

## Como testar

1. Instale o [Android Studio](https://developer.android.com/studio) (já vem com JDK e Android SDK).
2. **File → Open** e selecione a pasta `EdutecApp`. Espere o *Gradle Sync* terminar.
3. Para ver sem rodar: abra `ui/MinhasTurmasScreen.kt` e clique em **Split** (canto superior direito)
   para ver os previews "Tablet" e "Celular".
4. Para rodar: **Tools → Device Manager → Create Virtual Device**, escolha um tablet
   (ex.: *Pixel Tablet* ou *Medium Tablet*) para ver o layout de 3 colunas, ou um celular.
   Depois clique em **Run ▶** (Shift+F10).
5. Celular físico: ative *Opções do desenvolvedor → Depuração USB*, conecte por cabo e escolha-o no seletor de dispositivos.

Via linha de comando (com JDK 17 e Android SDK configurados):

```
gradlew.bat assembleDebug      # gera app/build/outputs/apk/debug/app-debug.apk
gradlew.bat installDebug       # instala no emulador/celular conectado
```
