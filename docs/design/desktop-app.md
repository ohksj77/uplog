# 서버 없는 맥 앱 설계

정책상 서비스가 사용자의 토스 Open API 키를 받아 서버에서 쓰면 안 된다. 그래서 **우리 서버 없이**, 사용자 맥에서만 키를 쓰고 계산하는 앱으로 바꾼다. 맥 앱을 먼저 만들고, 그다음 핵심을 TypeScript로 옮겨 아이폰으로 간다.

## 왜 웹 페이지가 아니라 설치형 앱인가

- 토스 Open API는 CORS 헤더를 주지 않는다(2026-10-18 확인: `OPTIONS /oauth2/token`에 `Access-Control-Allow-Origin` 없음). 브라우저의 웹 페이지는 토스를 직접 부를 수 없다.
- 토스는 허용 IP에서 온 요청만 받는다. 사용자 기기의 공인 IP를 WTS에 등록해야 한다. 집의 맥은 IP가 잘 바뀌지 않아 안정적이다(아이폰은 집 와이파이에서만 수집 가능).

## 구조

```
UpLog.app (Electron)
 ├─ main 프로세스: 빈 포트 고르기, 실행마다 새 비밀 토큰 만들기, 엔진 띄우기, 창·메뉴바 아이콘
 ├─ runtime/  jlink로 줄인 Java 런타임
 ├─ engine.jar (Spring Boot, desktop 프로필)
 │    ├─ 127.0.0.1:{port} 에만 바인딩
 │    ├─ 화면: web의 정적 export를 그대로 서빙(같은 출처라 CORS·쿠키 문제 없음)
 │    ├─ 계산·수집: 지금의 calc-engine, ingest, broker-toss 그대로
 │    └─ DB: ~/Library/Application Support/uplog/uplog.mv.db (H2 파일)
 └─ 키: macOS 키체인(마스터키) + 봉투 암호화로 DB에 보관
```

- 창을 닫아도 메뉴바 아이콘으로 계속 돌며, 매일 07:00 수집과 실행할 때 놓친 날 따라잡기를 한다. 로그인할 때 자동 실행은 설정에서 고른다.
- 우리 서버로 가는 통신은 없다. 밖으로 나가는 요청은 토스 Open API, 그리고 사용자가 누를 때만 "내 공인 IP 확인"(api.ipify.org) 둘뿐이다.

## 로컬 엔진 보안 (127.0.0.1이라도 브라우저에서 공격받을 수 있다)

- **실행 토큰**: Electron이 실행마다 256비트 토큰을 만들어 엔진에 넘기고, 창은 `http://127.0.0.1:{port}/__launch?token=…`으로 연다. 엔진은 HttpOnly·SameSite=Strict 쿠키로 바꿔 주고, 그 뒤 모든 요청은 이 쿠키가 있어야 한다. 다른 웹사이트가 이 포트로 요청해도 쿠키가 없어 거부된다.
- **Host 검사**: `Host`가 `127.0.0.1:{port}` 또는 `localhost:{port}`가 아니면 거부한다(DNS 리바인딩 차단).
- **Origin 검사**: 상태를 바꾸는 요청은 같은 출처만 받는다(지금의 OriginCheckFilter).
- **키**: 토스 키는 마스터키(키체인)로 봉투 암호화해 DB에 둔다. 응답·로그에 남기지 않는 규칙은 그대로. 토스 클라이언트의 조회 전용 허용 목록도 그대로.

## 사용자·로그인

- 로그인 없음. 맥 사용자 한 명 = 로컬 사용자 한 명(첫 실행 때 만든다). 카카오·개발용 로그인·이어 하기·세션 JWT는 서버 보관 브랜치(`server-archive`)로 옮기고 main에서 뺀다.
- 키를 넣기 전에는 샘플 계좌(데모)로 둘러볼 수 있다.

## 화면

- web은 `output: "export"`로 정적 파일을 만든다. 동적 경로는 쿼리로 바꾼다(`/days/2026-10-14` → `/days?date=2026-10-14`, `/holdings/005930` → `/holdings?code=005930`).
- 연결 화면: 허용 IP 단계는 "우리 서버 IP" 대신 **이 맥의 공인 IP**를 보여 준다(누르면 확인). 키 발급 안내, 한 번에 붙여 넣기, 틀린 값 안내, 키 다시 넣기는 그대로.
- 로그인·이어 하기 화면은 뺀다.

