// 앱(main 프로세스)과 엔진 stdout·stderr를 {data-dir}/logs/desktop.log에 쓴다. 5MB가 넘으면 .1 → .2 → .3으로 돌린다.
// 엔진 자체 로그는 엔진이 {data-dir}/logs/engine.log에 쓴다.
import { appendFileSync, existsSync, mkdirSync, renameSync, statSync } from "node:fs";
import { join } from "node:path";

const MAX_BYTES = 5 * 1024 * 1024;
const KEEP = 3;

export class RotatingLog {
  readonly file: string;
  private size = 0;

  constructor(readonly dir: string, name = "desktop.log") {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    this.file = join(dir, name);
    this.size = existsSync(this.file) ? statSync(this.file).size : 0;
  }

  info(message: string): void {
    this.write(`${new Date().toISOString()} [app] ${message}\n`);
  }

  /** 엔진 출력처럼 이미 줄 단위인 텍스트를 그대로 붙인다 */
  raw(prefix: string, chunk: Buffer | string): void {
    const text = chunk.toString();
    const lines = text.split("\n").filter((l) => l.length > 0);
    this.write(lines.map((l) => `${prefix} ${l}\n`).join(""));
  }

  private write(text: string): void {
    try {
      if (this.size + text.length > MAX_BYTES) this.rotate();
      appendFileSync(this.file, text, { mode: 0o600 });
      this.size += Buffer.byteLength(text);
    } catch {
      // 로그를 못 써도 앱은 계속 돈다
    }
  }

  private rotate(): void {
    for (let i = KEEP - 1; i >= 1; i--) {
      const from = `${this.file}.${i}`;
      if (existsSync(from)) renameSync(from, `${this.file}.${i + 1}`);
    }
    if (existsSync(this.file)) renameSync(this.file, `${this.file}.1`);
    this.size = 0;
  }
}
