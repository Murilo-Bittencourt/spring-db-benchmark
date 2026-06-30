#!/usr/bin/env bash
# Gera o site estático do GitHub Pages em docs/.
# Copia os resultados de results/*.json para docs/data/ e cria docs/data/manifest.json
# (lista os arquivos em ordem decrescente — o mais recente primeiro).
#
# Uso: bash scripts/build-pages.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/results"
DST="$ROOT/docs/data"

mkdir -p "$DST"
rm -f "$DST"/*.json

if [ ! -d "$SRC" ] || [ -z "$(ls -A "$SRC"/*.json 2>/dev/null || true)" ]; then
  echo "[]" > "$DST/manifest.json"
  echo "Nenhum resultado em $SRC — manifesto vazio."
  exit 0
fi

cp "$SRC"/*.json "$DST"/

# manifest.json = array JSON dos nomes, ordem decrescente (mais novo primeiro)
{
  echo "["
  ls "$DST"/benchmark-*.json 2>/dev/null | xargs -n1 basename | sort -r | \
    awk 'NR>1{printf ",\n"} {printf "  \"%s\"", $0} END{print ""}'
  echo "]"
} > "$DST/manifest.json"

echo "Site pronto em docs/. Arquivos:"
cat "$DST/manifest.json"
