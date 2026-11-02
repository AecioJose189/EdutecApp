# Lumini — guia do avaliador

**O que é:** o Lumini ajuda o professor a acompanhar o que a turma aprendeu. O professor envia um PDF,
a IA gera as questões, os alunos respondem pelo celular e o app mostra um **grafo de conceitos** com o
**domínio estimado** de cada um (não é nota).

Você vai precisar de **um computador** (professor) e **um celular** (aluno), com **duas contas Google
diferentes** (a mesma conta não pode ser professor e aluno na mesma turma).

## 1. Instalar o app do professor

Baixe o instalador do seu sistema em **https://github.com/AecioJose189/EdutecApp/releases/latest**.

- **Windows** (`...windows-x64.msi`): abra o arquivo. Se aparecer **"O Windows protegeu o computador"**,
  clique em **Mais informações → Executar assim mesmo**. Não precisa de senha de administrador.
- **macOS** (`...macos-apple-silicon.dmg` para M1/M2/M3...; `...macos-intel.dmg` para Mac Intel): abra o
  `.dmg` e arraste o Lumini para **Aplicativos**. Na primeira vez, clique no Lumini com o **botão direito → Abrir**
  e confirme **Abrir**. No macOS 15 ou mais novo, se não aparecer o botão: **Ajustes do Sistema → Privacidade e
  Segurança → Abrir Mesmo Assim**.
- **Linux (Ubuntu/Debian)** (`...linux-amd64.deb`): `sudo apt install ./Lumini-*-linux-amd64.deb` e procure
  "Lumini" no menu de aplicativos.

## 2. Material de teste

PDFs de teste: **[LINK DOS PDFs DE TESTE]**

Na Nova atividade, deixe as **opções padrão** (5 questões, múltipla escolha, dificuldade média): com elas
a resposta vem na hora. Um PDF seu também funciona, mas a IA pode levar **até ~1 minuto**.

## 3. Roteiro

**No computador (professor)**

1. Abra o Lumini e clique em **Entrar com Google** (o navegador abre; depois de entrar, volte ao app).
2. **Nova turma** → dê um nome e crie.
3. Abra a turma → **Convidar alunos**: aparece um código, um link e um QR.
4. **Nova atividade** → **Material em PDF** → escolha o PDF → **Gerar atividade**.
5. Confira as questões na **Revisão** → **Publicar**.
6. Aba **Atividades** → **Abrir sessão em sala**: aparece um QR que muda sozinho (deixe na tela).

**No celular (aluno)** — site **https://lumini-aluno.vercel.app**

7. Abra o **link do convite** (ou leia o QR do convite) e **entre com Google** (outra conta).
8. Leia o **QR da sessão** na tela do professor, responda as questões e envie.
9. Veja **Meu grafo**.

**De volta ao computador**

10. Acompanhe "entraram/enviaram" ao vivo → **Encerrar sessão** → veja o **grafo da turma**
    (✓ consolidado, ~ em desenvolvimento, ! revisar, ? sem dados) e clique nos conceitos.

## 4. Limitações conhecidas

- Questões **abertas** ainda não são corrigidas (não entram no grafo); use múltipla escolha.
- O **feedback com IA** para o aluno fica para a entrega final.
- A IA gratuita às vezes fica sobrecarregada: o app pede para **tentar de novo** (ou gerar menos questões).

## Problemas?

Fale com o grupo: **[CONTATO]**
