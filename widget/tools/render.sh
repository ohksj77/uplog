#!/usr/bin/env bash
# 위젯 화면을 PNG로(위젯 갤러리 없이 모양 검토용).
#   widget/tools/render.sh [sample.json] [out-dir]
# 기본: widget/tools/sample-widget.json(엔진이 데모 계좌로 쓴 스냅샷) → /private/tmp/claude-501/shots
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../Sources"
SAMPLE="${1:-$HERE/sample-widget.json}"
OUT_DIR="${2:-/private/tmp/claude-501/shots}"
BIN="$HERE/../build/render"

mkdir -p "$(dirname "$BIN")"
swiftc \
  -parse-as-library \
  -target arm64-apple-macos14.0 \
  -sdk "$(xcrun --sdk macosx --show-sdk-path)" \
  -swift-version 5 \
  -o "$BIN" \
  "$SRC/Snapshot.swift" "$SRC/Palette.swift" "$SRC/Views.swift" "$HERE/Render.swift"
"$BIN" "$SAMPLE" "$OUT_DIR"
