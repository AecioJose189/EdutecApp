# Lumini

O Lumini mostra ao professor do ensino superior o que a turma aprendeu, conceito por conceito. O professor envia o PDF da aula, a IA gera atividades, os alunos respondem em sala pelo celular e o Knowledge Tracing (BKT) estima o domínio de cada conceito, desenhado num grafo.

Este sistema vale para as duas superfícies: o **app desktop do professor** (Compose Multiplatform) e o **site do aluno** (React, no celular). Paleta de marca escolhida pelo grupo (Erick e Arthur); cores de apoio, de estado e regras de contraste definidas em 30/10/2026.

## Princípios

1. **Clareza antes de beleza.** O professor precisa saber sempre o que fazer em seguida. Toda tela tem uma ação principal, e só uma, na cor `primaria`.
2. **O estado nunca depende só da cor.** Consolidado, em desenvolvimento, revisar e sem dados aparecem sempre com símbolo (✓ ~ ! ?) e, quando houver espaço, com o nome.
3. **Calma, não alarme.** O tom é de acompanhamento. Laranja é o único alerta forte e é reservado para "revisar".
4. **Legível em projetor.** O desktop vai para a tela grande da sala: textos de 16px para cima e contraste de pelo menos 4,5:1 em todo texto.

## Voz e termos

- **"Domínio estimado"**, nunca "nota". Sempre que o número aparecer pela primeira vez numa tela: "chance estimada de saber o conceito; não é nota".
- **"Refazer"**, nunca "reteste".
- **"Sessão em sala"** para a atividade aberta com QR code.
- Botões dizem o que acontece: "Publicar para a turma", "Abrir sessão em sala", "Convidar alunos". Nada de "OK" ou "Confirmar" sozinhos.
- Erros dizem o que houve, o que não foi perdido e o que fazer: "A IA está sobrecarregada agora. Suas escolhas e o PDF continuam aqui. Tente de novo."

## Cores

A marca é feita de teal e menta. A interface é quase toda neutra (`fundo`, `superficie`, `tinta`), com a cor da marca concentrada nas ações.

| Papel | Token | Regra |
|---|---|---|
| Ação principal | `primaria` + `sobre-primaria` | Um botão principal por tela. |
| Seleção e destaque calmo | `menta` com texto `tinta` | Nunca texto branco sobre menta no tema claro (1,6:1). |
| Avisos informativos | `menta-suave` | Ex.: "Convide os alunos antes de abrir a sessão". |
| Detalhes | `teal-medio` | Ícones e divisores. Não serve para texto sobre fundo claro (2,9:1). |
| Sucesso pontual | `acento` + `sobre-acento` | "Enviado", "Publicado", ponto "ao vivo". Use pouco. |
| Erro | `erro` | Só para falhas e ações destrutivas. |
| Telas | `fundo` atrás, `superficie` nos cartões | O fundo levemente tingido dá hierarquia sem sombra. |

### Por que o verde da marca não é "consolidado"

