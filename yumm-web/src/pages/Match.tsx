import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api, blockUser, leaveGroup, reportUser } from "../api";
import { myUserId } from "../ws";

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

type Member = { userId: number; nickname: string; profileImageUrl: string | null };
type Status = {
  status: "WAITING" | "MATCHED" | "CANCELLED" | "TIMEOUT";
  groupId: string | null;
  region: keyof typeof REGIONS;
  mealDate: string;
  mealTime: keyof typeof MEAL_TIMES;
  expiresAt: string;
  members: Member[];
};

const today = new Date().toISOString().slice(0, 10);

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
  const prev = useRef<Status["status"] | null>(null);
  // members는 나를 포함한 그룹 전원이다. 자기 자신은 신고·차단 대상이 아니다(서버가 400).
  // 토큰이 없거나 깨져 null이면 누가 나인지 알 수 없으니 전원에게 그대로 노출한다 — 서버가 막는다.
  const me = myUserId();

  // 신청 이력이 없으면 서버가 404를 준다. 그건 에러가 아니라 "신청 폼을 보여줄 때"라는 뜻.
  // 상태값은 늘지 않는다 → 해체는 MATCHED에서 WAITING + groupId null로 되돌아온 것으로 감지한다.
  const load = () =>
    api<Status>("/match")
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
    try {
      const next = await api<Status>("/match", "POST", {
        region: form.get("region"),
        mealDate: form.get("mealDate"),
        mealTime: form.get("mealTime"),
        genderPreference: form.get("genderPreference"),
        foodPreferences: form.getAll("foodPreferences"),
      });
      prev.current = next.status;
      setStatus(next);
    } catch (err) {
      setError((err as Error).message);
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
      <div className="max-w-sm mx-auto flex flex-col gap-3">
        <h1 className="text-2xl font-bold">
          {status.status === "WAITING" ? "밥메이트 찾는 중…" : "매칭 완료!"}
        </h1>
        <p className="text-gray-600">
          {REGIONS[status.region]} · {status.mealDate} · {MEAL_TIMES[status.mealTime]}
        </p>
        {status.status === "WAITING" ? (
          <>
            {disbanded && (
              <p className="border border-amber-300 bg-amber-50 text-amber-800 rounded p-2 text-sm">
                그룹이 해체되어 다시 대기 중입니다.
              </p>
            )}
            <p className="text-sm text-gray-500">
              {new Date(status.expiresAt).toLocaleTimeString()}까지 기다립니다.
            </p>
            <button onClick={cancel} className="border rounded p-2">신청 취소</button>
          </>
        ) : (
          <>
            <ul className="flex flex-col gap-2">
              {status.members.map((m) => {
                const isMe = me !== null && m.userId === me;
                return (
                <li key={m.userId} className="border rounded p-2 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <span>
                      {m.nickname}
                      {isMe && <span className="ml-1 text-sm text-gray-500">(나)</span>}
                    </span>
                    {!isMe && (
                      <button
                        type="button"
                        onClick={() => block(m)}
                        disabled={busy === m.userId}
                        className="text-sm border rounded px-2 py-1 disabled:opacity-50"
                      >
                        차단
                      </button>
                    )}
                  </div>
                  {/* ponytail: 네이티브 details — 열림 상태를 React state로 들 이유가 없다 */}
                  {!isMe && (
                    <details className="text-sm">
                      <summary className="cursor-pointer text-gray-600">신고</summary>
                      <form onSubmit={(e) => report(e, m.userId)} className="flex flex-col gap-2 pt-2">
                        <label className="flex flex-col gap-1">사유
                          <select name="reason" required className="border rounded p-2">
                            {Object.entries(REPORT_REASONS).map(([v, label]) => (
                              <option key={v} value={v}>{label}</option>
                            ))}
                          </select>
                        </label>
                        <label className="flex flex-col gap-1">상세 내용 (선택)
                          <textarea name="detail" rows={2} className="border rounded p-2" />
                        </label>
                        <button disabled={busy === m.userId} className="bg-blue-600 text-white rounded p-2 disabled:opacity-50">
                          {busy === m.userId ? "보내는 중…" : "신고 보내기"}
                        </button>
                      </form>
                    </details>
                  )}
                  {!isMe && notice?.userId === m.userId && (
                    <p className={`text-sm ${notice.ok ? "text-green-700" : "text-red-600"}`}>{notice.text}</p>
                  )}
                </li>
                );
              })}
            </ul>
            {/* 채팅방 입장이 곧 참석 의사 표시다 (FR-C-01) */}
            <div className="flex gap-2">
              {status.groupId && (
                <Link to={`/chat/${status.groupId}`} className="flex-1 bg-blue-600 text-white rounded p-2 text-center">
                  채팅방 입장
                </Link>
              )}
              <button onClick={leave} disabled={leaving} className="flex-1 border rounded p-2 disabled:opacity-50">
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
    <form onSubmit={apply} className="max-w-sm mx-auto flex flex-col gap-3">
      <h1 className="text-2xl font-bold">밥메이트 신청</h1>
      {status && <p className="text-sm text-gray-500">지난 신청: {status.status === "TIMEOUT" ? "시간 초과" : "취소됨"}</p>}

      <label className="flex flex-col gap-1">지역
        <select name="region" required className="border rounded p-2">
          {Object.entries(REGIONS).map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </label>

      <label className="flex flex-col gap-1">날짜
        <input name="mealDate" type="date" required defaultValue={today} min={today} className="border rounded p-2" />
      </label>

      <label className="flex flex-col gap-1">점심/저녁
        <select name="mealTime" required className="border rounded p-2">
          {Object.entries(MEAL_TIMES).map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </label>

      <label className="flex flex-col gap-1">상대 성별
        <select name="genderPreference" required className="border rounded p-2">
          {Object.entries(GENDER_PREFS).map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </label>

      <fieldset className="flex flex-wrap gap-3">
        <legend className="mb-1">선호 음식 (1개 이상)</legend>
        {Object.entries(FOODS).map(([v, label]) => (
          <label key={v} className="flex gap-1 items-center">
            <input type="checkbox" name="foodPreferences" value={v} /> {label}
          </label>
        ))}
      </fieldset>

      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button className="bg-blue-600 text-white rounded p-2">신청하기</button>
    </form>
  );
};

export default Match;
