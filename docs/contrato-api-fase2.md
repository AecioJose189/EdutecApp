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
