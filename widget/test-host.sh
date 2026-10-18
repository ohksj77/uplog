#!/usr/bin/env bash
# Electron 앱에 넣기 전에 위젯만 시험한다: 작은 호스트 앱(~/Applications/UplogWidgetTest.app, dev.uplog.app.test)에
# 위젯(dev.uplog.app.test.widget)을 넣어 등록하고 한 번 띄운다. 그 뒤 바탕화면 우클릭 → 위젯 편집 → "UplogWidgetTest"의 "업로그 로그".
#   widget/test-host.sh            설치·등록
#   widget/test-host.sh --remove   지우기(등록 해제)
# 위젯이 읽는 파일: ~/Library/Application Support/uplog/widget/widget.json (엔진이 쓴다. 없으면 "업로그 앱을 열어 주세요")
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="UplogWidgetTest"
HOST_ID="dev.uplog.app.test"
WIDGET_ID="$HOST_ID.widget"
DEST="$HOME/Applications/$APP_NAME.app"
LSREGISTER="/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"

if [[ "${1:-}" == "--remove" ]]; then
  pluginkit -r "$DEST/Contents/PlugIns/UplogWidget.appex" 2>/dev/null || true
  "$LSREGISTER" -u "$DEST" 2>/dev/null || true
  rm -rf "$DEST"
  echo "✓ 지움: $DEST"
  exit 0
fi

BUILD="$HERE/build/test"
BUNDLE_ID="$WIDGET_ID" OUT="$BUILD" "$HERE/build.sh"

APP="$BUILD/$APP_NAME.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/PlugIns"
cp -R "$BUILD/UplogWidget.appex" "$APP/Contents/PlugIns/"

# 호스트는 아무것도 하지 않고 끝난다(위젯 확장을 시스템에 알리려면 한 번 실행돼야 한다).
cat > "$BUILD/Host.swift" <<'SWIFT'
import Foundation
print("UplogWidgetTest: 위젯을 등록했습니다. 바탕화면에서 위젯 편집을 여세요.")
SWIFT
swiftc -target arm64-apple-macos14.0 -sdk "$(xcrun --sdk macosx --show-sdk-path)" -O \
  -o "$APP/Contents/MacOS/$APP_NAME" "$BUILD/Host.swift"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleIdentifier</key><string>$HOST_ID</string>
  <key>CFBundleExecutable</key><string>$APP_NAME</string>
  <key>CFBundleName</key><string>$APP_NAME</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleShortVersionString</key><string>0.1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSUIElement</key><true/>
</dict>
</plist>
PLIST

# 서명 순서: appex(자기 entitlements로, build.sh가 이미 함) → 앱. --deep은 쓰지 않는다(확장 entitlements가 지워진다).
codesign --force --sign - "$APP"
codesign --verify --strict --verbose=1 "$APP/Contents/PlugIns/UplogWidget.appex"
codesign --verify --strict --verbose=1 "$APP"

mkdir -p "$HOME/Applications"
rm -rf "$DEST"
cp -R "$APP" "$DEST"
"$LSREGISTER" -f "$DEST"
open "$DEST"
sleep 2
pluginkit -m -p com.apple.widgetkit-extension -v | grep -F "$WIDGET_ID" || {
  echo "아직 pluginkit 목록에 없습니다. 몇 초 뒤 다시 확인하세요: pluginkit -m -p com.apple.widgetkit-extension -v | grep $WIDGET_ID" >&2
  exit 1
}
echo "✓ $DEST 설치·등록. 바탕화면 우클릭 → 위젯 편집 → $APP_NAME"
