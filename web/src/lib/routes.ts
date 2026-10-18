// 정적 export(맥 앱)에서는 동적 경로를 쓸 수 없어서 날짜·종목은 쿼리로 넘긴다.

/** 하루 기록: /days?date=YYYY-MM-DD */
export function dayHref(date: string): string {
  return `/days?date=${encodeURIComponent(date)}`;
}

/** 종목 상세: /holdings?code=XXX */
export function holdingHref(code: string): string {
  return `/holdings?code=${encodeURIComponent(code)}`;
}
