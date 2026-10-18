#!/usr/bin/env bash
# 업로그 로그 위젯(WidgetKit 확장)을 Xcode 없이 swiftc로 빌드한다.
#   widget/build.sh                       → widget/build/UplogWidget.appex (번들 id dev.uplog.app.widget)
#   BUNDLE_ID=… OUT=… widget/build.sh     → 다른 번들 id·위치(test-host.sh가 쓴다)
# 필요: Swift 툴체인 + macOS SDK(Command Line Tools면 된다). arm64, macOS 14 이상.
# 결과는 ad-hoc 서명(codesign -s -)되어 있다. 호스트 앱(dev.uplog.app)의 Contents/PlugIns/에 넣고, 앱을 서명하기 전에 이 appex를
# 먼저 서명해야 한다(이 스크립트가 이미 해 둔다. 앱 서명 때 --deep을 쓰지 말 것: 확장의 entitlements가 지워진다).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BUNDLE_ID="${BUNDLE_ID:-dev.uplog.app.widget}"
OUT="${OUT:-$HERE/build}"
NAME="UplogWidget"
VERSION="${VERSION:-0.1.0}"
BUILD_NUMBER="${BUILD_NUMBER:-1}"
MIN_MACOS="14.0"

APPEX="$OUT/$NAME.appex"
SDK="$(xcrun --sdk macosx --show-sdk-path)"

rm -rf "$APPEX"
mkdir -p "$APPEX/Contents/MacOS" "$OUT/tmp"

echo "▸ swiftc ($BUNDLE_ID)"
swiftc \
  -parse-as-library \
  -application-extension \
  -target "arm64-apple-macos$MIN_MACOS" \
  -sdk "$SDK" \
  -swift-version 5 \
  -O \
  -module-name UplogWidget \
  -framework WidgetKit -framework SwiftUI \
  -Xlinker -e -Xlinker _NSExtensionMain \
  -o "$APPEX/Contents/MacOS/$NAME" \
  "$HERE"/Sources/*.swift

cat > "$APPEX/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key><string>ko</string>
  <key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
  <key>CFBundleExecutable</key><string>$NAME</string>
  <key>CFBundleName</key><string>$NAME</string>
  <key>CFBundleDisplayName</key><string>업로그 로그</string>
  <key>CFBundlePackageType</key><string>XPC!</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$BUILD_NUMBER</string>
  <key>CFBundleSupportedPlatforms</key><array><string>MacOSX</string></array>
  <key>LSMinimumSystemVersion</key><string>$MIN_MACOS</string>
  <key>NSExtension</key>
  <dict>
    <key>NSExtensionPointIdentifier</key><string>com.apple.widgetkit-extension</string>
  </dict>
</dict>
</plist>
PLIST

# 샌드박스 + 엔진이 쓰는 스냅샷 폴더만 읽기(App Group은 Team ID가 없어 못 쓴다).
ENTITLEMENTS="$OUT/$NAME.entitlements"
cat > "$ENTITLEMENTS" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.security.app-sandbox</key><true/>
  <key>com.apple.security.temporary-exception.files.home-relative-path.read-only</key>
  <array>
    <string>/Library/Application Support/uplog/widget/</string>
  </array>
</dict>
</plist>
PLIST

echo "▸ codesign (ad-hoc)"
codesign --force --sign - --entitlements "$ENTITLEMENTS" --generate-entitlement-der "$APPEX"
codesign --verify --strict --verbose=1 "$APPEX"
rm -rf "$OUT/tmp"
echo "✓ $APPEX"
