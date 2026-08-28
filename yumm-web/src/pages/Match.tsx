import { useEffect, useState } from "react";
import { api } from "../api";

// 서버 enum과 1:1. 지역은 버킷 키라 자유 입력이면 "강남"/"강남구"가 갈라진다 → 고정 선택지.
const REGIONS = ["강남", "홍대", "신촌", "건대", "종로", "여의도", "판교"];
const MEAL_TIMES = { LUNCH: "점심", DINNER: "저녁" };
const GENDER_PREFS = { ANY: "상관없음", SAME_ONLY: "동성만" };
const FOODS = {
  KOREAN: "한식", CHINESE: "중식", JAPANESE: "일식", WESTERN: "양식",
  ASIAN: "아시안", FASTFOOD: "분식/패스트푸드", CAFE: "카페/디저트",
};

type Member = { userId: number; nickname: string; profileImageUrl: string | null };
type Status = {
  status: "WAITING" | "MATCHED" | "CANCELLED" | "TIMEOUT";
  groupId: string | null;
  region: string;
  mealDate: string;
  mealTime: keyof typeof MEAL_TIMES;
  expiresAt: string;
  members: Member[];
};

const today = new Date().toISOString().slice(0, 10);

const Match = () => {
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState("");

  // 신청 이력이 없으면 서버가 404를 준다. 그건 에러가 아니라 "신청 폼을 보여줄 때"라는 뜻.
  const load = () => api<Status>("/match").then(setStatus).catch(() => setStatus(null));

  useEffect(() => { load(); }, []);

  // 대기 중일 때만 폴링. 서버가 신청 시점에만 편성을 돌리므로 상태는 내가 물어봐야 안다.
  useEffect(() => {
    if (status?.status !== "WAITING") return;
    const id = setInterval(load, 10_000);
    return () => clearInterval(id);
  }, [status?.status]);

  const apply = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    const form = new FormData(e.currentTarget);
    try {
      setStatus(await api<Status>("/match", "POST", {
        region: form.get("region"),
        mealDate: form.get("mealDate"),
        mealTime: form.get("mealTime"),
        genderPreference: form.get("genderPreference"),
        foodPreferences: form.getAll("foodPreferences"),
      }));
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const cancel = async () => {
    try {
      await api("/match", "DELETE");
      setStatus(null);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (status?.status === "WAITING" || status?.status === "MATCHED") {
    return (
      <div className="max-w-sm mx-auto flex flex-col gap-3">
        <h1 className="text-2xl font-bold">
          {status.status === "WAITING" ? "밥메이트 찾는 중…" : "매칭 완료!"}
        </h1>
        <p className="text-gray-600">
          {status.region} · {status.mealDate} · {MEAL_TIMES[status.mealTime]}
        </p>
        {status.status === "WAITING" ? (
          <>
            <p className="text-sm text-gray-500">
              {new Date(status.expiresAt).toLocaleTimeString()}까지 기다립니다.
            </p>
            <button onClick={cancel} className="border rounded p-2">신청 취소</button>
          </>
        ) : (
          <ul className="flex flex-col gap-2">
            {status.members.map((m) => (
              <li key={m.userId} className="border rounded p-2">{m.nickname}</li>
            ))}
          </ul>
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
          {REGIONS.map((r) => <option key={r}>{r}</option>)}
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
