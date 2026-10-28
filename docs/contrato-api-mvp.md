# Lumini — Contrato da API do MVP (avaliação por pares, 28/10/2026)

Este arquivo é o acordo entre o **back-end** (Supabase, branch `desktop`) e o **site do aluno** (`web-aluno/`, branch `web-aluno`).
Quem precisar mudar algo aqui avisa o outro antes. Campos novos podem ser acrescentados; renomear ou remover, não.

## Geral

- Projeto Supabase: `https://vuxnyjkykftecqgznswg.supabase.co` · chave pública `sb_publishable_…` (a mesma do desktop; pública por natureza).
- Login: **Google via Supabase Auth** (`supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo } })`).
- Toda chamada do aluno vai para **uma** Edge Function, `aluno-api`, com o JWT do usuário logado (o supabase-js envia sozinho):
  `supabase.functions.invoke('aluno-api', { body: { acao: '<nome>', ...params } })`
- Resposta de sucesso: o JSON descrito abaixo, HTTP 200.
- Resposta de erro: `{ "erro": "<código>", "mensagem": "<texto em PT-BR para mostrar ao usuário>" }` com status 4xx/5xx.
  Códigos usados: `nao_autenticado`, `convite_invalido`, `qr_expirado`, `sessao_encerrada`, `nao_matriculado`, `tentativa_ja_enviada`, `tempo_esgotado`, `erro_interno`.
- O aluno **nunca** recebe gabarito, alternativa correta, rubrica ou feedback antes de enviar.
- Datas em ISO 8601 UTC. Domínio estimado de 0 a 1 (mostrar como %; é probabilidade, não nota).

## URLs do site (o desktop gera essas URLs)

| URL | Uso |
|---|---|
| `/convite/<codigo>` | Link de convite para a turma. Código curto, maiúsculas e dígitos, ex.: `K7QF2M`. |
| `/s/<token>` | Conteúdo do QR code da sessão em sala. O token muda a cada ~20 s. |

O site é uma SPA: a hospedagem precisa redirecionar qualquer caminho para `index.html`.
Se o usuário não estiver logado ao abrir uma dessas URLs, o site faz login com Google e **volta para a mesma URL** depois.

## Ações

### `minhas_turmas`
Entrada: `{}`
Saída:
```json
{ "turmas": [ { "id": "uuid", "nome": "Inteligência Artificial", "codigo": "Turma A", "professor_nome": "Júlio Lucena" } ] }
```

### `aceitar_convite`
Entrada: `{ "codigo": "K7QF2M" }`
Saída: `{ "turma": { "id": "uuid", "nome": "...", "codigo": "...", "professor_nome": "..." }, "ja_era_membro": false }`
Idempotente: aceitar duas vezes não dá erro.

### `entrar_sessao`
Entrada: `{ "token": "<do QR>" }`
Saída:
```json
{
  "tentativa_id": "uuid",
  "iniciada_em": "2026-10-28T21:40:00Z",
  "prazo_em": "2026-10-28T22:10:00Z",
  "ja_enviada": false,
  "atividade": {
    "id": "uuid",
    "titulo": "Busca competitiva em jogos",
    "turma": { "id": "uuid", "nome": "...", "codigo": "..." },
    "questoes": [
      {
        "id": "uuid-ou-indice",
        "tipo": "multipla_escolha",
        "enunciado": "Texto com LaTeX entre $...$",
        "conceitos": ["Poda Alfa-Beta"],
        "alternativas": [ { "id": "a", "texto": "..." }, { "id": "b", "texto": "..." } ]
      },
      { "id": "...", "tipo": "aberta", "enunciado": "...", "conceitos": ["..."], "alternativas": null }
    ]
  }
}
```
- `prazo_em` é `null` quando não há tempo máximo.
- Idempotente: ler o mesmo QR de novo (ou recarregar a página) devolve a mesma tentativa, com `ja_enviada` atualizado.
- Erros esperados: `qr_expirado` (peça para ler o QR de novo), `sessao_encerrada`, `nao_matriculado` (oriente a usar o link de convite da turma).

### `enviar_respostas`
Entrada:
```json
{ "tentativa_id": "uuid", "respostas": [ { "questao_id": "...", "alternativa_id": "b" }, { "questao_id": "...", "texto": "resposta aberta" } ] }
```
Saída:
```json
{ "acertos": 3, "total_multipla_escolha": 4, "abertas_aguardando_correcao": 1 }
```
- Envio único por tentativa (`tentativa_ja_enviada` se repetir). Depois do prazo: `tempo_esgotado`.
- No MVP o aluno vê só "acertou X de Y"; gabarito e feedback ficam para a entrega final.

### `meu_grafo`
Entrada: `{ "turma_id": "uuid" }`
Saída:
```json
{
  "conceitos": [
    { "nome": "Algoritmo Minimax", "prerequisitos": ["Jogos de Soma Zero"], "dominio": 0.82, "estado": "consolidado", "n_respostas": 4 },
    { "nome": "Quiescência", "prerequisitos": ["Decisões em Tempo Real"], "dominio": null, "estado": "sem_dados", "n_respostas": 0 }
  ]
}
```
- `estado`: `consolidado` (✓) · `em_desenvolvimento` (~) · `revisar` (!) · `sem_dados` (?).
- O grafo é acíclico; desenhar em camadas da esquerda para a direita pela profundidade de pré-requisitos (mesma regra do desktop).
