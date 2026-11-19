/** 백엔드 오류 응답 형식 {code, message}을 그대로 담는다. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const NETWORK_ERROR = "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";

/**
 * 같은 출처의 /api 로 요청한다(쿠키 자동 포함). 실패하면 ApiError를 던진다.
 * 본문이 없는 응답(204)은 undefined를 돌려준다.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(path, { ...init, headers, credentials: "same-origin", cache: "no-store" });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", NETWORK_ERROR);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      body?.code ?? "UNKNOWN",
      body?.message ?? (response.status >= 500 ? NETWORK_ERROR : "요청을 처리하지 못했습니다."),
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const json = (body: unknown) => JSON.stringify(body);
