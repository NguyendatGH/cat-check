#!/usr/bin/env bash
# Tải model embedding cục bộ cho RAG (multilingual-e5-small, ONNX int8, MIT) vào backend/data/models.
# Revision và SHA-256 được ghim: đổi model/phiên bản = đổi index vector (xem docs/AI_HYBRID_RAG_PLAN.md mục 3.4).
set -euo pipefail

REPO="Xenova/multilingual-e5-small"
REVISION="761b726dd34fb83930e26aab4e9ac3899aa1fa78"
DEST="${1:-$(dirname "$0")/../data/models/multilingual-e5-small}"

declare -A FILES=(
  ["onnx/model_quantized.onnx"]="f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193"
  ["tokenizer.json"]="0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39"
)

mkdir -p "$DEST"
for path in "${!FILES[@]}"; do
  target="$DEST/$(basename "$path")"
  if [[ -f "$target" ]] && echo "${FILES[$path]}  $target" | sha256sum --check --status; then
    echo "ok (đã có): $target"
    continue
  fi
  echo "tải: $path"
  curl -fL --retry 3 -o "$target.part" "https://huggingface.co/$REPO/resolve/$REVISION/$path"
  echo "${FILES[$path]}  $target.part" | sha256sum --check --status || { echo "SHA-256 sai: $path" >&2; rm -f "$target.part"; exit 1; }
  mv "$target.part" "$target"
done
echo "Model sẵn sàng ở $DEST — đặt CATCHECK_AI_EMBEDDING_MODEL_DIR=$(cd "$DEST" && pwd)"
