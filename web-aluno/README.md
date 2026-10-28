# Lumini — site do aluno (`web-aluno/`)

Site leve, feito para o celular, onde o aluno entra na turma, responde às atividades da aula (pelo QR code
projetado pelo app do professor) e vê o próprio grafo de conceitos com o domínio estimado.

**Stack:** Vite + React 18 + TypeScript + supabase-js, CSS próprio (sem biblioteca de UI), KaTeX para as
fórmulas `$...$` dos enunciados. Contrato com o back-end: `docs/contrato-api-mvp.md`.

## Rodar

```bash
cd web-aluno
npm install
cp .env.example .env.local   # e ajuste (fica fora do git)
npm run dev                  # http://localhost:5173 (no celular da mesma rede: http://<ip-do-pc>:5173)
npm test                     # testes (layout do grafo, LaTeX)
npm run build                # gera dist/
```

`.env.local`:

| Variável | Para quê |
|---|---|
| `VITE_API_SIMULADA` | `true` = não fala com o Supabase: login falso e respostas de exemplo do contrato. |
| `VITE_SUPABASE_URL` | URL do projeto Supabase (só no modo real). |
| `VITE_SUPABASE_CHAVE_PUBLICA` | Chave **pública** (publishable/anon). Nunca a `service_role`. |

## Modo simulado

Com `VITE_API_SIMULADA=true`, o botão "Entrar com Google" só marca um "Aluno de teste" na aba (sessionStorage).
O aluno começa sem turmas. As respostas seguem `docs/contrato-api-mvp.md`, e alguns valores disparam os erros:

| Abra | Resultado |
|---|---|
| `/convite/K7QF2M` | Entra em "Inteligência Artificial" (a turma do grafo de exemplo). Outro código: "Estruturas de Dados". |
| `/convite/XXXXXX` | `convite_invalido` |
| `/s/<qualquer>` | Atividade de 5 questões (4 múltipla escolha, 1 aberta), prazo de 30 min. |
| `/s/rapida` | Prazo de 45 s, para ver o cronômetro acabar e o envio automático. |
| `/s/semprazo` | `prazo_em: null` (sem cronômetro). |
| `/s/enviada` | `ja_enviada: true` |
| `/s/expirado` · `/s/encerrada` · `/s/fora` | `qr_expirado` · `sessao_encerrada` · `nao_matriculado` |

Para recomeçar do zero, feche a aba (o estado simulado fica no sessionStorage).

## Telas

| Rota | Tela |
|---|---|
| `/entrar?volta=<caminho>` | Login com Google; depois volta para `<caminho>` (convite ou QR). |
| `/` | Minhas turmas (`minhas_turmas`). |
| `/convite/:codigo` | Aceita o convite (`aceitar_convite`, idempotente). |
| `/s/:token` | Atividade do QR (`entrar_sessao` → `enviar_respostas`). |
| `/turmas/:turmaId/grafo` | Meu grafo (`meu_grafo`), em SVG, camadas da esquerda para a direita. |

Comportamentos combinados:

- **Atividade:** uma questão por vez, uma tela de revisão e "Enviar" com confirmação. As respostas ficam no
  `localStorage` do aparelho até o envio, então recarregar não perde nada; depois do envio fica só o resultado.
  A tentativa fica no `sessionStorage` por token, porque o token do QR muda a cada ~20 s e recarregar não deve
  dar "QR expirado".
- **Cronômetro:** aviso laranja no último minuto. A 3 s do fim, envia sozinho o que já foi respondido.
- **Grafo:** mesma regra de layout do desktop (`src/grafo/layoutEmCamadas.ts` é a porta do `LayoutGrafo.kt`,
  com os mesmos testes). Tocar num nó (ou na lista) mostra o nome completo, o domínio estimado em % e o nº de
  respostas.
- **Erros:** `src/api/erros.ts` traduz cada código do contrato num título simples e no que fazer.

## Estrutura

```
src/
├── api/          tipos (espelho do contrato), cliente Supabase, chamarAlunoApi (real ou simulada), erros
├── sessao/       login (Google ou falso) e rota protegida
├── telas/        Entrar, MinhasTurmas, Convite, atividade/, grafo/
├── componentes/  Cartão de erro, selo de estado (cor + símbolo), LaTeX, carregando, topo
├── grafo/        layout em camadas (+ testes)
└── estilos/      global.css com as cores do desktop
```

## Ligar no Supabase de verdade

1. Em `.env.local`: `VITE_API_SIMULADA=false`, mais a URL e a chave pública.
2. No painel do Supabase, em **Authentication → URL Configuration → Redirect URLs**, inclua
   `http://localhost:5173/**` e a URL publicada (ex.: `https://<site>.vercel.app/**`). Para testar no celular
   pela rede local, inclua também `http://<ip-do-pc>:5173/**`.
3. Provedor Google ativo em **Authentication → Providers**.

## Deploy (Vercel)

```bash
cd web-aluno
npx vercel        # primeira vez: escolher a conta/projeto; raiz = web-aluno
npx vercel --prod
```

Configure as três variáveis `VITE_*` no projeto da Vercel. O `vercel.json` manda todas as rotas para
`index.html` (SPA).

## Pendências com o back-end

- Quando `entrar_sessao` volta com `ja_enviada: true`, o contrato não traz o resultado. Por isso, em outro
  aparelho o aluno vê só "Você já enviou esta atividade". Um campo opcional `resultado` resolveria isso.