O `acento` (#02D496) e o amarelo de "em desenvolvimento" têm quase a mesma luminosidade (contraste 1,15:1 entre si). Para quem é daltônico, ou numa tela de projetor, os dois nós ficariam iguais. Por isso **consolidado usa o teal escuro da marca** (`estado-consolidado` = `primaria` no tema claro), que se separa bem do amarelo (4,9:1).

## Estados de aprendizagem

| Estado | Token | Símbolo | Símbolo/texto por cima |
|---|---|---|---|
| Consolidado | `estado-consolidado` | ✓ | `sobre-consolidado` |
| Em desenvolvimento | `estado-desenvolvimento` | ~ | `sobre-desenvolvimento` |
| Revisar | `estado-revisar` | ! | `sobre-revisar` |
| Sem dados | contorno tracejado `estado-sem-dados`, sem preenchimento | ? | `tinta-suave` |

Limiares propostos (BKT): revisar < 0,5 ≤ em desenvolvimento < 0,8 ≤ consolidado.

## Tipografia

- **Sora** (`display`) para títulos e números de destaque: geométrica e amigável, dá personalidade sem perder a seriedade.
- **DM Sans** (`sans`) para todo o resto: muito legível em tamanhos pequenos e em projetor.
- As duas são do Google Fonts (licença OFL). No desktop, empacotar os arquivos `.ttf` em `resources/font`; no site, carregar pelo Google Fonts.
- Números que se comparam (domínio, contagens) usam algarismos tabulares.

## Espaço e forma

- Grade de 4px: `espaco-1` a `espaco-12`.
- Cantos grandes e suaves: `raio-md` em botões e campos, `raio-lg` em cartões, `raio-pilula` em abas e contadores.
- Cartões comuns: `superficie` + borda `borda`, sem sombra. `sombra-cartao` só em diálogos e painéis que flutuam.

## O grafo de conhecimento

- **Nó = conceito**, círculo na cor do estado, com o símbolo no centro (`titulo-3`) e o nome embaixo (`apoio`, `tinta`).
- **Tamanho = número de respostas**, entre 40px e 72px de diâmetro.
- **Setas = pré-requisito**, 1,5px em `teal-medio`, com ponta.
- **Selecionado**: anel de 3px em `tinta` com 3px de folga.
- Nó sem dados: contorno tracejado de 2px em `estado-sem-dados`, fundo `superficie`.

## Acessibilidade

- Texto: 4,5:1 no mínimo, nos dois temas. Controles, ícones e o anel de foco: 3:1 no mínimo (`borda-controle`, `foco`).
- Estado sempre com símbolo; nunca só com cor.
- Foco visível em tudo que é clicável (anel de 2px em `foco`).
- Toque no celular: área mínima de 44 × 44px.

## Logo

Entregue pelo grupo de design em 06/11/2026. O símbolo é um **L em forma de chave**, com **raios de luz** em volta: a chave do conhecimento que se acende ("Lumini" vem de *lumen*, luz). Os arquivos (SVG, vetoriais) estão no grupo **Logos**.

### Versões

| Versão | Quando usar |
|---|---|
| **Horizontal** (símbolo + nome lado a lado) | Padrão. Cabeçalho do site, tela de login, barra do app, slides. |
| **Vertical** (símbolo em cima do nome) | Espaços quadrados ou altos: capa dos slides, tela de abertura, cartazes. |
| **Símbolo** sozinho | Ícone do app, favicon, avatar. Só quando o nome Lumini já aparece por perto ou o espaço é pequeno. |
| **Nome** sozinho | Rodapés e lugares estreitos onde o símbolo ficaria pequeno demais. |

### Cores

Cada versão existe em quatro cores:

- **primaria**: nome e haste em `primaria`, raios em `acento` e `menta`. **É a versão principal sobre fundo claro** (`fundo`, `superficie`, branco).
- **acento**: nome e haste em `acento`, raios em `primaria` e `menta`. Para destaque sobre fundo claro em tamanho grande (capa, cartaz). Evite em textos pequenos: o verde sobre branco tem contraste baixo.
- **branco**: tudo em branco. Sobre `primaria`, fotos escuras e o tema escuro.
- **mono**: tudo em preto. Impressão em preto e branco, carimbos, documentos oficiais.

Sobre `primaria` ou fundo escuro, nunca use as versões primaria ou mono: a haste some.

### Regras

- **Área de respiro:** em volta do logo, deixe livre no mínimo a altura da letra "L" do nome.
- **Tamanho mínimo:** horizontal com 120px de largura; símbolo com 32px de altura. Abaixo disso os raios viram borrão; para favicon e ícones de 16 a 24px, o grupo de design vai avaliar uma versão simplificada.
- **Não faça:** esticar ou achatar, trocar as cores dos raios, girar, colocar sombra ou contorno, refazer o nome digitando em outra fonte.
- O nome do logo é desenho próprio (letras arredondadas). Na interface, títulos continuam em **Sora**; não tente imitar o logo com fonte.

### Onde aplicar no código

- **Desktop:** símbolo como ícone do app (instaladores `.msi`, `.dmg`, `.deb` e a barra de tarefas) e logo horizontal na tela de login e no topo da lista de turmas.
- **Site do aluno:** logo horizontal no topo, símbolo como favicon.
- Use sempre os arquivos SVG do grupo **Logos**; não converta para PNG pequeno sem necessidade.

## Como aplicar no código

- **Desktop (Kotlin):** cada token vira uma constante em `ui/theme/Color.kt` (claro e escuro) e entra no `ColorScheme` do Material 3 em `Theme.kt`. Nenhuma cor literal fora desses dois arquivos.
- **Site do aluno:** cada token vira uma variável CSS (`--primaria`, `--tinta`…) num único arquivo de tema, com o tema escuro em `@media (prefers-color-scheme: dark)`.
- Ao trocar a paleta no futuro, só esses arquivos mudam.
