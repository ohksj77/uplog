// UpLog 맥 앱의 main 프로세스: 로컬 엔진을 띄우고, 창과 메뉴바 아이콘을 둔다.
// 설계: docs/design/desktop-app.md
import {
  app,
  BrowserWindow,
  dialog,
  Menu,
  MenuItemConstructorOptions,
  nativeImage,
  Notification,
  shell,
  Tray,
} from "electron";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { resolveConfig } from "./config";
import { Engine, EngineStartError } from "./engine";
import { RotatingLog } from "./log";

const APP_NAME = "UpLog";
const URL_SCHEME = "uplog";
app.setName(APP_NAME);

const config = resolveConfig(app.isPackaged, process.resourcesPath, process.env);
mkdirSync(config.dataDir, { recursive: true, mode: 0o700 });
const logsDir = join(config.dataDir, "logs");
const log = new RotatingLog(logsDir);

// Electron(크로미움) 자체 데이터도 엔진 data-dir 안에 둔다(검증용 UPLOG_DATA_DIR이면 실제 폴더를 건드리지 않는다).
app.setPath("userData", join(config.dataDir, "electron"));

// 개발 실행에서만: 원격 디버깅 포트(화면 확인용). 설치본은 무시한다.
if (!app.isPackaged && process.env.UPLOG_DEVTOOLS_PORT) {
  app.commandLine.appendSwitch("remote-debugging-port", process.env.UPLOG_DEVTOOLS_PORT);
  app.commandLine.appendSwitch("remote-debugging-address", "127.0.0.1");
}

const engine = new Engine(config, log);
let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let quitting = false;
let crashRestarts = 0;
let status = "시작 중…";

// uplog:// 링크: 앱을 열고 창을 앞으로 가져온다. (macOS는 이미 떠 있는 인스턴스에 open-url로 준다)
app.on("open-url", (event, url) => {
  event.preventDefault();
  log.info(`링크로 열림: ${url.split("?")[0]}`);
  if (app.isReady()) openWindow();
});

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => openWindow());
  void main();
}

async function main(): Promise<void> {
  await app.whenReady();
  log.info(`${APP_NAME} ${app.getVersion()} 시작 (packaged=${app.isPackaged}, electron ${process.versions.electron})`);
  Menu.setApplicationMenu(buildAppMenu());
  createTray();
  registerUrlScheme();

  // 로그인 시 자동 실행으로 켜졌으면 창 없이 메뉴바에서만 돈다
  const openedAtLogin = app.getLoginItemSettings().wasOpenedAtLogin;
  if (openedAtLogin) app.dock?.hide();
  if (await startEngine()) {
    if (!openedAtLogin) openWindow();
  }
}

/** 엔진을 띄운다. 실패하면 "로그 열기·다시 시도·종료"를 묻는다. */
async function startEngine(): Promise<boolean> {
  for (;;) {
    setStatus("엔진 시작 중…");
    try {
      await engine.start();
      setStatus(`실행 중 (127.0.0.1:${engine.port})`);
      return true;
    } catch (e) {
      const message = e instanceof EngineStartError ? e.message : String(e);
      log.info(`엔진 시작 실패: ${message}`);
      setStatus("엔진이 멈춤");
      const choice = await askAfterFailure(`${APP_NAME}를 시작하지 못했어요`, message);
      if (choice === "retry") continue;
      if (choice === "quit") {
        quit();
        return false;
      }
    }
  }
}

/** 오류 창: 로그 열기를 누르면 로그를 열고 다시 묻는다. */
async function askAfterFailure(title: string, detail: string): Promise<"retry" | "quit"> {
  for (;;) {
    const { response } = await dialog.showMessageBox({
      type: "error",
      title: APP_NAME,
      message: title,
      detail: `${detail}\n\n로그에서 원인을 볼 수 있어요: ${logsDir}`,
      buttons: ["다시 시도", "종료", "로그 열기"],
      defaultId: 0,
      cancelId: 1,
    });
    if (response === 0) return "retry";
    if (response === 1) return "quit";
    await shell.openPath(logsDir);
  }
}

engine.on("exited", (code, signal) => {
  void (async () => {
    log.info(`엔진이 예기치 않게 끝났습니다(code=${code}, signal=${signal})`);
    if (quitting) return;
    if (crashRestarts < 1) {
      crashRestarts++;
      log.info("엔진을 한 번 다시 띄웁니다");
      try {
        await engine.start();
        setStatus(`실행 중 (127.0.0.1:${engine.port})`);
        // 새 포트·새 토큰이라 창을 새 실행 주소로 다시 연다
        if (mainWindow && !mainWindow.isDestroyed()) void mainWindow.loadURL(engine.launchUrl);
        return;
      } catch (e) {
        log.info(`다시 띄우기 실패: ${String(e)}`);
      }
    }
    setStatus("엔진이 멈춤");
    const choice = await askAfterFailure(`${APP_NAME} 엔진이 멈췄어요`, `종료 코드 ${code ?? signal}`);
    if (choice === "quit") return quit();
    crashRestarts = 0;
    if (await startEngine()) {
      if (mainWindow && !mainWindow.isDestroyed()) void mainWindow.loadURL(engine.launchUrl);
      else openWindow();
    }
  })();
});

