// 로컬 엔진(Spring Boot jar)을 띄우고 멈춘다. 실행 계약: docs/design/desktop-app.md "실행 계약"
//  - 127.0.0.1의 빈 포트, 실행마다 새 256비트 토큰(base64url)
//  - /__health가 200이 될 때까지 기다린다
//  - 멈출 때 SIGTERM(스프링이 DB를 닫고 끝낸다), 제한 시간이 지나면 SIGKILL
import { ChildProcess, execFileSync, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { EventEmitter } from "node:events";
import { existsSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import http from "node:http";
import net from "node:net";
import { join } from "node:path";
import type { EngineConfig } from "./config";
import type { RotatingLog } from "./log";

const HOST = "127.0.0.1";
const HEALTH_TIMEOUT_MS = 90_000;
const STOP_TIMEOUT_MS = 15_000;

export class EngineStartError extends Error {}

export interface EngineEvents {
  /** 준비된 뒤 예기치 않게 끝났다 */
  exited: [code: number | null, signal: NodeJS.Signals | null];
}

export class Engine extends EventEmitter<EngineEvents> {
  private child: ChildProcess | null = null;
  private stopping = false;
  private _port = 0;
  private _token = "";

  constructor(private readonly config: EngineConfig, private readonly log: RotatingLog) {
    super();
  }

  get port(): number {
    return this._port;
  }

  get token(): string {
    return this._token;
  }

  get origin(): string {
    return `http://${HOST}:${this._port}`;
  }

  get launchUrl(): string {
    return `${this.origin}/__launch?token=${encodeURIComponent(this._token)}`;
  }

  get running(): boolean {
    return this.child !== null && this.child.exitCode === null && this.child.signalCode === null;
  }

  private get pidFile(): string {
    return join(this.config.dataDir, "engine.pid");
  }

  async start(): Promise<void> {
    if (this.running) return;
    for (const p of [this.config.java, this.config.jar]) {
      if (p.includes("/") && !existsSync(p)) throw new EngineStartError(`필요한 파일이 없습니다: ${p}`);
    }
    if (!existsSync(this.config.webDir)) this.log.info(`화면 폴더가 없습니다(API만 뜹니다): ${this.config.webDir}`);
    this.killLeftover();

    this.stopping = false;
    this._port = await freePort();
    this._token = randomBytes(32).toString("base64url"); // 43자

    const args = [
      // 엔진은 화면 없는 서버다. AWT가 뜨더라도 Dock에 자바 아이콘이 생기지 않게 한다.
      "-Djava.awt.headless=true",
      "-Dapple.awt.UIElement=true",
      "-XX:+UseSerialGC",
      "-XX:MaxRAMPercentage=25",
      "-jar", this.config.jar,
      "--spring.profiles.active=desktop",
      `--server.address=${HOST}`,
      `--server.port=${this._port}`,
      `--uplog.desktop.launch-token=${this._token}`,
      `--uplog.desktop.data-dir=${this.config.dataDir}`,
      `--uplog.desktop.web-dir=${this.config.webDir}`,
      // 엔진 로그는 engine.log에 있으니 콘솔 출력은 줄인다(시작 실패 같은 JVM 출력은 그대로 desktop.log로 온다)
      "--logging.threshold.console=WARN",
      "--spring.main.banner-mode=off",
      ...this.config.extraArgs,
    ];
    this.log.info(`엔진 시작: ${this.config.java} (포트 ${this._port}, data-dir ${this.config.dataDir})`);
    if (this.config.extraArgs.length > 0) this.log.info(`덧붙인 엔진 인자: ${this.config.extraArgs.join(" ")}`);

    const child = spawn(this.config.java, args, {
      cwd: this.config.dataDir,
      stdio: ["ignore", "pipe", "pipe"],
      env: { ...process.env, JAVA_TOOL_OPTIONS: undefined, JDK_JAVA_OPTIONS: undefined },
    });
    this.child = child;
    child.stdout?.on("data", (d: Buffer) => this.log.raw("[engine]", d));
    child.stderr?.on("data", (d: Buffer) => this.log.raw("[engine:err]", d));
    if (child.pid) writeFileSync(this.pidFile, String(child.pid), { mode: 0o600 });

    let ready = false;
    let exitedEarly: string | null = null;
    child.once("error", (e) => {
      exitedEarly = `엔진을 실행할 수 없습니다: ${e.message}`;
      this.log.info(exitedEarly);
    });
    child.once("exit", (code, signal) => {
      this.log.info(`엔진 종료: code=${code} signal=${signal}`);
      if (this.child === child) this.child = null;
      rmSync(this.pidFile, { force: true });
      if (exitedEarly === null) exitedEarly = `엔진이 시작 중에 끝났습니다(code=${code}, signal=${signal}).`;
      if (ready && !this.stopping) this.emit("exited", code, signal);
    });

    const deadline = Date.now() + HEALTH_TIMEOUT_MS;
    while (Date.now() < deadline) {
      if (exitedEarly !== null) throw new EngineStartError(exitedEarly);
      if (await health(this._port)) {
        ready = true;
        this.log.info(`엔진 준비됨: ${this.origin}`);
        return;
      }
      await sleep(250);
    }
    await this.stop();
    throw new EngineStartError(`엔진이 ${HEALTH_TIMEOUT_MS / 1000}초 안에 준비되지 않았습니다.`);
  }

  /** SIGTERM으로 끝내고, 제한 시간 안에 안 끝나면 SIGKILL */
  async stop(): Promise<void> {
    const child = this.child;
    this.stopping = true;
    if (!child || child.exitCode !== null || child.signalCode !== null) return;
    this.log.info("엔진 멈추는 중(SIGTERM)");
    await new Promise<void>((resolveStop) => {
      const timer = setTimeout(() => {
        this.log.info(`엔진이 ${STOP_TIMEOUT_MS / 1000}초 안에 끝나지 않아 SIGKILL`);
        child.kill("SIGKILL");
      }, STOP_TIMEOUT_MS);
      child.once("exit", () => {
        clearTimeout(timer);
        resolveStop();
      });
      child.kill("SIGTERM");
    });
  }

  /** 엔진 API 호출(메뉴바 "지금 수집"). 쿠키 값 = 실행 토큰, 상태 변경이라 같은 출처 Origin을 붙인다. */
  request(method: string, path: string): Promise<{ status: number; body: string }> {
    return new Promise((resolveReq, reject) => {
      const req = http.request(
        {
          host: HOST,
          port: this._port,
          method,
          path,
          headers: {
            Cookie: `uplog_desktop=${this._token}`,
            Origin: this.origin,
            Accept: "application/json",
          },
          timeout: 10_000,
        },
        (res) => {
          const chunks: Buffer[] = [];
          res.on("data", (c: Buffer) => chunks.push(c));
          res.on("end", () => resolveReq({ status: res.statusCode ?? 0, body: Buffer.concat(chunks).toString("utf8") }));
        },
      );
      req.on("timeout", () => req.destroy(new Error("시간 초과")));
      req.on("error", reject);
      req.end();
    });
  }

  /** 지난 실행에서 앱이 갑자기 끝나 엔진만 남았으면(H2 파일 잠금을 쥐고 있다) 끝낸다. 우리 엔진일 때만. */
  private killLeftover(): void {
    if (!existsSync(this.pidFile)) return;
    const pid = Number(readFileSync(this.pidFile, "utf8").trim());
    rmSync(this.pidFile, { force: true });
    if (!Number.isInteger(pid) || pid <= 1) return;
    try {
      const command = execFileSync("/bin/ps", ["-p", String(pid), "-o", "command="], { encoding: "utf8" });
      if (command.includes("--uplog.desktop.launch-token=") && command.includes(this.config.dataDir)) {
        this.log.info(`지난 실행의 엔진(pid ${pid})이 남아 있어 끝냅니다`);
        process.kill(pid, "SIGTERM");
        for (let i = 0; i < 50; i++) {
          try {
            process.kill(pid, 0);
          } catch {
            return;
          }
          execFileSync("/bin/sleep", ["0.2"]);
        }
        process.kill(pid, "SIGKILL");
      }
    } catch {
      // 프로세스가 이미 없다
    }
  }
}

function freePort(): Promise<number> {
  return new Promise((resolvePort, reject) => {
    const server = net.createServer();
    server.unref();
    server.on("error", reject);
    server.listen(0, HOST, () => {
      const address = server.address();
      const port = typeof address === "object" && address ? address.port : 0;
      server.close(() => resolvePort(port));
    });
  });
}

function health(port: number): Promise<boolean> {
  return new Promise((resolveHealth) => {
    const req = http.get({ host: HOST, port, path: "/__health", timeout: 1000 }, (res) => {
      res.resume();
      resolveHealth(res.statusCode === 200);
    });
    req.on("timeout", () => req.destroy());
    req.on("error", () => resolveHealth(false));
  });
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
