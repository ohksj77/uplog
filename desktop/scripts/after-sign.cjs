// electron-builder afterSign 훅: UpLog 위젯(WidgetKit 확장)을 Contents/PlugIns/에 넣고 앱만 다시 서명한다.
//  - appex는 widget/build.sh가 이미 자기 entitlements(샌드박스 + 스냅샷 폴더 읽기)로 서명해 둔다.
//  - 앱은 --deep 없이 다시 서명한다(--deep이면 appex의 entitlements가 지워진다). 앱 자신의 entitlements·플래그는 그대로 둔다.
//  - DMG는 이 훅 뒤에 만들어지므로 위젯이 든 앱이 들어간다.
// appex 위치: UPLOG_WIDGET_APPEX, 기본 widget/build/UplogWidget.appex. 없으면 위젯 없이 넘어간다(UPLOG_REQUIRE_WIDGET=1이면 실패).
const { execFileSync } = require("node:child_process");
const { cpSync, existsSync, mkdirSync, rmSync } = require("node:fs");
const { join, resolve } = require("node:path");

exports.default = async function afterSign(context) {
  if (context.electronPlatformName !== "darwin") return;
  const appPath = join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`);
  const appex = resolve(process.env.UPLOG_WIDGET_APPEX ?? join(__dirname, "..", "..", "widget", "build", "UplogWidget.appex"));
  if (!existsSync(appex)) {
    const msg = `위젯이 없습니다(${appex}). widget/build.sh를 먼저 실행하세요.`;
    if (process.env.UPLOG_REQUIRE_WIDGET === "1") throw new Error(msg);
    console.warn(`  • ${msg} 위젯 없이 만듭니다.`);
    return;
  }
  const run = (...args) => execFileSync("codesign", args, { stdio: "inherit" });
  // 위젯 서명이 살아 있는지(샌드박스 entitlements 포함) 먼저 확인
  run("--verify", "--strict", appex);

  const plugins = join(appPath, "Contents", "PlugIns");
  mkdirSync(plugins, { recursive: true });
  const target = join(plugins, "UplogWidget.appex");
  rmSync(target, { recursive: true, force: true });
  cpSync(appex, target, { recursive: true, verbatimSymlinks: true });

  console.log(`  • 위젯 넣음: ${target}`);
  run("--force", "--sign", "-", "--preserve-metadata=entitlements,requirements,flags,runtime", appPath);
  run("--verify", "--strict", "--verbose=1", appPath);
};
