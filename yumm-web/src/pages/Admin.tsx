import { useEffect, useState } from "react";
import { getReports, handleReport, setSuspended, type AdminReport } from "../api";
import { btnSm, card, h1, muted } from "../ui";

const when = (iso: string) => new Date(iso).toLocaleString([], {
  month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
});

const Admin = () => {
  const [reports, setReports] = useState<AdminReport[] | null>(null);
  const [includeHandled, setIncludeHandled] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = (withHandled: boolean) => {
    setError("");
    setReports(null);
    getReports(withHandled)
      .then((list) => setReports(list ?? []))
      .catch((e) => { setError((e as Error).message); setReports([]); });
  };

  useEffect(() => { load(includeHandled); }, [includeHandled]);

  const handle = async (id: number) => {
    setBusy(id);
    setError("");
    try {
      const updated = await handleReport(id);
      // 미처리 목록에서는 처리한 건이 사라지고, 전체 목록에서는 상태만 바뀐다.
      setReports((prev) => prev === null ? prev : includeHandled
        ? prev.map((r) => (r.id === id ? updated : r))
        : prev.filter((r) => r.id !== id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  // 한 사람이 여러 번 신고됐을 수 있다. 목록의 그 사람 행 전부를 같이 바꾼다.
  const suspend = async (r: AdminReport) => {
    const next = !r.reportedSuspended;
    if (next && !confirm(
      `${r.reportedNickname}님(#${r.reportedId})을 정지하시겠습니까?\n로그인이 막히고, 대기 중인 신청은 취소되며 속한 그룹에서 빠집니다.`
    )) return;
    setBusy(r.id);
    setError("");
    try {
      await setSuspended(r.reportedId, next);
      setReports((prev) => prev?.map((x) => (x.reportedId === r.reportedId ? { ...x, reportedSuspended: next } : x)) ?? prev);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h1 className={h1}>신고 처리</h1>
        <p className={muted}>접수된 순서대로 처리합니다.</p>
      </div>

      <label className="flex items-center gap-2 text-sm text-stone-600">
        <input
          type="checkbox"
          checked={includeHandled}
          onChange={(e) => setIncludeHandled(e.target.checked)}
          className="h-4 w-4 accent-orange-600"
        />
        처리된 신고도 보기
      </label>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {reports === null && <p className={muted}>불러오는 중…</p>}
      {reports !== null && reports.length === 0 && !error && (
        <div className={`${card} text-center`}>
          <p className={muted}>{includeHandled ? "신고가 없습니다." : "미처리 신고가 없습니다."}</p>
        </div>
      )}

      <ul className="flex flex-col gap-2.5">
        {reports?.map((r) => (
          <li key={r.id} className={`${card} flex flex-col gap-2.5 p-4`}>
            <div className="flex items-start justify-between gap-2">
              <div className="flex flex-col gap-1">
                <span className="text-sm">
                  <span className="font-semibold">{r.reportedNickname}</span>
                  <span className="text-stone-400"> #{r.reportedId}</span>
                  {r.reportedSuspended && (
                    <span className="ml-1.5 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">정지됨</span>
                  )}
                </span>
                <span className="text-xs text-stone-500">
                  신고자 {r.reporterNickname} · {when(r.createdAt)}
                </span>
              </div>
              {r.handledAt ? (
                <span className="shrink-0 rounded-full bg-stone-100 px-2.5 py-1 text-xs text-stone-500">
                  처리됨
                </span>
              ) : (
                <button onClick={() => handle(r.id)} disabled={busy === r.id} className={`${btnSm} shrink-0`}>
                  {busy === r.id ? "처리 중…" : "처리 완료"}
                </button>
              )}
            </div>

            <span className="w-fit rounded-full bg-orange-100 px-2.5 py-1 text-xs font-medium text-orange-700">
              {r.reasonLabel}
            </span>
            <button
              onClick={() => suspend(r)}
              disabled={busy === r.id}
              className={`${btnSm} w-fit ${r.reportedSuspended ? "" : "border-red-200 text-red-700 hover:bg-red-50"}`}
            >
              {r.reportedSuspended ? "정지 해제" : "계정 정지"}
            </button>
            {r.detail && (
              <p className="rounded-xl bg-stone-50 p-3 text-sm whitespace-pre-wrap text-stone-700">{r.detail}</p>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Admin;