function isEngineUrl(url: string): boolean {
  try {
    return new URL(url).origin === engine.origin;
  } catch {
    return false;
  }
}

/** 엔진 밖 주소는 앱 창에서 열지 않고, http(s)만 기본 브라우저로 넘긴다(tossinvest.com, 토스 문서 등). */
function openExternalSafely(url: string): void {
  try {
    const u = new URL(url);
    if (u.protocol === "https:" || u.protocol === "http:") void shell.openExternal(u.toString());
    else log.info(`열지 않은 주소(프로토콜 ${u.protocol})`);
  } catch {
    // 잘못된 주소는 무시
  }
}

function openWindow(): void {
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
    return;
  }
  if (!engine.running) return;

  app.dock?.show();
  const win = new BrowserWindow({
    title: APP_NAME,
    width: 1200,
    height: 820,
    minWidth: 380,
    minHeight: 560,
    show: false,
    backgroundColor: "#ffffff",
    webPreferences: {
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      nodeIntegrationInSubFrames: false,
      webSecurity: true,
      allowRunningInsecureContent: false,
      webviewTag: false,
      spellcheck: false,
      devTools: !app.isPackaged,
    },
  });
  mainWindow = win;

  const wc = win.webContents;
  wc.setWindowOpenHandler(({ url }) => {
    if (isEngineUrl(url)) void win.loadURL(url);
    else openExternalSafely(url);
    return { action: "deny" };
  });
  wc.on("will-navigate", (event, url) => {
    if (!isEngineUrl(url)) {
      event.preventDefault();
      openExternalSafely(url);
    }
  });
  wc.on("will-redirect", (event, url) => {
    if (!isEngineUrl(url)) event.preventDefault();
  });
  wc.on("will-attach-webview", (event) => event.preventDefault());
  wc.on("did-fail-load", (_e, code, description, url) => log.info(`화면을 못 불러옴: ${code} ${description} ${url}`));
  wc.on("render-process-gone", (_e, details) => log.info(`화면 프로세스 종료: ${details.reason}`));

  // 화면이 권한(알림·카메라·위치 등)을 달라고 해도 주지 않는다
  wc.session.setPermissionRequestHandler((_wc, _permission, callback) => callback(false));
  wc.session.setPermissionCheckHandler(() => false);

  win.once("ready-to-show", () => win.show());
  win.on("page-title-updated", (event) => event.preventDefault());
  // 창을 닫아도 앱은 메뉴바에서 계속 돈다(07:00 수집). Dock 아이콘만 숨긴다.
  win.on("closed", () => {
    if (mainWindow === win) mainWindow = null;
    if (!quitting) app.dock?.hide();
  });

  void win.loadURL(engine.launchUrl);
}

async function syncNow(): Promise<void> {
  if (!engine.running) return notify("엔진이 실행 중이 아니에요.");
  try {
    const { status: code, body } = await engine.request("POST", "/api/connections/toss/sync");
    log.info(`지금 수집: ${code}`);
    if (code === 202) return notify("수집을 시작했어요. 끝나면 화면에 반영돼요.");
    let message = `수집을 시작하지 못했어요(${code}).`;
    try {
      const parsed = JSON.parse(body) as { code?: string; message?: string };
      if (parsed.code === "ACCOUNT_NOT_CONNECTED") message = "토스 계좌를 먼저 연결해 주세요.";
      else if (parsed.message) message = parsed.message;
    } catch {
      // 본문이 JSON이 아니다
    }
    notify(message);
  } catch (e) {
    log.info(`지금 수집 실패: ${String(e)}`);
    notify("엔진에 연결하지 못했어요.");
  }
}

function notify(body: string): void {
  if (Notification.isSupported()) new Notification({ title: APP_NAME, body, silent: true }).show();
  else void dialog.showMessageBox({ type: "info", title: APP_NAME, message: body });
}

function setStatus(text: string): void {
  status = text;
  refreshTrayMenu();
}

function registerUrlScheme(): void {
  // 설치본은 Info.plist(CFBundleURLTypes, electron-builder protocols)에도 들어 있다.
  // 개발 실행은 등록하지 않는다(node_modules의 Electron이 uplog://를 가로채지 않게).
  if (!app.isPackaged) return;
  const ok = app.setAsDefaultProtocolClient(URL_SCHEME);
  log.info(`${URL_SCHEME}:// 등록: ${ok}`);
}

