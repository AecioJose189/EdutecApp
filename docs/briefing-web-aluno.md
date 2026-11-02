# Briefing para o Claude Code — Site do aluno do Lumini

_Leia este arquivo inteiro, depois `CLAUDE.md` e `docs/contrato-api-mvp.md`. Entrega: avaliação por pares em **28/10/2026, 18:30**. Congelamento de código às **16h**._

## O projeto em uma frase

**Lumini** é uma ferramenta de **Knowledge Tracing** e **Learning Analytics** para o professor do ensino superior: o professor envia um PDF, a IA (Gemini) extrai conceitos e pré-requisitos e gera questões, os alunos respondem em sala e o **BKT** (Bayesian Knowledge Tracing, no Postgres) estima o domínio de cada conceito. O professor vê o grafo da turma; o aluno vê o próprio grafo.

## Plataforma (decidida com o professor da disciplina)

- **Professor:** app **desktop** em Compose Multiplatform (Kotlin) — módulo `:desktop`, feito pelo Júlio.
- **Aluno:** **site web leve** usado no celular — **é a sua parte**, pasta `web-aluno/`.
- **Back-end:** 100% **Supabase** (Auth com Google, Postgres com RLS, Edge Functions em TypeScript). A IA só é chamada pelas Edge Functions. Feito pelo Júlio.
- O antigo app Android está congelado (tag `demo-nivel2`); não mexa em `app/`.

## Sua tarefa: `web-aluno/`

Stack: **Vite + React + TypeScript + supabase-js**. Sem framework de UI pesado; CSS simples ou uma biblioteca leve. Mobile-first (tela de celular, toque, fontes ≥ 16 px). Tudo em **português do Brasil**.

Telas do MVP:
1. **Entrar** — botão "Entrar com Google". Depois do login, voltar para a URL de onde veio (convite ou QR).
2. **Minhas turmas** — lista (`minhas_turmas`); estado vazio: "Peça ao professor o link de convite da turma".
3. **Convite** (`/convite/<codigo>`) — confirma a turma e entra (`aceitar_convite`).
4. **Atividade** (`/s/<token>`, vindo do QR) — `entrar_sessao`; mostra as questões uma por vez ou em lista, com cronômetro se houver `prazo_em`; múltipla escolha com alternativas grandes e tocáveis; aberta com campo de texto; botão **Enviar** com confirmação; depois mostra "Você acertou X de Y" (+ "N questões abertas aguardam correção").
5. **Meu grafo** (por turma) — `meu_grafo`; desenhe em **SVG**: nós = conceitos, setas = pré-requisitos, camadas da esquerda para a direita; tocar num nó mostra nome completo, domínio estimado em % ("chance estimada de você saber o conceito; não é nota") e nº de respostas. No celular, permita rolar/arrastar.

Erros: mostre a `mensagem` do servidor em linguagem simples + o que fazer (ex.: `qr_expirado` → "O código mudou. Aponte a câmera para o QR de novo.").

## Regras que valem para todo o código

- **Nenhuma chave secreta no site.** Só a URL do Supabase e a chave pública (`VITE_SUPABASE_URL`, `VITE_SUPABASE_CHAVE_PUBLICA` em `.env.local`, fora do git; deixe um `.env.example`).
- O aluno nunca vê gabarito antes de enviar (o servidor já não manda; não tente contornar).
- Estado de aprendizagem sempre com **símbolo além da cor**: ✓ consolidado (azul `#1F5BA8`), ~ em desenvolvimento (amarelo `#F2C14E`), ! revisar (laranja `#D0531F`), ? sem dados (cinza). Fundo `#F6F4EF`, cartões brancos com cantos grandes — mesmo visual do desktop.
- Termos: "domínio estimado" (nunca "nota"), "refazer" (nunca "reteste").
- Privacidade: nada de dados de alunos em logs ou serviços de terceiros.

## Como trabalhar em paralelo sem atrapalhar

- Branch **`web-aluno`**, criada a partir da `desktop` mais recente (`git fetch && git checkout -b web-aluno origin/desktop`).
- Mexa **só** em `web-aluno/` (e no próprio README dela). Para mudar o contrato da API ou o `CLAUDE.md`, combine com o Júlio.
- Enquanto o back-end não estiver pronto, use um **modo simulado** (`VITE_API_SIMULADA=true`) que devolve exatamente os exemplos de `docs/contrato-api-mvp.md`. Quando o Júlio avisar, desligue e teste contra o Supabase de verdade.
- Commits pequenos; `git push` com frequência.

## Configuração que depende do Júlio

- URL e chave pública do Supabase (ele te passa por mensagem privada).
- Incluir `http://localhost:5173/**` e a URL final do site em **Authentication → URL Configuration** do Supabase.
- Deploy: **Vercel** (`npx vercel` dentro de `web-aluno/`), com redirecionamento de todas as rotas para `index.html` (`vercel.json`). Combinem em qual conta.

## Pronto quando

No celular, pela URL publicada: entrar com Google → abrir link de convite → aceitar → ler o QR projetado pelo desktop → responder → ver "acertou X de Y" → abrir "Meu grafo" e ver os conceitos coloridos.
