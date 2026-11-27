# Lumini — site do aluno (`web-aluno/`)

Site leve, feito para o celular, onde o aluno entra na turma, responde às atividades da aula (pelo QR code
projetado pelo app do professor) e vê o próprio mapa do conhecimento com o domínio estimado.

**Stack:** Vite + React 18 + TypeScript + supabase-js, CSS próprio (sem biblioteca de UI), KaTeX para as
fórmulas `$...$` dos enunciados. Contrato com o back-end: `docs/contrato-api-mvp.md` e, para a revisão do
aluno, `docs/contrato-api-fase2.md`.

## Rodar

```bash
cd web-aluno
npm install
cp .env.example .env.local   # e ajuste (fica fora do git)
npm run dev                  # http://localhost:5173 (no celular da mesma rede: http://<ip-do-pc>:5173)
npm test                     # testes (layout do grafo, LaTeX, contrato no modo simulado)
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
| `/s/expirado` · `/s/encerrada` | `qr_expirado` · `sessao_encerrada` |
| `/s/fora` | Ainda não está na turma: "Entrar na turma e começar" (`entrar_na_turma: true`, formato provisório da fase 2). |
| `/s/fora-antigo` | `nao_matriculado` sem a turma, como no servidor do MVP. |
| `/s/deslogado` | `nao_autenticado`: sai e vai para o login, que depois volta para `/s/deslogado`. |

### Fase 2: revisão do aluno

O back-end de `minhas_atividades` e `revisao_tentativa` ainda vai ao ar; até lá, teste no modo simulado.
`src/api/simulada.test.ts` confere o formato do contrato (por exemplo, que `correta`, `feedback` e `rubrica`
não vêm sem o gabarito).

| Abra | Resultado |
|---|---|
| `/s/<qualquer>` e envie | Depois de "Você acertou X de Y", a lista de questões com Acertou / Errou / Aguarda correção, **sem** a alternativa certa. |
| `/atividades` | Minhas atividades. Depois de `/convite/K7QF2M`: Atividade 2 e 1 de exemplo, mais as que você enviou nesta aba (Atividade 3). Sem turma: estado vazio. |
| `/atividades?turma=00000000-0000-4000-8000-000000000001` | Só a Inteligência Artificial (é o link "Atividades desta turma" do mapa). |
| `/atividades/exemplo-sem-gabarito` | Revisão com `gabarito_liberado: false`: a sua resposta e se acertou; uma questão em branco. |
| `/atividades/exemplo-liberado` | Revisão com `gabarito_liberado: true`: resposta certa, feedback de cada alternativa e rubrica da aberta. Alternativas da questão 1 embaralhadas (o rótulo A, B, C vem da posição). |
| `/atividades/tentativa-<token>` | A que você enviou por `/s/<token>`, sem o gabarito (na simulação, a sessão continua aberta). |
| `/atividades/outra-pessoa` | `nao_matriculado`: "Atividade não encontrada". |

Para recomeçar do zero, feche a aba (o estado simulado fica no sessionStorage).

## Telas

| Rota | Tela |
|---|---|
| `/entrar?volta=<caminho>` | Login com Google; depois volta para `<caminho>` (convite ou QR). |
| `/` | Minhas turmas (`minhas_turmas`). |
| `/convite/:codigo` | Aceita o convite (`aceitar_convite`, idempotente). |
| `/s/:token` | Atividade do QR (`entrar_sessao` → `enviar_respostas`). |
| `/turmas/:turmaId/grafo` | Mapa do conhecimento (`meu_grafo`), em SVG, camadas da esquerda para a direita. |
| `/atividades?turma=<id>` | Minhas atividades (`minhas_atividades`), da mais recente para a mais antiga. |
| `/atividades/:tentativaId` | Revisão de uma tentativa enviada (`revisao_tentativa`). |

Comportamentos combinados:

- **Atividade:** uma questão por vez, uma tela de revisão e "Enviar" com confirmação. As respostas ficam no
  `localStorage` do aparelho até o envio, então recarregar não perde nada; depois do envio fica só o resultado.
  Ao recarregar, o site pergunta de novo ao servidor, que aceita o token velho de quem já tem tentativa. Uma
  cópia no `sessionStorage` só é usada se a rede cair.
- **Cronômetro:** aviso laranja no último minuto. A 3 s do fim, envia sozinho o que já foi respondido.
- **Grafo:** mesma regra de layout do desktop (`src/grafo/layoutEmCamadas.ts` é a porta do `LayoutGrafo.kt`,
  com os mesmos testes). Tocar num nó (ou na lista) mostra o nome completo, o domínio estimado em % e o nº de
  respostas.
- **Depois de enviar (fase 2):** "Você acertou X de Y" e quais questões errou, sem a alternativa certa nem
  o feedback, porque colegas ainda podem estar respondendo. Se o servidor não mandar `questoes` (MVP), fica
  só o placar.
- **Revisão (fase 2):** o gabarito (alternativa certa, feedback, rubrica) só aparece quando o servidor o manda,
  ou seja, depois que o professor encerra a sessão. As alternativas são rotuladas A, B, C… pela posição na
  lista, nunca pelo id (podem vir embaralhadas). Acertou/errou sempre com símbolo e palavra (✓ ✗ …).
- **Erros:** `src/api/erros.ts` traduz cada código do contrato num título simples e no que fazer.
- **Login vencido:** um 401 da plataforma (token ausente ou vencido, sem o JSON do contrato) vira
  `nao_autenticado`. Nesse caso, o site tenta renovar o login uma vez; se não conseguir, sai e leva para
  `/entrar`, que depois volta para a mesma página.

## Estrutura

```
src/
├── api/          tipos (espelho do contrato), cliente Supabase, chamarAlunoApi (real ou simulada), erros
├── sessao/       login (Google ou falso) e rota protegida
├── telas/        Entrar, MinhasTurmas, Convite, atividade/, grafo/
├── componentes/  Cartão de erro, selo de estado (cor + símbolo), LaTeX, carregando, topo
├── grafo/        layout em camadas (+ testes)
└── estilos/      tema.css (tokens do Design System; única fonte de cores) e global.css
```

## Ligar no Supabase de verdade

1. Em `.env.local`: `VITE_API_SIMULADA=false`, mais a URL e a chave pública.
2. No painel do Supabase, em **Authentication → URL Configuration → Redirect URLs**, inclua
   `http://localhost:5173/**` e a URL publicada (ex.: `https://<site>.vercel.app/**`). Para testar no celular
   pela rede local, inclua também `http://<ip-do-pc>:5173/**`.