function createTray(): void {
  // 템플릿 이미지(파일 이름이 ...Template.png): macOS가 메뉴바 밝기·다크 모드에 맞춰 칠한다. @2x는 자동으로 고른다.
  // 설치본: Resources/branding, 개발 실행: 저장소의 branding/
  const brandingDir = app.isPackaged ? join(process.resourcesPath, "branding") : join(__dirname, "..", "..", "branding");
  const icon = nativeImage.createFromPath(join(brandingDir, "trayTemplate.png"));
  icon.setTemplateImage(true);
  tray = new Tray(icon);
  tray.setToolTip(APP_NAME);
  refreshTrayMenu();
}

function refreshTrayMenu(): void {
  if (!tray) return;
  const loginItem = app.getLoginItemSettings();
  const menu = Menu.buildFromTemplate([
    { label: `엔진: ${status}`, enabled: false },
    { type: "separator" },
    { label: `${APP_NAME} 열기`, click: () => openWindow(), enabled: engine.running },
    { label: "지금 수집", click: () => void syncNow(), enabled: engine.running },
    { type: "separator" },
    { label: "로그 폴더 열기", click: () => void shell.openPath(logsDir) },
    {
      label: "로그인 시 자동 실행",
      type: "checkbox",
      checked: loginItem.openAtLogin,
      click: (item) => {
        app.setLoginItemSettings({ openAtLogin: item.checked });
        log.info(`로그인 시 자동 실행: ${item.checked}`);
        refreshTrayMenu();
      },
    },
    { type: "separator" },
    { label: `${APP_NAME} 종료`, click: () => quit() },
  ]);
  tray.setContextMenu(menu);
}

function buildAppMenu(): Menu {
  const view: MenuItemConstructorOptions[] = [
    { label: "새로 고침", role: "reload" },
    { type: "separator" },
    { label: "실제 크기", role: "resetZoom" },
    { label: "확대", role: "zoomIn" },
    { label: "축소", role: "zoomOut" },
    { type: "separator" },
    { label: "전체 화면", role: "togglefullscreen" },
  ];
  if (!app.isPackaged) view.push({ type: "separator" }, { label: "개발자 도구", role: "toggleDevTools" });

  return Menu.buildFromTemplate([
    {
      label: APP_NAME,
      submenu: [
        { label: `${APP_NAME}에 관하여`, role: "about" },
        { type: "separator" },
        { label: `${APP_NAME} 가리기`, role: "hide" },
        { label: "기타 가리기", role: "hideOthers" },
        { label: "모두 보기", role: "unhide" },
        { type: "separator" },
        { label: `${APP_NAME} 종료`, accelerator: "Command+Q", click: () => quit() },
      ],
    },
    {
      label: "편집",
      submenu: [
        { label: "실행 취소", role: "undo" },
        { label: "실행 복귀", role: "redo" },
        { type: "separator" },
        { label: "오려두기", role: "cut" },
        { label: "복사하기", role: "copy" },
        { label: "붙여넣기", role: "paste" },
        { label: "모두 선택", role: "selectAll" },
      ],
    },
    { label: "보기", submenu: view },
    {
      label: "윈도우",
      submenu: [
        { label: "최소화", role: "minimize" },
        { label: "확대/축소", role: "zoom" },
        { label: "닫기", role: "close" },
      ],
    },
    {
      label: "도움말",
      role: "help",
      submenu: [
        { label: "지금 수집", click: () => void syncNow() },
        { label: "로그 폴더 열기", click: () => void shell.openPath(logsDir) },
        { label: "데이터 폴더 열기", click: () => void shell.openPath(config.dataDir) },
      ],
    },
  ]);
}

/** 앱을 끝낸다: 엔진을 먼저 멈추고(SIGTERM → 제한 시간 뒤 SIGKILL) 나간다. */
function quit(): void {
  if (quitting) return;
  quitting = true;
  setStatus("종료 중…");
  log.info("종료 요청");
  void engine.stop().finally(() => {
    log.info("종료");
    tray?.destroy();
    app.exit(0);
  });
}

// Dock 아이콘 클릭
app.on("activate", () => openWindow());
// 창을 모두 닫아도 끝내지 않는다(메뉴바에서 계속 돈다)
app.on("window-all-closed", () => {
  /* 메뉴바 앱 */
});
// Cmd+Q·Dock의 종료·osascript 'quit app' → 엔진을 멈춘 뒤 끝낸다
app.on("before-quit", (event) => {
  if (!quitting) {
    event.preventDefault();
    quit();
  }
});
for (const signal of ["SIGTERM", "SIGINT", "SIGHUP"] as const) {
  process.on(signal, () => quit());
}
