// 토스증권 키를 "한 번에 붙여 넣기"할 때 쓰는 순수 함수 모음. React·DOM·저장소에 닿지 않는다.
// 테스트 러너가 없어서, 지원하는 입력과 기대 결과를 아래 표로 남겨 둔다. 규칙을 바꾸면 표도 함께 고친다.
//
// | 붙여 넣은 내용                                              | clientId          | clientSecret | 규칙 |
// |------------------------------------------------------------|-------------------|--------------|------|
// | "tsck_live_abc\ntssk_live_xyz"                              | tsck_live_abc     | tssk_live_xyz| 2 (접두어) |
// | "tssk_live_xyz tsck_live_abc"                               | tsck_live_abc     | tssk_live_xyz| 2 (순서 무관) |
// | "tssk_live_xyz"                                             | null              | tssk_live_xyz| 2 (secret만) |
// | "tsck_live_abc\nsecret123456789012"                         | tsck_live_abc     | secret12…    | 2 (tsck_ 토큰 + 남은 긴 토큰) |
// | "client_id: tsck_live_abc / client_secret: s3cr3t"          | tsck_live_abc     | s3cr3t       | 1 (라벨) |
// | "client_id=abc\nclient_secret=xyz=="                        | abc               | xyz==        | 1 (값 끝의 = 는 base64라 남긴다) |
// | '{"clientId":"tsck_a","clientSecret":"b"}'                  | tsck_a            | b            | 1 (JSON처럼 따옴표·중괄호) |
// | "Client ID\ntsck_a\nClient Secret\nbbbb"                    | tsck_a            | bbbb         | 1 (라벨 다음 줄의 값) |
// | "클라이언트 ID：tsck_a  시크릿 키 : bbbb"                    | tsck_a            | bbbb         | 1 (한글 라벨, 전각 콜론) |
// | "tsck_live_abc\tqwertyuiopasdfgh"                           | tsck_live_abc     | qwerty…      | 2 (탭 구분) |
// | "qwertyuiopasdfgh tsck_live_abc"                            | tsck_live_abc     | qwerty…      | 2 (순서 무관) |
// | "client_secret: s3cr3t"                                     | null              | s3cr3t       | 1 (한쪽만 있으면 한쪽만) |
// | "tsck_live_abc"                                             | tsck_live_abc     | null         | 2 |
// | "abcdefabcdefabcdef"                                        | null              | null         | 하나뿐인 라벨 없는 토큰은 어느 값인지 몰라 고르지 않는다 |
// | "client_secret: tssk_live_********"                         | null              | null         | WTS가 가린 secret(*)은 값으로 치지 않는다 (maskedSecret: true) |
// | "Client Id\ntsck_live_a\n복사\nClient Secret\ntssk_live_****\n발급일 2026.10.18" | tsck_live_a | null | WTS 화면 통째 복사. 날짜는 secret이 아니다 (maskedSecret: true) |
// | "Client Id tsck_live_1 Client Secret tssk_live_2"           | tsck_live_1       | tssk_live_2  | 1 |
// | "ididididid secretsecretsecret" (tsck_ 없음, 라벨 없음)      | null              | null         | 둘 다 tsck_가 아니면 순서를 믿지 않는다 |
// | "​tsck_a " (보이지 않는 문자·줄바꿈 없는 공백)      | tsck_a            | null         | 0 (정리) |
// | ""                                                          | null              | null         | |
//
// 규칙 0. 정리: BOM·폭 없는 공백(U+200B–U+200D, U+2060, U+FEFF)을 지우고, 줄바꿈 없는 공백(U+00A0)과 CRLF를 일반 공백·줄바꿈으로 바꾼다.
// 규칙 1. 라벨: "client_id", "client id", "clientId", "클라이언트 ID", "아이디" 같은 라벨 뒤의 값(:, =, ：, 탭, 공백, 줄바꿈, 따옴표를 건너뛴다).
//         secret 라벨: "client_secret", "client secret", "clientSecret", "secret", "시크릿", "비밀 키" 등.
// 규칙 2. 라벨로 못 찾은 값만 토큰으로 고른다. 토큰은 공백·쉼표·세미콜론·따옴표·괄호·파이프로 나누고, 라벨 단어는 버린다.
//         - client_id: tsck_로 시작하는 첫 토큰(대소문자 무시)
//         - client_secret: tssk_로 시작하는 첫 토큰. 없으면 남은 토큰 중 8자 이상인 가장 긴 것.
//           단, 접두어 없는 토큰은 client_id를 찾았을 때만 고른다(그래야 "어느 쪽이 secret인지"를 추측하지 않는다).
//         접두어 없는 후보는 영문이 섞인 8자 이상의 키 문자여야 한다(날짜 "2026.10.18" 같은 값은 버린다).
// 규칙 3. WTS가 가려 보여 주는 secret(tssk_live_********처럼 *가 들어간 값)은 진짜 값이 아니라서 버린다.

