# Lumini — Contrato da API, fase 2 (revisão do aluno)

Acréscimo a `docs/contrato-api-mvp.md`. Mesmas regras gerais: tudo pela Edge Function `aluno-api`, `{ "acao": "<nome>", ... }`, erro como `{ "erro", "mensagem" }`, qualquer 401 = `nao_autenticado`. Só se **acrescentam** campos e ações; nada do MVP muda de significado.

## Regra de revelação (anti-cola)

| Momento | O aluno vê |
|---|---|
| Logo após enviar | Quantas acertou e **quais questões errou**. Sem a alternativa correta, sem feedback. |
| Depois que o professor **encerra a sessão** | Gabarito completo: alternativa correta, feedback de cada alternativa e rubrica das abertas. |

Motivo: enquanto a sessão está aberta, colegas na mesma sala ainda podem estar respondendo.

## Mudança em `enviar_respostas`

A resposta ganha o campo `questoes`:
```json
{
  "acertos": 3, "total_multipla_escolha": 4, "abertas_aguardando_correcao": 1,
  "questoes": [
    { "questao_id": "1", "acertou": true },
    { "questao_id": "2", "acertou": false },
    { "questao_id": "5", "acertou": null }
  ]
}
```
`acertou: null` = questão aberta (aguardando correção).

## Nova ação `minhas_atividades`

Entrada: `{ "turma_id": "uuid" }` (opcional; sem ele, todas as turmas do aluno).
Saída, da mais recente para a mais antiga:
```json
{
  "atividades": [
    {
      "tentativa_id": "uuid",
      "atividade": { "id": "uuid", "numero": 2, "titulo": "Probabilidade condicional" },
      "turma": { "id": "uuid", "nome": "Inteligência Artificial", "codigo": "Turma A" },
      "enviada_em": "2026-11-04T21:40:00Z",
      "acertos": 3, "total_multipla_escolha": 4,
      "gabarito_liberado": false
    }
  ]
}
```
- `numero` = ordem da atividade dentro da turma (1, 2, 3…), a mesma que o professor vê.
- Tentativas não enviadas não aparecem.

## Nova ação `revisao_tentativa`

Entrada: `{ "tentativa_id": "uuid" }` (só do próprio aluno; outra pessoa → `nao_matriculado`).
Saída:
```json
{
  "atividade": { "id": "uuid", "numero": 2, "titulo": "Probabilidade condicional" },
  "gabarito_liberado": true,
  "acertos": 3, "total_multipla_escolha": 4,
  "questoes": [
    {
      "questao_id": "2",
      "tipo": "multipla_escolha",
      "enunciado": "…",
      "conceitos": ["Probabilidade condicional"],
      "minha_resposta": { "alternativa_id": "a" },
      "acertou": false,
      "alternativas": [
        { "id": "a", "texto": "…", "correta": false, "feedback": "…" },
        { "id": "b", "texto": "…", "correta": true,  "feedback": "…" }
      ]
    },
    {
      "questao_id": "5",
      "tipo": "aberta",
      "enunciado": "…",
      "conceitos": ["…"],
      "minha_resposta": { "texto": "…" },
      "acertou": null,
      "rubrica": "…"
    }
  ]
}
```
- Com `gabarito_liberado: false`: `correta`, `feedback` e `rubrica` **não vêm** (o campo some, não vem `null`). `acertou` vem sempre.
- `minha_resposta` vazia (`{}`) quando o aluno deixou em branco.
- Sem o gabarito, cada alternativa vem só com `{ "id", "texto" }`; com ele, `{ "id", "texto", "correta", "feedback" }`.
- Só tentativas **enviadas** têm revisão: tentativa de outra pessoa, ainda não enviada, inexistente ou com
  `tentativa_id` fora do formato → `nao_matriculado`.

## Detalhes de implementação (servidor no ar desde a fase 2)