## 빌드

한 번에: `scripts/build-mac-app.sh` (단계별로 `SKIP_WEB=1`·`SKIP_ENGINE=1`·`SKIP_RUNTIME=1`).

1. `cd web && npm run build` → `web/out/`(정적 export, 약 2.6MB). jar에 넣지 않고 앱의 `Resources/web`에 두고 `--uplog.desktop.web-dir`로 넘긴다.
2. `./gradlew :app:bootJar` → `app/build/libs/app-0.1.0-SNAPSHOT.jar`(약 32MB) → `Resources/engine.jar`.
3. `desktop/scripts/build-runtime.sh`: fat jar의 `BOOT-INF/lib`·`classes`를 풀어 `jdeps --print-module-deps --ignore-missing-deps --multi-release 25 --recursive`로
   모듈을 고르고, jdeps가 못 보는 모듈(JNDI·DNS, 문자셋, ko 로캘, zipfs, JMX, JDBC 등)을 더해
   `jlink --strip-debug --no-header-files --no-man-pages --compress=zip-6 --include-locales=en,ko` → `desktop/build/runtime`(약 54MB) → `Resources/runtime`.
   모듈(23개): java.base, java.compiler, java.desktop, java.instrument, java.logging, java.management, java.naming, java.net.http, java.prefs,
   java.scripting, java.security.jgss, java.sql, java.sql.rowset, java.xml, jdk.charsets, jdk.crypto.ec, jdk.jfr, jdk.localedata, jdk.management,
   jdk.naming.dns, jdk.net, jdk.unsupported, jdk.zipfs. 스크립트가 이 런타임으로 엔진을 띄워 `/__health` 200을 확인한 뒤에 패키징한다.
4. `desktop/`에서 electron-builder(`npm run dist`) → `desktop/dist-app/mac-arm64/UpLog.app`(약 330MB, 그중 Electron 240MB)과
   `desktop/dist-app/UpLog-0.1.0-arm64.dmg`(약 175MB). 번들 id `dev.uplog.app`, 아이콘 `branding/UpLog.icns`, `uplog://` 스킴(`protocols`).
   afterSign 훅(`desktop/scripts/after-sign.cjs`)이 `widget/build/UplogWidget.appex`를 `Contents/PlugIns/`에 넣고 앱만 `--deep` 없이
   (`--preserve-metadata=entitlements,requirements,flags,runtime`) 다시 서명한다. DMG는 그 뒤에 만든다. 스크립트가 끝에 `codesign --verify --strict`와
   위젯의 `app-sandbox` entitlements를 확인한다. arm64만, **ad-hoc 서명**(`identity: "-"`), hardened runtime 끔, 공증 없음.
   배포하려면 Apple Developer ID 서명·hardened runtime(+ JVM용 `allow-jit`·`allow-unsigned-executable-memory` 엔타이틀먼트)·공증이 필요하다.

**Electron 껍데기** (`desktop/src`, TypeScript → `tsc`, 런타임 의존성 없음. Electron 44.5.1, electron-builder 26.15.3):

- 단일 인스턴스, 127.0.0.1 빈 포트, `crypto.randomBytes(32)` base64url 토큰, `runtime/bin/java -Djava.awt.headless=true -jar engine.jar …`(계약 그대로).
  엔진 stdout·stderr는 `{data-dir}/logs/desktop.log`(5MB × 4개 돌림). 지난 실행에서 남은 엔진은 `{data-dir}/engine.pid`로 찾아 끝낸다(H2 파일 잠금).
- `/__health`를 90초까지 기다리고, 실패하면 "다시 시도 · 종료 · 로그 열기" 창. 준비된 뒤 엔진이 죽으면 한 번 다시 띄우고(새 포트·토큰, 창을 새 실행 주소로),
  또 죽으면 같은 창.
- 창: `contextIsolation`·`sandbox`·`webSecurity` 켬, `nodeIntegration` 끔, preload 없음, 설치본은 개발자 도구 끔, 권한 요청 모두 거부.
  엔진 출처 밖으로의 이동·새 창은 막고 http(s)만 기본 브라우저로 연다(tossinvest.com, 토스 문서).