export type ParsedCredentials = {
  clientId: string | null;
  clientSecret: string | null;
  /** 가려진 secret(tssk_live_********)만 있었다. "secret은 발급할 때만 보여요"를 안내할 때 쓴다. */
  maskedSecret: boolean;
};

/** 보이지 않는 문자를 지우고 공백·줄바꿈을 통일한다(규칙 0). */
function normalizeText(raw: string): string {
  return raw
    .replace(/[​-‍⁠﻿]/g, "")
    .replace(/ /g, " ")
    .replace(/\r\n?/g, "\n");
}

/**
 * 키 한 칸의 값을 보낼 수 있는 모양으로 다듬는다. 키에는 공백이 들어가지 않으므로
 * 복사하면서 끼어든 앞뒤·중간의 공백, 줄바꿈, 보이지 않는 문자를 모두 지운다.
 */
export function normalizeCredential(value: string): string {
  return normalizeText(value).replace(/\s+/g, "");
}

/** 토스증권 client_id는 tsck_로 시작한다(예: tsck_live_…). 다른 모양을 막지는 않고 안내만 한다. */
export function looksLikeClientId(value: string): boolean {
  return /^tsck_/i.test(value);
}

/** 토스증권 client_secret은 tssk_로 시작한다(예: tssk_live_…). 다른 모양을 막지는 않고 안내만 한다. */
export function looksLikeClientSecret(value: string): boolean {
  return /^tssk_/i.test(value);
}

/** WTS가 가려서 보여 주는 secret(예: tssk_live_********). 복사해도 쓸 수 없는 값이다. */
export function isMaskedSecret(value: string): boolean {
  return value.includes("*");
}

// 라벨과 값 사이: 닫는 따옴표, 공백(줄바꿈 포함), 구분자(: = ： => -), 여는 따옴표를 건너뛴다.
const GAP = String.raw`["'\x60]?\s*(?:[:=：]|=>|->|-)?\s*["'\x60]?`;
// 값: 공백·따옴표·쉼표·세미콜론·괄호가 나오기 전까지. =, /, +, -, _ 는 값에 들어갈 수 있어 남긴다.
const VALUE = String.raw`([^\s"'\x60,;{}\[\]()<>|]+)`;

// 라벨 앞이 글자·숫자·밑줄이면 다른 낱말의 일부라 보고 건너뛴다(예: "myclient_id").
const BEFORE = String.raw`(?:^|[^A-Za-z0-9_가-힣])`;

const ID_LABEL = String.raw`(?:client[\s_-]?id|클라이언트\s*(?:ID|아이디)|아이디)`;
const SECRET_LABEL = String.raw`(?:client[\s_-]?secret|클라이언트\s*(?:시크릿|secret)|secret(?:[\s_-]?key)?|시크릿(?:\s*키)?|비밀\s*키)`;

