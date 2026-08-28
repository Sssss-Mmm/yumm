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

/** 매칭된 그룹에서 이탈한다. 남은 인원은 서버가 다시 WAITING으로 되돌린다. */
export const leaveGroup = () => api<void>("/match/group", "DELETE");

/** 신고. reason은 서버 enum 값(HARASSMENT 등). */
export const reportUser = (reportedUserId: number, reason: string, detail?: string) =>
  api<void>("/reports", "POST", { reportedUserId, reason, detail });

/** 차단. 멱등이며 해제 API는 없다. 이후 편성에만 반영되고 현재 그룹은 유지된다. */
export const blockUser = (userId: number) => api<void>(`/blocks/${userId}`, "POST");