- `gabarito_liberado` = a **sessão daquela tentativa** foi encerrada (`sessoes.aberta = false`). A regra fica no
  Postgres (`revisao_tentativa`, `minhas_atividades`, migration `20261127130000`), não na Edge Function;
  testes em `supabase/tests/database/revisao.test.sql`.
- `numero`: ordem de publicação na turma (`publicada_em`, desempate pelo id), igual à `listar_atividades` da
  turma-api que o professor vê.
- `enviada_em` em ISO 8601 UTC com milissegundos (`2026-11-04T21:40:00.000Z`).
- `minhas_atividades` com `turma_id` fora do formato → `nao_matriculado`; turma sem tentativas → lista vazia.
- `enviar_respostas.questoes` vem na ordem das questões; múltipla escolha em branco conta como `acertou: false`.

## Mudança em `entrar_sessao`: entrar na turma pelo QR

Problema dos testes de novembro: quem leu o QR da sessão sem ter aceitado o convite ficava sem saída.

**Quem não é membro da turma** recebe `nao_matriculado` (403) com a turma da sessão, para o site oferecer
"Entrar na turma e começar":
```json
{
  "erro": "nao_matriculado",
  "mensagem": "Você ainda não está nesta turma. Entre nela para responder a atividade.",
  "turma": { "id": "uuid", "nome": "Inteligência Artificial", "codigo": "Turma A" }
}
```

**Entrada nova** `entrar_na_turma: true`:
```json
{ "acao": "entrar_sessao", "token": "…", "entrar_na_turma": true }
```
Matricula o aluno na turma e entra na sessão na mesma chamada; a saída é a de sempre (a tentativa).
Vale só com:
- token válido e **recente** (o QR na tela agora; um token velho → `qr_expirado`);
- sessão aberta e com a entrada aberta (senão `sessao_encerrada`, e o aluno **não** fica na turma).

Para quem já é membro, `entrar_na_turma` é ignorado (nada muda).

**O professor da turma nunca vira aluno dela**: com ou sem `entrar_na_turma`, recebe `nao_matriculado`
**sem** o campo `turma` e a mensagem "Você é o professor desta turma. Para responder como aluno, use outra
conta Google." (o site mostra o erro, sem oferecer a entrada).

No banco: `entrar_na_turma_pela_sessao(sessao, aluno)` (migration `20261127120000`, só a `service_role`),
testes em `supabase/tests/database/entrar_pela_sessao.test.sql`.

## Fase 2b: alternativas embaralhadas e confiança na resposta

Só acréscimos: o site atual continua funcionando sem mudar nada.

### Alternativas embaralhadas por tentativa (anti-cola em sala)

- Em `entrar_sessao`, as alternativas de cada questão vêm numa **ordem própria da tentativa**: sorteio
  determinístico a partir do `tentativa_id`. Recarregar a página (ou ler o QR de novo) mostra a mesma ordem;
  dois alunos quase sempre veem ordens diferentes. A ordem das **questões** não muda.
- Os **ids continuam os originais** (`"a"`, `"b"`...): uma alternativa `{ "id": "c", ... }` pode vir em
  primeiro lugar. `enviar_respostas` continua recebendo o `alternativa_id` (o id), e a correção não muda.
- **O site deve rotular A, B, C... pela POSIÇÃO na lista, nunca pelo id.** Exemplo: se vierem
  `[{ "id": "c" }, { "id": "a" }, { "id": "b" }]`, os rótulos na tela são A, B, C, e quem toca em "A" envia
  `alternativa_id: "c"`.
- `revisao_tentativa` devolve as alternativas na mesma ordem que o aluno viu (com o gabarito, quando liberado).

### Confiança na resposta

`enviar_respostas` aceita, em cada resposta, `confianca` opcional:
```json
{ "questao_id": "2", "alternativa_id": "c", "confianca": "certeza" }
```
- Valores: `"certeza"` | `"acho"` | `"chute"` (maiúsculas e espaços nas pontas são aceitos). Sem o campo,
  ou com outro valor, fica `null` (não é erro). Vale também para as abertas.
