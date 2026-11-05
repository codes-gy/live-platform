/**
 * 로그인 뒤 돌아갈 주소. 우리 사이트 안의 경로만 허용한다(//evil.com 같은 외부 이동 방지).
 */
export function safeNext(next: string | string[] | undefined, fallback = "/"): string {
  const value = Array.isArray(next) ? next[0] : next;
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return fallback;
  }
  return value;
}

export function loginPath(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}