- 메뉴바 아이콘(템플릿 이미지): 엔진 상태, 업로그 열기, 지금 수집(main이 토큰을 쿠키로, 같은 출처 Origin으로 `POST /api/connections/toss/sync` → 알림),
  로그 폴더 열기, 로그인 시 자동 실행(`app.setLoginItemSettings`), 업로그 종료. 창을 닫으면 Dock 아이콘만 숨기고 계속 돈다.
  종료(⌘Q·메뉴·`osascript -e 'quit app "UpLog"'`)는 엔진에 SIGTERM, 15초 안에 안 끝나면 SIGKILL.
- 앱 이름 `UpLog`(productName·`app.setName`·창 제목·메뉴·메뉴바 툴팁). 이름은 ASCII로 둔다: Electron 44는 `CFBundleName`으로 Helper 앱("<이름> Helper")을
  찾는데, 한글 이름(예: "업로그")이면 시작하자마자 SIGTRAP으로 죽는다.
- `uplog://…` 링크(위젯 누르기 등)는 `open-url`로 받아 창을 열거나 앞으로 가져온다. 설치본만 `app.setAsDefaultProtocolClient("uplog")`를 부른다.

## 실행 계약 (엔진 · 화면 · Electron)

**엔진 실행** (Electron main이 띄운다):

```
runtime/bin/java -jar engine.jar \
  --spring.profiles.active=desktop \
  --server.address=127.0.0.1 --server.port={port} \
  --uplog.desktop.launch-token={token}   # 실행마다 새 256비트(base64url)
  --uplog.desktop.data-dir={dir}         # ~/Library/Application Support/uplog
  --uplog.desktop.web-dir={dir}          # 앱 안의 web 정적 export 폴더
```

| 경로 | 인증 | 동작 |
|---|---|---|
| `GET /__health` | 없음 | `200 {"status":"UP"}`. Electron이 준비됐는지 확인 |
| `GET /__launch?token=…` | 토큰 일치 | `uplog_desktop` 쿠키(HttpOnly, SameSite=Strict, Path=/)를 주고 `/`로 302. 틀리면 403 |
| `/api/**` | 쿠키 = 토큰 | 없거나 틀리면 403 `{code:"DESKTOP_LOCKED"}` |
| 그 밖(정적 화면) | 없음 | `web-dir`의 파일. 경로가 없으면 `{path}.html` → `404.html` 순으로 찾는다 |

- 모든 요청: `Host`가 `127.0.0.1:{port}`·`localhost:{port}`가 아니면 403. 상태를 바꾸는 요청은 Origin이 같은 출처여야 한다.
- 로컬 사용자: 첫 실행 때 하나 만들고, 쿠키가 맞는 요청은 모두 그 사용자다(`CurrentUser`).
- 키체인: 마스터키를 `security find-generic-password -s uplog -a master-key -w`로 읽고, 없으면 만들어 `add-generic-password`로 넣는다.
- `GET /api/desktop/public-ip` → `{"ip":"1.2.3.4"}`: 사용자가 누를 때만 api.ipify.org를 부른다(최대 1분 기억, 실패는 502 `PUBLIC_IP_UNAVAILABLE`). 연결 화면 IP 단계가 쓴다. `GET /api/connections/toss/setup`과 `TOSS_IP_NOT_ALLOWED` 오류 본문은 `egressIps` 대신 `publicIpCheck: "/api/desktop/public-ip"`를 준다.
- 그 밖의 요청 규칙: Host가 틀리면 403 `HOST_NOT_ALLOWED`, 다른 출처의 상태 변경은 403 `ORIGIN_NOT_ALLOWED`. 실행 토큰은 32자 이상이어야 하고(짧으면 시작하지 않음), `server.address`가 127.0.0.1이 아니면 시작하지 않는다. 정적 화면의 `_next/static/**`은 1년 캐시(immutable), 나머지는 `no-cache`, `.txt`(RSC 페이로드)는 `text/plain; charset=utf-8`.
- 수집: 매일 07:00(KST) 직전 거래일, 그리고 엔진이 뜰 때·30분마다 놓친 날 따라잡기(가장 최근 거래일 하나로, [account-connection.md](account-connection.md) "수집 규칙"). 큐는 한가하면 30초마다 본다.
- 개발: `--uplog.desktop.dev-open=true`면 토큰·Host 검사를 끄고(127.0.0.1 바인딩은 유지) `next dev`가 `/api`를 엔진으로 넘긴다.
- 테스트용 가짜 토스: `--spring.profiles.active=desktop,fake-toss`.