- O domínio estimado (BKT) **não muda** com a confiança: ela só é guardada e lida.
- `revisao_tentativa` ganha `confianca` em cada questão (`"certeza"`, `"acho"`, `"chute"` ou `null`); o
  campo vem sempre, mesmo com o gabarito ainda não liberado (é a resposta do próprio aluno).
- A resposta de `enviar_respostas` não muda.

### Estatísticas de confiança (turma-api, só o professor da turma)

`{ "acao": "confianca", "turma_id": "uuid" }` →
```json
{
  "turma": { "n_respostas": 40, "n_com_confianca": 32, "erros_com_certeza": 5, "acertos_no_chute": 3 },
  "conceitos": [{ "conceito": "Probabilidade condicional", "n_respostas": 12, "n_com_confianca": 10,
                  "erros_com_certeza": 3, "acertos_no_chute": 1 }],
  "alunos": [{ "aluno_id": "uuid", "nome": "Ana", "n_respostas": 4, "n_com_confianca": 4,
               "erros_com_certeza": 1, "acertos_no_chute": 0 }]
}
```
- Só múltipla escolha respondida. Cada questão conta uma vez na turma e no aluno, e uma vez em cada
  conceito dela.
- `erros_com_certeza`: errou dizendo "certeza" (equívoco: o aluno acha que sabe). `acertos_no_chute`: acertou
  dizendo "chute" (o acerto não indica domínio).
- Turma de outro professor → 404, como as outras ações da turma-api.

No banco: migrations `20261128120000_alternativas_embaralhadas` (`chave_alternativa`, `questoes_da_tentativa`)
e `20261128130000_confianca` (`respostas.confianca`, `confianca_turma`); testes em
`supabase/tests/database/embaralhar.test.sql` e `confianca.test.sql`.

## Fase 4: revisão espaçada (flashcards)

Treino fora de sala, no celular, com as **próprias questões que o professor publicou**. Só acréscimos: o
site atual continua funcionando sem mudar nada. Três ações novas na `aluno-api`.

### Regras

- **De onde vêm os cartões:** só questões de **múltipla escolha** publicadas pelo professor (RN04), de
  atividades das turmas do aluno com o **gabarito liberado**: a atividade teve pelo menos uma sessão
  **encerrada** e não tem nenhuma aberta agora. Se o professor reabrir a atividade em sala, os cartões dela
  somem até ele encerrar de novo (nada vaza para quem está respondendo). Questões abertas não viram cartão.
- **Nada é gerado:** nenhuma chamada à IA. Um cartão é uma questão (aluno × atividade × questão).
- **Fora do domínio estimado:** o treino **não** entra no BKT nem no mapa do aluno ou da turma. Fica num
  registro próprio (só inserção). O professor vê só quantos treinos e o acerto no treino.
- **Caixas de Leitner (1, 2, 3):** acerto sobe uma caixa (máximo 3); erro volta para a 1. A próxima revisão
  fica para daqui a **1, 3 ou 7 dias** (caixa 1, 2, 3). Cartão novo começa na caixa 1.
- **"Hoje"** é o dia no fuso de Brasília (`America/Sao_Paulo`).
- **Só conta para a caixa** a primeira resposta a um cartão que está para hoje (vencido ou novo). Responder de
  novo no mesmo dia (ou um cartão que ainda não venceu) registra o treino, mas a caixa não muda
  (`contou_para_caixa: false`).
- **O baralho**, na ordem:
  1. cartões **vencidos** (próxima revisão hoje ou antes; o mais atrasado primeiro, depois a caixa menor);
  2. cartões **novos** das questões ligadas a conceitos em que o domínio estimado do aluno está em
     **"revisar"** (primeiro) ou **"em desenvolvimento"** (do menor domínio para o maior). Conceitos
     consolidados ou sem dados não geram cartões novos.
- **Alternativas embaralhadas, ordem fixa por cartão** (como nas tentativas): o mesmo cartão vem sempre na
  mesma ordem. Os **ids são os originais**; o site rotula A, B, C **pela posição**.

