import { useEffect, useState } from "react";
import { ApiError, MEAL_TIMES, REGIONS, getPendingAttendance, myUserId, submitAttendance, type MatchStatus } from "../api";
import { btn, card, label, muted } from "../ui";

// 매칭 화면 칩과 같은 sr-only 라디오 + peer 스타일.
const chip =
  "block rounded-lg border border-stone-300 bg-white px-3 py-1.5 text-sm text-stone-700 transition " +
  "peer-checked:border-orange-600 peer-checked:bg-orange-50 peer-checked:text-orange-800 " +
  "peer-focus-visible:ring-2 peer-focus-visible:ring-orange-200";

/**
 * 만남 뒤 참석 상호 체크(FR-C-06). 체크할 그룹이 없으면 아무것도 그리지 않는다.
 * 매칭 화면 어느 상태에서든 위에 붙는다 — 재신청해서 최근 신청이 바뀌어도 지난 그룹 체크는 남아야 한다.
 */
const AttendanceCheck = () => {
  const [pending, setPending] = useState<MatchStatus[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [submitting, setSubmitting] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [done, setDone] = useState(false);
  const me = myUserId();

  const load = () => {
    setLoadError("");
    getPendingAttendance()
      .then((list) => setPending(list ?? []))
      .catch(() => setLoadError("참석 체크 목록을 불러오지 못했어요."));
  };
  useEffect(load, []);

  const submit = async (e: React.FormEvent<HTMLFormElement>, group: MatchStatus) => {
    e.preventDefault();
    const groupId = group.groupId!;
    const data = new FormData(e.currentTarget);
    const others = group.members.filter((m) => m.userId !== me);
    setSubmitting(groupId);
    setErrors((prev) => ({ ...prev, [groupId]: "" }));
    try {
      await submitAttendance(groupId, others.map((m) => ({ userId: m.userId, attended: data.get(`m${m.userId}`) === "yes" })));
      setPending((prev) => prev?.filter((g) => g.groupId !== groupId) ?? null);
      setDone(true);
    } catch (err) {
      // 다른 탭에서 이미 냈으면 목록에서 빼면 끝이다.
      if (err instanceof ApiError && err.code === "ATTENDANCE_ALREADY_CHECKED") {
        setPending((prev) => prev?.filter((g) => g.groupId !== groupId) ?? null);
        return;
      }
      setErrors((prev) => ({ ...prev, [groupId]: err instanceof Error ? err.message : "제출하지 못했어요." }));
    } finally {
      setSubmitting(null);
    }
  };

  if (loadError) {
    return (
      <p className="flex items-center justify-between rounded-xl bg-stone-100 px-3 py-2 text-sm text-stone-500">
        {loadError}
        <button onClick={load} className="font-medium text-orange-700 hover:underline">다시 시도</button>
      </p>
    );
  }
  // 로딩 중과 빈 목록은 둘 다 안 그린다 — 보조 기능이라 화면을 밀어내지 않는다.
  if (!pending || pending.length === 0) {
    return done ? (
      <p className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
        참석 체크를 제출했어요. 고마워요.
      </p>
    ) : null;
  }

  return (
    <div className="flex flex-col gap-3">
      {pending.map((group) => {
        const groupId = group.groupId!;
        const others = group.members.filter((m) => m.userId !== me);
        const region = REGIONS[group.region as keyof typeof REGIONS] ?? group.region;
        const mealTime = MEAL_TIMES[group.mealTime as keyof typeof MEAL_TIMES] ?? group.mealTime;
        return (
          <form key={groupId} onSubmit={(e) => submit(e, group)} className={`${card} flex flex-col gap-3`}>
            <div className="flex flex-col gap-0.5">
              <p className="font-semibold text-stone-900">밥약속, 다들 나왔나요?</p>
              <p className={muted}>
                {region} · {group.mealDate} · {mealTime}. 만남이 끝난 뒤에 체크해 주세요. 다른 그룹원에게는 보이지 않아요.
              </p>
            </div>
            {others.map((m) => (
              <fieldset key={m.userId} className="flex items-center justify-between gap-2">
                <legend className="sr-only">{m.nickname} 참석 여부</legend>
                <span className={label}>{m.nickname}</span>
                <div className="flex gap-1.5">
                  {[["yes", "왔어요"], ["no", "안 왔어요"]].map(([v, text]) => (
                    <label key={v} className="cursor-pointer">
                      <input type="radio" name={`m${m.userId}`} value={v} required className="peer sr-only" />
                      <span className={chip}>{text}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
            {errors[groupId] && <p className="text-sm text-red-600">{errors[groupId]}</p>}
            <button disabled={submitting === groupId} className={btn}>
              {submitting === groupId ? "제출 중…" : "제출"}
            </button>
          </form>
        );
      })}
    </div>
  );
};

export default AttendanceCheck;
