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

/** 서버 에러의 `error` 코드를 살려둔다. 화면이 코드로 분기해야 할 때가 있다(EMAIL_NOT_VERIFIED 등). */
export class ApiError extends Error {
  code?: string;
  constructor(message: string, code?: string) {
    super(message);
    this.code = code;
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
  if (!res.ok) throw new ApiError(json.message ?? `요청 실패 (${res.status})`, json.error);
  return json.data as T;
}

// ponytail: 리프레시 토큰 흐름 없음. 액세스 토큰 만료(10시간)되면 그냥 다시 로그인.

/**
 * 액세스 토큰의 페이로드를 읽는다. 서명은 검증하지 않는다 — 화면 분기용이고
 * 실제 권한 판정은 서버가 한다(관리자 API는 SecurityConfig가 막는다).
 */
function claims(): Record<string, unknown> | null {
  const payload = getToken()?.split(".")[1];
  if (!payload) return null;
  try {
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}

/** 로그인 토큰의 sub 클레임 = 내 userId. 내 메시지 구분용. */
export function myUserId(): number | null {
  const id = Number(claims()?.sub);
  return Number.isFinite(id) ? id : null;
}

/** 관리자 메뉴를 보여줄지 결정한다. 화면 표시용일 뿐 접근 제어가 아니다. */
export const isAdmin = () =>
  (claims()?.roles as string[] | undefined)?.includes("ROLE_ADMIN") ?? false;

export type AdminReport = {
  id: number;
  reporterId: number;
  reporterNickname: string;
  reportedId: number;
  reportedNickname: string;
  reason: string;
  reasonLabel: string;
  detail: string | null;
  createdAt: string;
  handledAt: string | null;
};

/** 신고 목록(FR-D-01). 기본은 미처리만. */
export const getReports = (includeHandled = false) =>
  api<AdminReport[]>(`/admin/reports?includeHandled=${includeHandled}`);

export const handleReport = (id: number) =>
  api<AdminReport>(`/admin/reports/${id}/handle`, "POST");

/** 회원가입. agreedToTerms는 필수 동의라 서버가 false면 400을 준다. */
export type SignupRequest = {
  email: string;
  password: string;
  nickname: string;
  gender: string;
  birthYear: number;
  agreedToTerms: boolean;
};
export const signup = (body: SignupRequest) => api<void>("/user/signup", "POST", body);

export type MatchMember = { userId: number; nickname: string; profileImageUrl: string | null };

/**
 * GET /api/match 응답. 신청 이력이 없으면 서버가 404 → api()가 ApiError를 던진다.
 * region/mealTime은 서버 enum 이름(GANGNAM, LUNCH …) 그대로다.
 */
export type MatchStatus = {
  status: "WAITING" | "MATCHED" | "CANCELLED" | "TIMEOUT";
  groupId: string | null;
  region: string;
  mealDate: string;
  mealTime: string;
  expiresAt: string;
  members: MatchMember[];
};

/**
 * 내 매칭 현황. 매칭 화면과 채팅 화면이 같은 응답으로 그룹 해체를 감지한다(FR-C-04).
 * 화면이 region/mealTime을 좁힌 타입으로 쓰면 T로 넘긴다.
 */
export const getMatchStatus = <T extends MatchStatus = MatchStatus>() => api<T>("/match");

/** 매칭된 그룹에서 이탈한다. 남은 인원은 서버가 다시 WAITING으로 되돌린다. */
export const leaveGroup = () => api<void>("/match/group", "DELETE");

/** 신고. reason은 서버 enum 값(HARASSMENT 등). */
export const reportUser = (reportedUserId: number, reason: string, detail?: string) =>
  api<void>("/reports", "POST", { reportedUserId, reason, detail });

/** 차단. 멱등이며 해제 API는 없다. 이후 편성에만 반영되고 현재 그룹은 유지된다. */
export const blockUser = (userId: number) => api<void>(`/blocks/${userId}`, "POST");

/**
 * 이메일 인증 코드 발송(FR-M-14). 가입이 아니라 첫 매칭 신청 직전에만 호출한다.
 * 바디는 없다 — 대상 주소는 서버가 토큰의 사용자에서 가져온다.
 */
export const sendEmailCode = () => api<void>("/user/verify-email", "POST");

/** 받은 코드 확인. 성공하면 그 뒤의 매칭 신청이 통과한다(미인증이면 403 EMAIL_NOT_VERIFIED). */
export const confirmEmailCode = (code: string) =>
  api<void>("/user/verify-email/confirm", "POST", { code });