### `revisao_baralho`

Entrada: `{ "acao": "revisao_baralho", "turma_id": "uuid", "limite": 10 }`
- `turma_id` opcional (sem ele: todas as turmas do aluno); fora do formato → `nao_matriculado`.
- `limite` opcional, padrão 10, de 1 a 50.

Saída (sem `correta`, `feedback` nem `rubrica`: o gabarito só vem depois de responder):
```json
{
  "cartoes": [
    {
      "cartao_id": "6f1c2a3b-4d5e-6f70-8192-a3b4c5d6e7f8",
      "turma": { "id": "2b8c1e4a-0f3d-4c7b-9a61-5e2d8f7c3b10", "nome": "Inteligência Artificial", "codigo": "Turma A" },
      "atividade": { "id": "9d4e7a21-3c5b-4f86-a2d0-1b7e6c9f8a35", "numero": 2, "titulo": "Probabilidade condicional" },
      "questao_id": "3",
      "enunciado": "Num baralho comum, qual a probabilidade de tirar um rei sabendo que a carta é de copas?",
      "conceitos": ["Probabilidade condicional"],
      "alternativas": [
        { "id": "c", "texto": "1/13" },
        { "id": "a", "texto": "1/52" },
        { "id": "d", "texto": "4/13" },
        { "id": "b", "texto": "1/4" }
      ],
      "caixa": 2,
      "novo": false
    },
    {
      "cartao_id": "0a9b8c7d-6e5f-4a3b-2c1d-0e9f8a7b6c5d",
      "turma": { "id": "2b8c1e4a-0f3d-4c7b-9a61-5e2d8f7c3b10", "nome": "Inteligência Artificial", "codigo": "Turma A" },
      "atividade": { "id": "4c3b2a19-8d7e-4f6a-b5c4-d3e2f1a0b9c8", "numero": 1, "titulo": "Espaço amostral e eventos" },
      "questao_id": "1",
      "enunciado": "Qual destes é o evento complementar de \"sair número par\" num dado?",
      "conceitos": ["Evento complementar"],
      "alternativas": [
        { "id": "b", "texto": "Sair número ímpar" },
        { "id": "a", "texto": "Sair 6" },
        { "id": "c", "texto": "Sair número primo" }
      ],
      "caixa": 1,
      "novo": true
    }
  ],
  "pendentes_hoje": 7,
  "total_cartoes": 12
}
```
- `pendentes_hoje`: vencidos + novos disponíveis hoje (sem o limite). Com 0, o site mostra "Nada para revisar hoje".
- `total_cartoes`: cartões que o aluno já respondeu ao menos uma vez (nas atividades que podem ser treinadas).
- `novo: true` = o aluno ainda não respondeu este cartão (`caixa` é 1).
- `cartao_id` é estável: o mesmo cartão tem sempre o mesmo id.
- Turma sem atividades encerradas, ou aluno sem conceitos em "revisar"/"em desenvolvimento" e sem vencidos →
  `{ "cartoes": [], "pendentes_hoje": 0, "total_cartoes": ... }`.

### `revisao_responder`

Entrada:
```json
{ "acao": "revisao_responder", "cartao_id": "6f1c2a3b-4d5e-6f70-8192-a3b4c5d6e7f8", "alternativa_id": "c", "confianca": "acho" }
```
- `confianca` opcional (`"certeza"` | `"acho"` | `"chute"`; outro valor = não informado), como em `enviar_respostas`.

