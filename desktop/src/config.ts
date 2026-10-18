// 엔진을 띄우는 데 필요한 경로. 설치된 앱은 .app 안의 Resources를 쓰고, 개발 실행은 저장소의 빌드 결과를 쓴다.
// 실행 계약: docs/design/desktop-app.md "실행 계약"
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";

export interface EngineConfig {
  /** java 실행 파일 (설치본: Resources/runtime/bin/java) */
  java: string;
  /** Spring Boot 엔진 jar */
  jar: string;
  /** web 정적 export 폴더 */
  webDir: string;
  /** H2 DB·로그·키 파일 폴더 (~/Library/Application Support/uplog) */
  dataDir: string;
  /** 엔진에 덧붙일 인자(검증·개발용, UPLOG_ENGINE_ARGS) */
  extraArgs: string[];
}

export const DEFAULT_DATA_DIR = join(homedir(), "Library", "Application Support", "uplog");

export function resolveConfig(isPackaged: boolean, resourcesPath: string, env: NodeJS.ProcessEnv): EngineConfig {
  const dataDir = env.UPLOG_DATA_DIR ? resolve(env.UPLOG_DATA_DIR) : DEFAULT_DATA_DIR;
  const extraArgs = (env.UPLOG_ENGINE_ARGS ?? "").split(/\s+/).filter((a) => a.length > 0);

  if (isPackaged) {
    return {
      java: join(resourcesPath, "runtime", "bin", "java"),
      jar: join(resourcesPath, "engine.jar"),
      webDir: join(resourcesPath, "web"),
      dataDir,
      extraArgs,
    };
  }

  // 개발 실행(npm start): desktop/dist/main.js → 저장소 루트
  const repo = resolve(__dirname, "..", "..");
  const bundledRuntime = join(repo, "desktop", "build", "runtime", "bin", "java");
  return {
    java: env.UPLOG_JAVA ?? (existsSync(bundledRuntime) ? bundledRuntime : "java"),
    jar: env.UPLOG_ENGINE_JAR ?? join(repo, "app", "build", "libs", "app-0.1.0-SNAPSHOT.jar"),
    webDir: env.UPLOG_WEB_DIR ?? join(repo, "web", "out"),
    dataDir,
    extraArgs,
  };
}
