#!/usr/bin/env bash
# Envia um PDF para a Edge Function gerar-atividade e salva a resposta em amostras/resultado.json.
#
# Uso: scripts/testar-geracao.sh [arquivo.pdf] [quantidade] [formato] [dificuldade] [forcar_nova]
#   arquivo.pdf  padrão: o primeiro PDF de amostras/
#   quantidade   1–10 (padrão 5)
#   formato      multipla_escolha | aberta | mista (padrão mista)
#   dificuldade  facil | media | dificil (padrão media)
#   forcar_nova  true para gerar de novo mesmo com resultado salvo (padrão false)
#
# URL e chave pública (publishable ou anon) vêm das variáveis SUPABASE_URL e SUPABASE_CHAVE_PUBLICA
# (ou SUPABASE_PUBLISHABLE_KEY / SUPABASE_ANON_KEY), ou de SUPABASE_URL e SUPABASE_CHAVE_PUBLICA
# em local.properties (as mesmas que o app lê).
#
# Desde o MVP com login, a função exige o JWT de um usuário (verify_jwt = true). O script usa, nesta
# ordem: a variável SUPABASE_JWT, ou o access_token da sessão do Lumini desktop
# (~/.config/lumini/sessao.json; no Windows %APPDATA%/Lumini/sessao.json) — entre no app antes.
set -euo pipefail
export LC_NUMERIC=C  # ponto decimal no printf, mesmo com locale pt_BR

raiz="$(cd "$(dirname "$0")/.." && pwd)"

ler_propriedade() {
  [ -f "$raiz/local.properties" ] || return 0
  sed -n "s/^$1[[:space:]]*=[[:space:]]*//p" "$raiz/local.properties" | tail -n 1
}

url="${SUPABASE_URL:-$(ler_propriedade SUPABASE_URL)}"
chave="${SUPABASE_CHAVE_PUBLICA:-${SUPABASE_PUBLISHABLE_KEY:-${SUPABASE_ANON_KEY:-}}}"
chave="${chave:-$(ler_propriedade SUPABASE_CHAVE_PUBLICA)}"

if [ -z "$url" ] || [ -z "$chave" ]; then
  echo "Defina SUPABASE_URL e SUPABASE_CHAVE_PUBLICA (no ambiente ou em local.properties)." >&2
  exit 1
fi

sessao="${LUMINI_SESSAO:-${XDG_CONFIG_HOME:-$HOME/.config}/lumini/sessao.json}"
[ -f "$sessao" ] || sessao="${APPDATA:-}/Lumini/sessao.json"
jwt="${SUPABASE_JWT:-}"
if [ -z "$jwt" ] && [ -f "$sessao" ]; then
  jwt="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$sessao" | head -n 1)"
fi
if [ -z "$jwt" ]; then
  echo "Sem JWT: entre no Lumini desktop (a sessão fica em $sessao) ou defina SUPABASE_JWT." >&2
  exit 1
fi

pdf="${1:-$(find "$raiz/amostras" -maxdepth 1 -iname '*.pdf' 2>/dev/null | sort | head -n 1)}"
if [ -z "$pdf" ] || [ ! -f "$pdf" ]; then
  echo "Nenhum PDF encontrado. Coloque um em amostras/ ou passe o caminho como 1º argumento." >&2
  exit 1
fi

quantidade="${2:-5}"
formato="${3:-mista}"
dificuldade="${4:-media}"
forcar_nova="${5:-false}"
saida="$raiz/amostras/resultado.json"
mkdir -p "$raiz/amostras"
bruto="$(mktemp)"
trap 'rm -f "$bruto"' EXIT

echo "Enviando $(basename "$pdf") ($(du -h "$pdf" | cut -f1)): $quantidade questões, $formato, $dificuldade..."
inicio=$(date +%s.%N)
status=$(curl -sS -o "$bruto" -w '%{http_code}' --max-time 160 \
  -X POST "${url%/}/functions/v1/gerar-atividade" \
  -H "apikey: $chave" \
  -H "Authorization: Bearer $jwt" \
  -F "pdf=@$pdf;type=application/pdf" \
  -F "quantidade=$quantidade" \
  -F "formato=$formato" \
  -F "dificuldade=$dificuldade" \
  -F "forcar_nova=$forcar_nova")
fim=$(date +%s.%N)

if command -v jq >/dev/null 2>&1 && jq . "$bruto" > "$saida" 2>/dev/null; then :; else cp "$bruto" "$saida"; fi

printf 'HTTP %s em %.1f s. Resposta salva em %s\n' "$status" "$(awk "BEGIN{print $fim - $inicio}")" "$saida"
if [ "$status" != "200" ]; then
  cat "$saida" >&2
  exit 1
fi
if command -v jq >/dev/null 2>&1; then
  jq -r '"reaproveitado: \(.reaproveitado) · gerado em \(.gerado_em) · \(.questoes | length) questões"' "$saida"
fi
