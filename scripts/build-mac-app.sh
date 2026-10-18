#!/usr/bin/env bash
# 맥 앱(.app·.dmg, arm64, 서명 없음/ad-hoc)을 만든다. docs/design/desktop-app.md "빌드"
#   scripts/build-mac-app.sh            → desktop/dist-app/mac-arm64/UpLog.app, desktop/dist-app/UpLog-0.1.0-arm64.dmg
#   SKIP_WEB=1 SKIP_ENGINE=1 SKIP_RUNTIME=1 SKIP_WIDGET=1 로 이미 만든 단계를 건너뛸 수 있다.
# 위젯(widget/build/UplogWidget.appex)은 electron-builder afterSign 훅(desktop/scripts/after-sign.cjs)이
# Contents/PlugIns/에 넣고 앱만 --deep 없이 다시 서명한다(위젯의 샌드박스 entitlements를 지키려고).
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP="$REPO/desktop"
JAR="$REPO/app/build/libs/app-0.1.0-SNAPSHOT.jar"

step() { printf '\n== %s\n' "$1"; }

if [[ "${SKIP_WEB:-}" != 1 ]]; then
  step "화면 정적 export (web/out)"
  (cd "$REPO/web" && { [[ -d node_modules ]] || npm ci; } && npm run build)
fi

if [[ "${SKIP_ENGINE:-}" != 1 ]]; then
  step "엔진 jar (./gradlew :app:bootJar)"
  (cd "$REPO" && ./gradlew :app:bootJar)
fi

if [[ "${SKIP_RUNTIME:-}" != 1 ]]; then
  step "Java 런타임 (jdeps + jlink)"
  "$DESKTOP/scripts/build-runtime.sh" "$JAR"
fi

if [[ "${SKIP_WIDGET:-}" != 1 ]]; then
  step "위젯 (widget/build.sh → widget/build/UplogWidget.appex)"
  "$REPO/widget/build.sh"
fi

step "런타임으로 엔진이 뜨는지 확인 (/__health)"
CHECK_DIR="$(mktemp -d)"
PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1])')"
TOKEN="$(head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=')"
"$DESKTOP/build/runtime/bin/java" -Djava.awt.headless=true -jar "$JAR" --spring.profiles.active=desktop \
  --server.address=127.0.0.1 --server.port="$PORT" --uplog.desktop.launch-token="$TOKEN" \
  --uplog.desktop.data-dir="$CHECK_DIR" --uplog.desktop.web-dir="$REPO/web/out" \
  --uplog.credential.key-store=file --logging.threshold.console=OFF --spring.main.banner-mode=off &
ENGINE_PID=$!
ok=0
for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:$PORT/__health" >/dev/null; then ok=1; break; fi
  kill -0 "$ENGINE_PID" 2>/dev/null || break
  sleep 0.5
done
kill "$ENGINE_PID" 2>/dev/null || true
wait "$ENGINE_PID" 2>/dev/null || true
rm -rf "$CHECK_DIR"
[[ $ok == 1 ]] || { echo "런타임으로 엔진이 뜨지 않습니다. 모듈 목록(desktop/scripts/build-runtime.sh)을 확인하세요." >&2; exit 1; }
echo "health 200"

step "Electron 리소스 모으기 (desktop/build)"
rm -rf "$DESKTOP/build/web" "$DESKTOP/build/engine.jar"
cp "$JAR" "$DESKTOP/build/engine.jar"
cp -R "$REPO/web/out" "$DESKTOP/build/web"

step "electron-builder (mac arm64, 서명 없음)"
cd "$DESKTOP"
[[ -d node_modules ]] || npm ci
rm -rf dist dist-app
UPLOG_REQUIRE_WIDGET=1 npm run dist

APP="$(ls -d "$DESKTOP"/dist-app/mac-arm64/*.app)"
DMG="$(ls "$DESKTOP"/dist-app/*.dmg)"
step "서명 확인"
codesign --verify --strict --verbose=1 "$APP"
codesign -d --entitlements - "$APP/Contents/PlugIns/UplogWidget.appex" 2>/dev/null | grep -q com.apple.security.app-sandbox \
  || { echo "위젯 entitlements(app-sandbox)가 사라졌습니다" >&2; exit 1; }
echo "위젯 app-sandbox entitlements 유지"

step "결과"
du -sh "$APP" "$DMG"
