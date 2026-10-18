#!/usr/bin/env bash
# 엔진(boot jar)이 쓰는 모듈만 담은 Java 런타임을 jlink로 만든다 → desktop/build/runtime
#   사용: desktop/scripts/build-runtime.sh [engine.jar]   (기본: app/build/libs/app-0.1.0-SNAPSHOT.jar)
# jdeps로 fat jar 안의 BOOT-INF/lib·classes를 훑어 모듈을 고르고, jdeps가 못 보는(리플렉션·서비스) 모듈을 더한다.
set -euo pipefail

DESKTOP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_DIR="$(cd "$DESKTOP_DIR/.." && pwd)"
JAR="${1:-$REPO_DIR/app/build/libs/app-0.1.0-SNAPSHOT.jar}"
OUT="$DESKTOP_DIR/build/runtime"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
JDEPS="$JAVA_HOME/bin/jdeps"
JLINK="$JAVA_HOME/bin/jlink"

[[ -f "$JAR" ]] || { echo "엔진 jar가 없습니다: $JAR (./gradlew :app:bootJar)" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
(cd "$WORK" && unzip -q "$JAR" 'BOOT-INF/*')

DETECTED="$("$JDEPS" --print-module-deps --ignore-missing-deps --multi-release 25 --recursive \
  --class-path "$WORK/BOOT-INF/lib/*" "$WORK"/BOOT-INF/lib/*.jar "$WORK/BOOT-INF/classes" 2>/dev/null | tail -1)"
[[ "$DETECTED" == java.base* ]] || { echo "jdeps 결과가 이상합니다: $DETECTED" >&2; exit 1; }

# jdeps가 정적으로 못 보는 것:
#   java.naming·jdk.naming.dns(JNDI/DNS 조회), jdk.crypto.ec(TLS ECDHE, 25에선 java.base로 옮겨졌지만 호환용),
#   jdk.charsets(EUC-KR 등), jdk.localedata(ko 로캘 형식), jdk.zipfs(zip 파일시스템), jdk.management(JMX 플랫폼 MXBean),
#   java.sql(H2·JDBC), java.xml(로그백 설정)
EXTRA="java.naming,jdk.naming.dns,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.management,java.sql,java.xml,java.logging"
MODULES="$DETECTED,$EXTRA"

rm -rf "$OUT"
mkdir -p "$(dirname "$OUT")"
"$JLINK" --add-modules "$MODULES" \
  --include-locales=en,ko \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
  --output "$OUT"

echo "$MODULES" | tr ',' '\n' | sort -u | tr '\n' ',' | sed 's/,$//' > "$OUT/MODULES.txt"
echo "런타임: $OUT ($(du -sh "$OUT" | cut -f1))"
echo "모듈: $(cat "$OUT/MODULES.txt")"