**화면(web)**: `next build`가 `out/`에 정적 export를 만든다(`output: "export"`, 개발 중 rewrites는 `next dev`에서만). 동적 경로는 쿼리로(`/days?date=`, `/holdings?code=`). 로그인·이어 하기(`/login`, `/continue`)와 사용자 메뉴는 없앤다.

## 위젯 ("UpLog 로그")

맥 바탕화면·알림 센터 위젯(WidgetKit). 소스는 `widget/`(SwiftUI), Xcode 없이 `swiftc`로 빌드한다. 서버·네트워크 없이 **엔진이 쓴 파일 하나**만 읽는다.

**데이터 경로**: Team ID가 없어 App Group을 못 쓴다. 엔진이 `{data-dir}/widget/widget.json`에 쓰고(기본 `~/Library/Application Support/uplog/widget/widget.json`),
샌드박스 위젯은 entitlement `com.apple.security.temporary-exception.files.home-relative-path.read-only = ["/Library/Application Support/uplog/widget/"]`로 그 폴더만 읽는다.
샌드박스 안에서는 `NSHomeDirectory()`가 컨테이너를 가리키므로 실제 홈은 `getpwuid(getuid())`로 찾는다. 파일이 없거나 못 읽으면 "UpLog 앱을 열어 주세요".
그래서 위젯은 **기본 data-dir**에서만 데이터를 본다(개발용 임시 data-dir로 띄운 엔진은 위젯에 보이지 않는다).

**언제 쓰나** (`WidgetSnapshotWriter`, portfolio 모듈 `com.uplog.portfolio.widget`): 엔진이 뜬 직후, 계좌·시세 데이터 번호(`PortfolioDataVersion`·`PriceDataVersion`)나
날짜(KST)가 바뀐 뒤(5초마다 번호만 비교 — 수집·역산 뒤 `PortfolioService.refresh`, 연결·해제, 시세 수집이 모두 번호를 올린다), 그리고 30분마다.
임시 파일에 쓰고 rename해서 위젯이 반쯤 쓴 파일을 보지 않는다. 계좌가 아직 만드는 중(`ACCOUNT_NOT_READY`)이거나 없으면 이전 파일을 그대로 둔다.
`uplog.widget.enabled=false`면 쓰지 않는다. 키·토큰·계좌번호는 넣지 않는다.

**계좌**: 로컬 사용자의 ACTIVE 계좌, 없으면 데모 계좌. `sample`은 계좌의 sample 값(데모 계좌·가짜 토스 계좌는 true).

**형식** (`version: 1`):

```jsonc
{
  "version": 1,
  "generatedAt": "2026-10-18T10:21:07.418Z",   // 쓴 시각(UTC)
  "asOf": "2026-10-16",                        // 마지막으로 기록한 거래일(MISSING 제외). 기록이 없으면 null
  "accountName": "데모 계좌",
  "sample": true,
  "today": { "pnl": 200491, "return": 0.005811 },   // asOf 하루의 손익(원)·수익률(소수). 기록이 없으면 null
  "month": { "pnl": -557040, "gainDays": 3, "lossDays": 7 },     // asOf가 든 달
  "year":  { "pnl": 3058778, "gainDays": 107, "lossDays": 98 },  // asOf가 든 해(스크린리더 "올해 n일 벌고 m일 잃었어요")
  "streak": { "kind": "GAIN", "length": 1 },   // 마지막 기록일까지 이어진 연속. "GAIN" | "LOSS" | null(마지막 날 손익 0·기록 없음, length 0)
  "weeks": [                                   // 26주, 오래된 순. 마지막 주 = 오늘(KST)이 든 주
    { "start": "2026-10-12",                   // 월요일
      "days": [                                // 월–금 5칸
        { "date": "2026-10-12", "level": null, "pnl": null },     // 기록 없음(MISSING)·휴장·미래
        { "date": "2026-10-13", "level": -4, "pnl": -225166 },
        { "date": "2026-10-14", "level": 3, "pnl": 170685 }, … ] } ],
  "thresholds": [50000, 120000, 210000]        // 진하기 경계(원)
}
```

