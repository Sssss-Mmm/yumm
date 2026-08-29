import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  ApiError, api, blockUser, confirmEmailCode, getMatchStatus, leaveGroup, myUserId, reportUser, sendEmailCode,
  type MatchMember as Member, type MatchStatus,
} from "../api";
import { btn, btnGhost, btnSm, card, h1, input, label, muted } from "../ui";

// 서버 Region enum과 1:1. 지역은 버킷 키라 자유 입력이면 "강남"/"강남구"가 갈라진다 → 고정 선택지.
// ponytail: 목록은 프론트 상수. 지역이 자주 바뀌면 그때 서버에서 내려받는다.
const REGIONS = {
  GANGNAM: "강남", HONGDAE: "홍대", SINCHON: "신촌", KONDAE: "건대",
  JONGNO: "종로", YEOUIDO: "여의도", PANGYO: "판교",
};
const MEAL_TIMES = { LUNCH: "점심", DINNER: "저녁" };
const GENDER_PREFS = { ANY: "상관없음", SAME_ONLY: "동성만" };
const FOODS = {
  KOREAN: "한식", CHINESE: "중식", JAPANESE: "일식", WESTERN: "양식",
  ASIAN: "아시안", FASTFOOD: "분식/패스트푸드", CAFE: "카페/디저트",
};

const REPORT_REASONS = {
  HARASSMENT: "괴롭힘·불쾌한 언행",
  OFF_PURPOSE: "목적 이탈 (영업·홍보·데이팅)",
  NO_SHOW: "약속 불이행",
  INAPPROPRIATE_PROFILE: "부적절한 프로필",
  OTHER: "기타",
};

// 서버 응답 그대로에 이 화면이 쓰는 라벨 키만 좁혀 붙인다.
type Status = MatchStatus & { region: keyof typeof REGIONS; mealTime: keyof typeof MEAL_TIMES };

// 인증 때문에 신청이 막히면 이 값을 들고 있다가 인증 성공 후 그대로 다시 보낸다.
type ApplyPayload = {
  region: FormDataEntryValue | null;
  mealDate: FormDataEntryValue | null;
  mealTime: FormDataEntryValue | null;
  genderPreference: FormDataEntryValue | null;
  foodPreferences: FormDataEntryValue[];
  allowPair: boolean;
};