// 라벨 바로 뒤에 영문·숫자가 붙으면 라벨이 아니라 값의 일부다(예: "secret123…" 은 secret 라벨이 아니다).
const AFTER = String.raw`(?![A-Za-z0-9_])`;

const ID_RE = new RegExp(`${BEFORE}${ID_LABEL}${AFTER}${GAP}${VALUE}`, "i");
const SECRET_RE = new RegExp(`${BEFORE}${SECRET_LABEL}${AFTER}${GAP}${VALUE}`, "i");

/** 접두어 없는 secret 후보: 영문을 하나 이상 포함한 키 문자만(날짜·한글·숫자뿐인 토큰은 버린다). */
function secretLike(t: string): boolean {
  return t.length >= 8 && /^[A-Za-z0-9_\-+/=.]+$/.test(t) && /[A-Za-z]/.test(t);
}

/** 값 자리에 다른 라벨이 잡혔을 때(예: "client_id\nclient_secret: …") 버리려고 쓴다. */
const LABEL_WORD = /^(?:client[_-]?(?:id|secret)|clientid|clientsecret|client|id|secret|key|클라이언트|아이디|시크릿|키|비밀)[:=：]?$/i;

/** 끝에 붙은 구분자(예: "tsck_a/" 의 "/")는 키의 일부가 아니다. base64 꼬리 "="는 남긴다. */
function trimValue(value: string): string {
  return value.replace(/[/:：.]+$/, "");
}

function hasMaskedSecret(text: string): boolean {
  return /tssk_[A-Za-z0-9_]*\*/i.test(text);
}

function labelled(text: string, re: RegExp): string | null {
  const m = re.exec(text);
  if (!m) return null;
  const value = trimValue(m[1]);
  if (!value || LABEL_WORD.test(value) || isMaskedSecret(value)) return null;
  return value;
}

function tokens(text: string): string[] {
  return text
    .split(/[\s,;"'\x60{}\[\]()<>|]+/)
    .map(trimValue)
    .filter((t) => t.length > 0 && !LABEL_WORD.test(t) && !isMaskedSecret(t) && !/^[:=：/\-]+$/.test(t));
}

/**
 * 붙여 넣은 글에서 client_id와 client_secret을 찾는다. 찾지 못한 값은 null.
 * 받은 글은 어디에도 남기지 않는다. 결과의 secret은 부르는 쪽이 바로 입력 칸에 쓰고 버려야 한다.
 */
export function parseCredentials(raw: string): ParsedCredentials {
  const text = normalizeText(raw).trim();
  if (!text) return { clientId: null, clientSecret: null, maskedSecret: false };

  // 규칙 1: 라벨
  let clientId = labelled(text, ID_RE);
  let clientSecret = labelled(text, SECRET_RE);
  if (clientId && clientSecret && clientId === clientSecret) clientSecret = null;

  // 규칙 2: 라벨 없이 나열된 토큰
  if (!clientId || !clientSecret) {
    const rest = tokens(text).filter((t) => t !== clientId && t !== clientSecret);
    if (!clientId) {
      const tsck = rest.find(looksLikeClientId);
      if (tsck) clientId = tsck;
    }
    if (!clientSecret) {
      const tssk = rest.find((t) => t !== clientId && looksLikeClientSecret(t));
      if (tssk) clientSecret = tssk;
    }
    // 가려진 secret이 보였다면 진짜 secret은 없는 것이니 다른 토큰으로 추측하지 않는다.
    if (!clientSecret && clientId && !hasMaskedSecret(text)) {
      const candidates = rest.filter((t) => t !== clientId && secretLike(t) && !looksLikeClientId(t));
      if (candidates.length > 0) {
        clientSecret = candidates.reduce((longest, t) => (t.length > longest.length ? t : longest));
      }
    }
  }

  return { clientId, clientSecret, maskedSecret: !clientSecret && hasMaskedSecret(text) };
}