3. Provedor Google ativo em **Authentication → Providers**.

## Deploy (Vercel)

O projeto `lumini-aluno` está ligado ao GitHub: cada push gera um deploy (a `main` vai para produção em
https://lumini-aluno.vercel.app, as outras branches viram prévia) e o check "Vercel" aparece no PR.
No painel (Settings → Build and Deployment), **Root Directory = `web-aluno`** e Framework = Vite. Sem a pasta
raiz, a Vercel tenta construir na raiz do repositório e falha com `vite: command not found`.

À mão, se precisar: `cd web-aluno && npx vercel --prod`.

Variáveis `VITE_*` em Production e Preview: URL do Supabase, chave **pública** (`sb_publishable_`) e
`VITE_API_SIMULADA=false` (nunca `true` em Production). O `vercel.json` manda todas as rotas para
`index.html` (SPA).

## Pendências com o back-end

- Quando `entrar_sessao` volta com `ja_enviada: true`, o contrato não traz o resultado. Por isso, em outro
  aparelho o aluno vê só "Você já enviou esta atividade". Um campo opcional `resultado` resolveria isso.
- Fase 2: o servidor de `minhas_atividades`, `revisao_tentativa` e de `questoes` no envio está na branch
  `fase2-backend` (Júlio). Depois que entrar na `main` e for publicado, testar com `VITE_API_SIMULADA=false`.
