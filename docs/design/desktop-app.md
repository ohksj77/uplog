# 서버 없는 맥 앱 설계

정책상 서비스가 사용자의 토스 Open API 키를 받아 서버에서 쓰면 안 된다. 그래서 **우리 서버 없이**, 사용자 맥에서만 키를 쓰고 계산하는 앱으로 바꾼다. 맥 앱을 먼저 만들고, 그다음 핵심을 TypeScript로 옮겨 아이폰으로 간다.

## 왜 웹 페이지가 아니라 설치형 앱인가

- 토스 Open API는 CORS 헤더를 주지 않는다(2026-10-18 확인: `OPTIONS /oauth2/token`에 `Access-Control-Allow-Origin` 없음). 브라우저의 웹 페이지는 토스를 직접 부를 수 없다.
- 토스는 허용 IP에서 온 요청만 받는다. 사용자 기기의 공인 IP를 WTS에 등록해야 한다. 집의 맥은 IP가 잘 바뀌지 않아 안정적이다(아이폰은 집 와이파이에서만 수집 가능).

## 구조

```
uplog.app (Electron)
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

1. `./gradlew :app:bootJar` → engine.jar
2. `jdeps`로 필요한 모듈을 골라 `jlink`로 런타임을 만든다(약 50MB).
3. `web`에서 `next build`(export) → engine.jar의 `static/`에 넣는다.
4. `desktop/`에서 electron-builder로 `.app`/`.dmg`(개발용은 서명 없음. 배포는 Apple Developer ID 서명·공증 필요).

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
- `GET /api/desktop/public-ip` → `{"ip":"1.2.3.4"}`: 사용자가 누를 때만 api.ipify.org를 부른다. 연결 화면 IP 단계가 쓴다.
- 개발: `--uplog.desktop.dev-open=true`면 토큰·Host 검사를 끄고(127.0.0.1 바인딩은 유지) `next dev`가 `/api`를 엔진으로 넘긴다.
- 테스트용 가짜 토스: `--spring.profiles.active=desktop,fake-toss`.

**화면(web)**: `next build`가 `out/`에 정적 export를 만든다(`output: "export"`, 개발 중 rewrites는 `next dev`에서만). 동적 경로는 쿼리로(`/days?date=`, `/holdings?code=`). 로그인·이어 하기(`/login`, `/continue`)와 사용자 메뉴는 없앤다.

## 다음 단계

- macOS 위젯(WidgetKit, Swift): 엔진이 앱 그룹 폴더에 `widget.json`(오늘 손익, 이번 달 로그 칸, 연속 기록)을 쓰고 위젯이 읽는다. Xcode가 필요하다.
- 아이폰: 계산·토스 클라이언트를 TypeScript 패키지로 옮겨 Expo 앱과 맥 앱이 함께 쓴다. 아이폰은 집 와이파이에 있을 때 수집하고, 위젯은 마지막 기록을 보여 준다.