- **진하기(level)**: 부호 = 손익 부호, 크기 = `|pnl|`이 경계 `[a, b, c]`의 어디에 드는지(`< a` → 1, `< b` → 2, `< c` → 3, 나머지 4). 손익 0은 0. 경계는 asOf부터 1년 안에
  기록한 날 중 손익이 0이 아닌 날의 `|pnl|` 사분위수(25·50·75%, 선형 보간)를 1만 원 이상은 만 원, 1천 원 이상은 천 원 단위로 반올림한 값 — web의
  `quartileThresholds`·`niceWon`(log-grid/logic.ts, portfolio-home/log-cells.ts)과 같은 규칙이다(web은 그해, 위젯은 지난 1년).
- 연속은 기록한 날만 이어서 센다(MISSING은 건너뛰고, 손익 0인 날에서 끊긴다).

**화면**: 작게(최근 8주 + 오늘 손익), 중간(최근 18주 + 오늘 손익 + 이번 달 며칠 벌고/잃고), 크게(26주를 13주씩 두 줄, 월 라벨, 오늘·이번 달·연속, 범례, 기준일).
GitHub 잔디처럼 행 = 월–금, 둥근 네모 칸. 색은 web의 토큰(`--gain-1..4` 빨강, `--loss-1..4` 파랑, `--log-zero`, 기록 없음은 `--log-empty` 테두리)을
밝은·어두운 모드로 나눠 쓴다. 샘플 계좌면 "샘플 계좌" 배지. 타임라인은 지금 한 칸, 30분 뒤 다시 읽기. 누르면 `uplog://open`(Electron이 스킴을 등록한다).

**빌드·시험**:

```bash
widget/build.sh          # → widget/build/UplogWidget.appex (dev.uplog.app.widget, arm64, macOS 14+, 샌드박스 entitlements로 ad-hoc 서명)
widget/test-host.sh      # 작은 호스트 ~/Applications/UplogWidgetTest.app(dev.uplog.app.test, 위젯 dev.uplog.app.test.widget)에 넣어 등록·실행
widget/test-host.sh --remove
widget/tools/render.sh   # 위젯 화면을 PNG로(ImageRenderer, 밝은·어두운). 기본 입력 widget/tools/sample-widget.json(엔진이 데모 계좌로 쓴 스냅샷)
pluginkit -m -p com.apple.widgetkit-extension -v | grep dev.uplog   # 등록 확인
```

**Electron 앱에 넣기** (`scripts/build-mac-app.sh`가 한다): `widget/build.sh`의 결과를 `UpLog.app/Contents/PlugIns/UplogWidget.appex`로 복사한다(호스트 번들 id `dev.uplog.app`, 위젯 id는 그 접두어
`dev.uplog.app.widget`이어야 한다). 서명은 안쪽부터: appex를 자기 entitlements로 먼저(build.sh가 이미 함), 그다음 앱. 앱을 `--deep`으로 서명하면 appex의
entitlements가 지워지므로 쓰지 않는다(electron-builder가 서명을 마친 뒤 afterSign 훅에서 appex를 넣고 앱만 다시 서명한다). 앱은 `uplog://` 스킴을 등록하고
(`CFBundleURLTypes`, `app.setAsDefaultProtocolClient("uplog")`), 위젯이 갤러리에 안 보이면 첫 실행 때 `lsregister -f {앱 경로}`를 한 번 부른다.
앱은 `/Applications` 또는 `~/Applications`에 있어야 pluginkit이 등록한다.

## 다음 단계

- 아이폰: 계산·토스 클라이언트를 TypeScript 패키지로 옮겨 Expo 앱과 맥 앱이 함께 쓴다. 아이폰은 집 와이파이에 있을 때 수집하고, 위젯은 마지막 기록을 보여 준다.
- 배포: Apple Developer ID 서명 + hardened runtime(JVM 엔타이틀먼트) + 공증(`notarytool`). 그전까지는 Gatekeeper "그래도 열기"가 필요하다.
- 자동 업데이트 없음(새 dmg로 덮어 설치). 서명 뒤 electron-updater 등을 검토한다.
- Intel 맥: x64 JDK로 x64 런타임을 jlink해야 한다(jlink는 실행 중인 JDK와 같은 아키텍처의 런타임만 만든다). 지금은 arm64만.
- 앱(main 프로세스)이 강제로 죽으면 엔진만 남는다: 다음 실행이 `engine.pid`로 정리한다. 엔진이 부모 프로세스를 지켜보다 스스로 끝나게 하는 것도 검토.
