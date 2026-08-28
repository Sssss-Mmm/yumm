const TOKEN_KEY = "accessToken";
const REFRESH_KEY = "refreshToken";

export const getToken = () => localStorage.getItem(TOKEN_KEY);

export function setTokens(access: string | null, refresh: string | null = null) {
  for (const [key, value] of [[TOKEN_KEY, access], [REFRESH_KEY, refresh]] as const) {
    if (value) localStorage.setItem(key, value);
    else localStorage.removeItem(key);
  }
}

/** 서버의 리프레시 토큰까지 무효화한다. 실패해도 로컬 토큰은 지운다. */
export async function logout() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  try {
    if (refreshToken) await api("/auth/logout", "POST", { refreshToken });
  } finally {
    setTokens(null);
  }
}

/** 서버 공통 응답은 { message, data }, 에러는 { error, message } */
export async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const token = getToken();
  const res = await fetch(`/api${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  // 토큰 만료/미인증은 어디서 터지든 로그인으로 보낸다.
  if (res.status === 401) {
    setTokens(null);
    location.href = "/login";
    throw new Error("로그인이 필요합니다.");
  }
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(json.message ?? `요청 실패 (${res.status})`);
  return json.data as T;
}

// ponytail: 리프레시 토큰 흐름 없음. 액세스 토큰 만료(10시간)되면 그냥 다시 로그인.
