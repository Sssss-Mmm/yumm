import { useEffect, useState } from "react";
import { MEAL_TIMES, REGIONS, getStats, localDate, type AdminStats as Stats } from "../api";
import { card, input, label, muted } from "../ui";

const pct = (v: number | null) => (v === null ? "—" : `${v}%`);

/** 지표 한 칸. 목표(product-plan 2절)를 넘겼는지 색으로만 표시한다. 값이 없으면 판단하지 않는다. */
const Tile = ({ title, value, sub, ok }: { title: string; value: string; sub: string; ok: boolean | null }) => (
  <div className={`${card} flex flex-col gap-0.5 p-4`}>
    <span className="text-xs text-stone-500">{title}</span>
    <span className={`text-xl font-bold ${ok === null ? "text-stone-900" : ok ? "text-emerald-700" : "text-red-600"}`}>
      {value}
    </span>
    <span className="text-xs text-stone-400">{sub}</span>
  </div>
);

const check = (v: number | null, pass: (v: number) => boolean) => (v === null ? null : pass(v));

/** 운영 지표(FR-D-03). 버킷 설계와 2인 폴백 유지 여부를 판단하는 근거다. */
const AdminStats = () => {
  const [from, setFrom] = useState(localDate(new Date(Date.now() - 6 * 86_400_000)));
  const [to, setTo] = useState(localDate());
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setError("");
    setStats(null);
    getStats(from, to).then(setStats).catch((e) => setError((e as Error).message));
  }, [from, to]);

  const m = stats?.meetings;
  // 2인 vs 3~4인 노쇼 격차(목표 2배 이내). 2인은 노쇼 판정이 불가능해 "안 왔어요 1건 이상" 비율끼리 비교한다.
  const gap = m && m.pair.flaggedRate !== null && m.group.flaggedRate ? m.pair.flaggedRate / m.group.flaggedRate : null;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-bold text-stone-900">운영 지표</h2>
        <p className={muted}>식사 날짜 기준. 초록은 목표 달성, 빨강은 미달입니다.</p>
      </div>
      <div className="flex gap-2">
        <label className="flex flex-1 flex-col gap-1">
          <span className={label}>시작</span>
          <input type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} className={input} />
        </label>
        <label className="flex flex-1 flex-col gap-1">
          <span className={label}>끝</span>
          <input type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} className={input} />
        </label>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!stats && !error && <p className={muted}>불러오는 중…</p>}
      {stats && stats.funnel.requests === 0 && m?.groups === 0 && (
        <div className={`${card} text-center`}><p className={muted}>이 기간에는 신청이 없습니다.</p></div>
      )}
      {stats && m && (stats.funnel.requests > 0 || m.groups > 0) && (
        <>
          <div className="grid grid-cols-2 gap-2">
            <Tile title="매칭 성사율 (목표 60%↑)" value={pct(stats.funnel.matchRate)}
              sub={`${stats.funnel.matched} / ${stats.funnel.requests}건`} ok={check(stats.funnel.matchRate, (v) => v >= 60)} />
            <Tile title="평균 대기 (목표 15분↓)"
              value={stats.funnel.avgWaitMinutes === null ? "—" : `${stats.funnel.avgWaitMinutes}분`}
              sub="신청 → 첫 성사" ok={check(stats.funnel.avgWaitMinutes, (v) => v <= 15)} />
            <Tile title="만남 전환율 (목표 70%↑)" value={pct(m.conversionRate)}
              sub={`만남 ${m.metGroups} / 응답 ${m.respondedGroups} / 성사 ${m.groups}그룹`} ok={check(m.conversionRate, (v) => v >= 70)} />
            <Tile title="노쇼율 3~4인 (목표 15%↓)" value={pct(m.group.noShowRate)}
              sub={`${m.group.noShows} / ${m.group.judged}명 판정`} ok={check(m.group.noShowRate, (v) => v <= 15)} />
            <Tile title="2인 비중 (목표 30%↓)" value={pct(m.pairShare)}
              sub={`${m.pairGroups} / ${m.groups}그룹`} ok={check(m.pairShare, (v) => v <= 30)} />
            <Tile title="2인 노쇼 격차 (목표 2배↓)" value={gap === null ? "—" : `${gap.toFixed(1)}배`}
              sub={`'안 왔어요' 있음 2인 ${pct(m.pair.flaggedRate)} · 3~4인 ${pct(m.group.flaggedRate)}`}
              ok={check(gap, (v) => v <= 2)} />
          </div>

          <div className={`${card} overflow-x-auto p-0`}>
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-stone-500">
                <tr className="border-b border-stone-200">
                  <th className="px-4 py-2 font-medium">지역·끼니</th>
                  <th className="px-2 py-2 text-right font-medium">신청</th>
                  <th className="px-2 py-2 text-right font-medium">성사율</th>
                  <th className="px-4 py-2 text-right font-medium">평균 대기</th>
                </tr>
              </thead>
              <tbody>
                {stats.buckets.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-3 text-center text-stone-500">끝난 신청이 없습니다.</td></tr>
                )}
                {stats.buckets.map((b) => (
                  <tr key={`${b.region}-${b.mealTime}`} className="border-b border-stone-100 last:border-0">
                    <td className="px-4 py-2">
                      {REGIONS[b.region as keyof typeof REGIONS] ?? b.region} · {MEAL_TIMES[b.mealTime as keyof typeof MEAL_TIMES] ?? b.mealTime}
                    </td>
                    <td className="px-2 py-2 text-right">{b.funnel.requests}</td>
                    <td className="px-2 py-2 text-right">{pct(b.funnel.matchRate)}</td>
                    <td className="px-4 py-2 text-right">
                      {b.funnel.avgWaitMinutes === null ? "—" : `${b.funnel.avgWaitMinutes}분`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
};

export default AdminStats;