// 로컬 시간대 기준 YYYY-MM-DD. toISOString()은 UTC라 KST 00:00~09:00 사이에 어제가 나온다.
// 예: KST 2026-08-29 08:00 → "2026-08-29" (toISOString()은 "2026-08-28")
export const localDate = (d: Date = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

const today = localDate();

// 낯선 사람과 대면하는 서비스의 최소 고지. 한 번 확인하면 이 브라우저에서는 다시 띄우지 않는다.
// ponytail: localStorage 플래그 하나. 기기별로 다시 뜨는 건 감수한다 — 서버 저장은 요청받지 않았다.
const SAFETY_ACK_KEY = "safetyNoticeAcknowledged";
const SAFETY_NOTICE = [
  "만나기 전 안전 수칙을 확인해 주세요.",
  "",
  "· 첫 만남은 사람이 많은 공공장소에서 하세요.",
  "· 집 주소, 직장 등 개인정보는 공유하지 마세요.",
  "· 불편하면 언제든 그룹에서 나가고 신고할 수 있습니다.",
  "· 금전 거래, 영업·홍보, 데이팅 목적의 이용은 금지입니다.",
  "",
  "확인을 누르면 신청이 접수됩니다.",
].join("\n");

const Match = () => {
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState("");
  const [disbanded, setDisbanded] = useState(false);
  const [leaving, setLeaving] = useState(false);
  // ponytail: 신고/차단은 한 번에 하나. 그룹원별 상태 맵 대신 진행 중인 userId 하나만 들고 간다.
  const [busy, setBusy] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ userId: number; text: string; ok: boolean } | null>(null);
  // FR-M-14: 첫 신청 직전의 이메일 인증. pending이 있으면 인증 창이 뜨고, 통과하면 이 신청이 이어진다.
  const [pending, setPending] = useState<ApplyPayload | null>(null);
  const [sending, setSending] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verifyNotice, setVerifyNotice] = useState<{ text: string; ok: boolean } | null>(null);
  const prev = useRef<Status["status"] | null>(null);
  // members는 나를 포함한 그룹 전원이다. 자기 자신은 신고·차단 대상이 아니다(서버가 400).
  // 토큰이 없거나 깨져 null이면 누가 나인지 알 수 없으니 전원에게 그대로 노출한다 — 서버가 막는다.
  const me = myUserId();

  // 신청 이력이 없으면 서버가 404를 준다. 그건 에러가 아니라 "신청 폼을 보여줄 때"라는 뜻.
  // 상태값은 늘지 않는다 → 해체는 MATCHED에서 WAITING + groupId null로 되돌아온 것으로 감지한다.
  const load = () =>
    getMatchStatus<Status>()
      .then((next) => {
        if (prev.current === "MATCHED" && next.status === "WAITING" && next.groupId === null) {
          setDisbanded(true);
        }
        prev.current = next.status;
        setStatus(next);
      })
      .catch(() => {
        prev.current = null;
        setStatus(null);
      });

  useEffect(() => { load(); }, []);

  // 대기/매칭 중에는 계속 폴링. 매칭 후에도 그룹이 해체될 수 있으므로 MATCHED에서 멈추면 안 된다.
  useEffect(() => {
    if (status?.status !== "WAITING" && status?.status !== "MATCHED") return;
    const id = setInterval(load, 10_000);
    return () => clearInterval(id);
  }, [status?.status]);

  const sendCode = async () => {
    setSending(true);
    setVerifyNotice(null);
    try {
      await sendEmailCode();
      setVerifyNotice({ text: "인증 코드를 메일로 보냈습니다. 메일함을 확인해 주세요.", ok: true });
    } catch (err) {
      setVerifyNotice({ text: (err as Error).message, ok: false });
    } finally {
      setSending(false);
    }
  };

  // 신청은 이 함수 하나로만 나간다 — 최초 제출도, 인증 통과 후 재시도도 같은 payload를 쓴다.
  const submit = async (payload: ApplyPayload) => {
    try {
      const next = await api<Status>("/match", "POST", payload);
      setPending(null);
      prev.current = next.status;
      setStatus(next);
    } catch (err) {
      if (err instanceof ApiError && err.code === "EMAIL_NOT_VERIFIED") {
        setPending(payload);   // 폼은 그대로 둔 채 인증 창만 덮는다 (입력값 보존)
        await sendCode();
        return;
      }
      // 인증과 무관한 실패는 인증 창을 닫고 폼 위에 보여준다 — 창이 덮고 있으면 에러가 안 보인다.
      setPending(null);
      setError((err as Error).message);
    }
  };

  const apply = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setDisbanded(false);
    const form = new FormData(e.currentTarget);
    // 신청이 나가기 전에 고지한다. 취소하면 아무것도 보내지 않는다.
    if (!localStorage.getItem(SAFETY_ACK_KEY)) {
      if (!confirm(SAFETY_NOTICE)) return;
      localStorage.setItem(SAFETY_ACK_KEY, "1");
    }
    await submit({
      region: form.get("region"),
      mealDate: form.get("mealDate"),
      mealTime: form.get("mealTime"),
      genderPreference: form.get("genderPreference"),
      foodPreferences: form.getAll("foodPreferences"),
      // 체크 안 하면 FormData에 키 자체가 없다 → false (FR-M-12 기본값)
      allowPair: form.has("allowPair"),
    });
  };

  const verify = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!pending) return;
    const code = String(new FormData(e.currentTarget).get("code") ?? "").trim();
    setVerifying(true);
    setVerifyNotice(null);
    try {
      await confirmEmailCode(code);
      await submit(pending);   // 사용자가 하려던 신청을 그대로 이어서 보낸다
    } catch (err) {
      setVerifyNotice({ text: (err as Error).message, ok: false });
    } finally {
      setVerifying(false);
    }
  };

  const cancel = async () => {
    try {
      await api("/match", "DELETE");
      prev.current = null;
      setStatus(null);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const leave = async () => {
    if (!confirm("그룹에서 나가시겠습니까? 채팅방에도 다시 들어갈 수 없습니다.")) return;
    setError("");
    setLeaving(true);
    try {
      await leaveGroup();
      prev.current = null;   // 나간 사람에게 해체 배너를 띄우지 않는다
      setDisbanded(false);
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLeaving(false);
    }
  };

  const report = async (e: React.FormEvent<HTMLFormElement>, userId: number) => {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    setNotice(null);
    setBusy(userId);
    try {
      await reportUser(userId, data.get("reason") as string, (data.get("detail") as string) || undefined);
      form.closest("details")?.removeAttribute("open");
      form.reset();
      setNotice({ userId, text: "신고를 접수했습니다.", ok: true });
    } catch (err) {
      setNotice({ userId, text: (err as Error).message, ok: false });
    } finally {
      setBusy(null);
    }
  };

  // 차단은 이후 편성에만 적용된다. 지금 그룹은 유지되므로 확인 문구에서 "그룹 나가기"와 구분해준다.
  const block = async (m: Member) => {
    if (!confirm(
      `${m.nickname}님을 차단하시겠습니까?\n차단은 해제할 수 없습니다.\n앞으로 매칭에서 다시 만나지 않지만, 지금 그룹은 그대로 유지됩니다. 이 그룹에서 나가려면 "그룹 나가기"를 사용하세요.`
    )) return;
    setNotice(null);
    setBusy(m.userId);
    try {
      await blockUser(m.userId);
      setNotice({ userId: m.userId, text: "차단했습니다. 이후 매칭에서 제외됩니다.", ok: true });
    } catch (err) {
      setNotice({ userId: m.userId, text: (err as Error).message, ok: false });
    } finally {
      setBusy(null);
    }
  };

  if (status?.status === "WAITING" || status?.status === "MATCHED") {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            {status.status === "WAITING"
              ? <span className="h-2 w-2 animate-pulse rounded-full bg-orange-500" />
              : <span className="h-2 w-2 rounded-full bg-emerald-500" />}
            <h1 className={h1}>
              {status.status === "WAITING" ? "밥메이트 찾는 중" : "매칭 완료!"}
            </h1>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {[REGIONS[status.region], status.mealDate, MEAL_TIMES[status.mealTime]].map((t) => (
              <span key={t} className="rounded-full bg-stone-100 px-2.5 py-1 text-xs font-medium text-stone-600">
                {t}
              </span>
            ))}
          </div>
        </div>
        {status.status === "WAITING" ? (
          <>
            {disbanded && (
              <p className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
                그룹이 해체되어 다시 대기 중입니다.
              </p>
            )}
            <div className={`${card} flex flex-col items-center gap-1 text-center`}>
              <p className="text-sm text-stone-600">조건이 맞는 사람을 찾고 있어요</p>
              <p className="text-2xl font-bold tracking-tight">
                {new Date(status.expiresAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
              </p>
              <p className={muted}>까지 기다립니다</p>
            </div>
            <button onClick={cancel} className={btnGhost}>신청 취소</button>
          </>
        ) : (
          <>
            <ul className="flex flex-col gap-2">
              {status.members.map((m) => {
                const isMe = me !== null && m.userId === me;
                return (
                <li key={m.userId} className="flex flex-col gap-2 rounded-2xl border border-stone-200 bg-white p-3.5 shadow-sm">
                  <div className="flex items-center justify-between gap-2">
                    <span className="flex items-center gap-2.5">
                      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-orange-100 text-sm font-semibold text-orange-700">
                        {m.nickname.slice(0, 1)}
                      </span>
                      <span className="font-medium">
                        {m.nickname}
                        {isMe && <span className="ml-1 text-sm font-normal text-stone-400">(나)</span>}
                      </span>
                    </span>
                    {!isMe && (
                      <button
                        type="button"
                        onClick={() => block(m)}
                        disabled={busy === m.userId}
                        className={btnSm}
                      >
                        차단
                      </button>
                    )}
                  </div>
                  {/* ponytail: 네이티브 details — 열림 상태를 React state로 들 이유가 없다 */}
                  {!isMe && (
                    <details className="text-sm">
                      <summary className="cursor-pointer text-stone-500 hover:text-stone-800">신고</summary>
                      <form onSubmit={(e) => report(e, m.userId)} className="flex flex-col gap-2 pt-2">
                        <label className="flex flex-col gap-1.5"><span className={label}>사유</span>
                          <select name="reason" required className={input}>
                            {Object.entries(REPORT_REASONS).map(([v, label]) => (
                              <option key={v} value={v}>{label}</option>
                            ))}
                          </select>
                        </label>
                        <label className="flex flex-col gap-1.5"><span className={label}>상세 내용 (선택)</span>
                          <textarea name="detail" rows={2} className={input} />
                        </label>
                        <button disabled={busy === m.userId} className={btn}>
                          {busy === m.userId ? "보내는 중…" : "신고 보내기"}
                        </button>
                      </form>
                    </details>
                  )}
                  {!isMe && notice?.userId === m.userId && (
                    <p className={`text-sm ${notice.ok ? "text-emerald-700" : "text-red-600"}`}>{notice.text}</p>
                  )}
                </li>
                );
              })}
            </ul>
            {/* 채팅방 입장이 곧 참석 의사 표시다 (FR-C-01) */}
            <div className="flex gap-2">
              {status.groupId && (
                <Link to={`/chat/${status.groupId}`} className={`${btn} flex-1`}>
                  채팅방 입장
                </Link>
              )}
              <button onClick={leave} disabled={leaving} className={`${btnGhost} flex-1`}>
                {leaving ? "나가는 중…" : "그룹 나가기"}
              </button>
            </div>
          </>
        )}
        {error && <p className="text-red-600 text-sm">{error}</p>}
      </div>
    );
  }

  return (
    <>
    <form onSubmit={apply} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h1 className={h1}>밥메이트 신청</h1>
        <p className={muted}>조건이 맞는 3~4명을 묶어드려요.</p>
      </div>
      {status && (
        <p className="rounded-xl bg-stone-100 px-3 py-2 text-sm text-stone-500">
          지난 신청: {status.status === "TIMEOUT" ? "시간 초과" : "취소됨"}
        </p>
      )}

      <div className={`${card} flex flex-col gap-3`}>
        <label className="flex flex-col gap-1.5">
          <span className={label}>지역</span>
          <select name="region" required className={input}>
            {Object.entries(REGIONS).map(([v, text]) => <option key={v} value={v}>{text}</option>)}
          </select>
        </label>

        <div className="flex gap-3">
          <label className="flex flex-1 flex-col gap-1.5">
            <span className={label}>날짜</span>
            <input name="mealDate" type="date" required defaultValue={today} min={today} className={input} />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className={label}>점심/저녁</span>
            <select name="mealTime" required className={input}>
              {Object.entries(MEAL_TIMES).map(([v, text]) => <option key={v} value={v}>{text}</option>)}
            </select>
          </label>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className={label}>상대 성별</span>
          <select name="genderPreference" required className={input}>
            {Object.entries(GENDER_PREFS).map(([v, text]) => <option key={v} value={v}>{text}</option>)}
          </select>
        </label>
      </div>

      {/* ponytail: peer + sr-only. 체크박스를 그대로 두니 폼 전송도 키보드 접근도 공짜다 */}
      <fieldset className={`${card} flex flex-col gap-2.5`}>
        <legend className="sr-only">선호 음식</legend>
        <span className={label}>선호 음식 <span className="text-stone-400">(1개 이상)</span></span>
        <div className="flex flex-wrap gap-2">
          {Object.entries(FOODS).map(([v, text]) => (
            <label key={v} className="cursor-pointer">
              <input type="checkbox" name="foodPreferences" value={v} className="peer sr-only" />
              <span className="block rounded-full border border-stone-300 bg-white px-3.5 py-1.5 text-sm text-stone-600 transition peer-checked:border-orange-600 peer-checked:bg-orange-600 peer-checked:text-white peer-focus-visible:ring-2 peer-focus-visible:ring-orange-200">
                {text}
              </span>
            </label>
          ))}
        </div>
      </fieldset>

      {/* FR-M-12: 기본 해제. FR-M-13: 고지는 접지 않고 상시 노출한다 */}
      <div className={`${card} flex flex-col gap-2`}>
        <label className="flex cursor-pointer items-center gap-2.5">
          <input type="checkbox" name="allowPair" className="h-4 w-4 shrink-0 accent-orange-600" />
          <span className={label}>2명이어도 괜찮아요</span>
        </label>
        <p className={muted}>
          3~4명을 못 모으면 2명으로 만날 수 있어요. 2명은 상대가 나오지 않으면 만남 자체가 성립하지 않습니다.
        </p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      <button className={btn}>신청하기</button>
    </form>

    {/* 폼 위에 덮기만 한다 — 폼을 언마운트하면 채워둔 값이 날아간다 */}
    {pending && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-stone-900/40 p-4">
        <div role="dialog" aria-modal="true" aria-label="이메일 인증" className={`${card} w-full max-w-sm flex flex-col gap-3`}>
          <div className="flex flex-col gap-1">
            <h2 className="text-lg font-bold tracking-tight">이메일 인증</h2>
            <p className={muted}>
              신청 전에 한 번만 확인해요. 가입한 메일로 보낸 인증 코드를 입력해 주세요.
            </p>
          </div>
          <form onSubmit={verify} className="flex flex-col gap-3">
            <label className="flex flex-col gap-1.5">
              <span className={label}>인증 코드</span>
              <input
                name="code"
                required
                autoFocus
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="메일로 받은 코드"
                className={input}
              />
            </label>
            {verifyNotice && (
              <p className={`text-sm ${verifyNotice.ok ? "text-emerald-700" : "text-red-600"}`}>
                {verifyNotice.text}
              </p>
            )}
            <button disabled={verifying || sending} className={btn}>
              {verifying ? "확인 중…" : "인증하고 신청하기"}
            </button>
          </form>
          <div className="flex gap-2">
            <button type="button" onClick={sendCode} disabled={sending || verifying} className={`${btnGhost} flex-1`}>
              {sending ? "보내는 중…" : "코드 재발송"}
            </button>
            <button
              type="button"
              onClick={() => { setPending(null); setVerifyNotice(null); }}
              disabled={verifying}
              className={`${btnGhost} flex-1`}
            >
              나중에 하기
            </button>
          </div>
        </div>
      </div>
    )}
    </>
  );
};

export default Match;
