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