Saída (gabarito e feedback de todas as alternativas, na mesma ordem do baralho):
```json
{
  "acertou": true,
  "alternativa_correta": "c",
  "alternativas": [
    { "id": "c", "texto": "1/13", "correta": true,  "feedback": "Isso: são 13 cartas de copas e uma delas é o rei." },
    { "id": "a", "texto": "1/52", "correta": false, "feedback": "Essa é a probabilidade sem a condição \"é de copas\"." },
    { "id": "d", "texto": "4/13", "correta": false, "feedback": "Há só um rei de copas, não quatro." },
    { "id": "b", "texto": "1/4",  "correta": false, "feedback": "1/4 é a chance de a carta ser de copas." }
  ],
  "nova_caixa": 3,
  "proxima_revisao": "2026-12-08",
  "contou_para_caixa": true
}
```
- `proxima_revisao`: data (`AAAA-MM-DD`, dia de Brasília) em que o cartão volta ao baralho.
- `contou_para_caixa: false` (segunda resposta no mesmo dia, ou cartão ainda não vencido): `nova_caixa` e
  `proxima_revisao` são os de antes; o treino fica registrado.

Erros:

| Situação | Código | Status |
|---|---|---|
| `cartao_id` fora do formato, de outra pessoa, inexistente, ou de atividade que não pode ser treinada agora (reaberta em sala, aluno saiu da turma) | `nao_matriculado` | 403 |
| `alternativa_id` ausente ou que não existe na questão | `erro_interno` | 400 |
| Sem login (qualquer 401) | `nao_autenticado` | 401 |

### `revisao_resumo`

Entrada: `{ "acao": "revisao_resumo", "turma_id": "uuid" }` (opcional; sem ele, todas as turmas do aluno, na
ordem em que entrou nelas).

Saída:
```json
{
  "turmas": [
    {
      "turma": { "id": "2b8c1e4a-0f3d-4c7b-9a61-5e2d8f7c3b10", "nome": "Inteligência Artificial", "codigo": "Turma A" },
      "pendentes_hoje": 7,
      "total_cartoes": 12,
      "cartoes_por_caixa": { "1": 5, "2": 4, "3": 3 },
      "treinos_7_dias": 18,
      "acertos_7_dias": 13,
      "conceitos_com_mais_cartoes": [
        { "conceito": "Probabilidade condicional", "n_cartoes": 5 },
        { "conceito": "Evento complementar", "n_cartoes": 4 }
      ]
    }
  ]
}
```
- `cartoes_por_caixa` e `total_cartoes`: só cartões já respondidos. `pendentes_hoje` é o mesmo do baralho.
- `treinos_7_dias`: respostas a cartões nos últimos 7 dias (incluindo as que não contaram para a caixa).
- `conceitos_com_mais_cartoes`: até 5, do maior para o menor (uma questão com dois conceitos conta nos dois).
- Turma em que o aluno não está → lista vazia.

### Professor (turma-api, só o dono da turma)

`{ "acao": "treino_turma", "turma_id": "uuid" }` →
```json
{
  "resumo": { "n_alunos": 25, "n_alunos_treinando": 9, "treinos_7_dias": 140, "n_treinos": 310, "taxa_acerto": 0.7129 },
  "alunos": [
    { "aluno_id": "uuid", "nome": "Ana", "treinos_7_dias": 18, "n_treinos": 40, "acertos": 31, "taxa_acerto": 0.775,
      "cartoes_por_caixa": { "1": 2, "2": 4, "3": 6 }, "ultimo_treino_em": "2026-12-01T22:10:03.120Z" }
  ],
  "conceitos": [{ "conceito": "Probabilidade condicional", "n_alunos_treinando": 6, "treinos_7_dias": 52 }]
}
```
- `n_alunos_treinando` = alunos com ao menos um treino nos últimos 7 dias; `taxa_acerto` sobre todos os
  treinos (null sem treinos). Todos os membros aparecem em `alunos`, com zeros se não treinaram.
- `historico_conceito` ganha `treino: { "n_treinos": 6, "acertos": 4 }` (o aluno naquele conceito).
- Os conceitos contados são os **atuais** da questão (acompanham a fusão de conceitos); o registro de cada
  treino guarda os nomes do momento.

No banco: migration `20261130120000_revisao_espacada` (`cartoes`, `treinos`, `revisao_baralho`,
`revisao_responder`, `revisao_resumo`, `treino_turma`, `treino_aluno_conceito`); testes em
`supabase/tests/database/revisao_espacada.test.sql`.
