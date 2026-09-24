import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { MEAL_TIMES, REGIONS, countOf, getToken, getWaitingCounts, localDate, type WaitingCount } from "../api";
import Seats from "../components/Seats";
import { btn, btnGhost } from "../ui";

const today = localDate();
const tomorrow = localDate(new Date(Date.now() + 86_400_000));

// 실제 순서가 있는 흐름이라 번호를 붙인다.
const STEPS = [
  { title: "조건 고르기", body: "지역, 오늘·내일, 점심·저녁, 먹고 싶은 음식만 고르면 됩니다." },
  { title: "30분 안에 매칭", body: "같은 조건으로 기다리는 사람 중 취향이 가장 겹치는 3~4명을 묶어드려요." },
  { title: "채팅으로 약속 잡기", body: "그룹 채팅방이 바로 열려요. 어디서 몇 시에 만날지 정하면 끝." },
  { title: "같이 먹기", body: "식사 당일 아침에 한 번 더 알려드려요." },
];

const SAFETY = [
  "만 19세 이상만 가입할 수 있어요",
  "신청 전에 이메일 인증을 거쳐요",
  "불편한 사람은 바로 신고·차단할 수 있어요",
  "이메일·나이는 다른 사람에게 보이지 않아요",
];

/** 지역별 대기 현황. 가입 전에 "여기 사람이 있다"를 보여주는 게 이 화면의 일이다(FR-A-10). */
const Board = () => {
  const [counts, setCounts] = useState<WaitingCount[] | null>(null);
  const [error, setError] = useState(false);
  const [day, setDay] = useState(today);

  useEffect(() => {
    const load = () => getWaitingCounts()
      .then((list) => { setCounts(list ?? []); setError(false); })
      .catch(() => setError(true));
    load();
    // 편성 주기(30초)보다 촘촘할 이유가 없다.
    const id = setInterval(load, 30_000);
    return () => clearInterval(id);
  }, []);

  const total = counts?.filter((c) => c.mealDate === day).reduce((sum, c) => sum + c.count, 0) ?? 0;
  const dayTab = (value: string, text: string) => (
    <button
      type="button"
      onClick={() => setDay(value)}
      aria-pressed={day === value}
      className={`rounded-lg px-3 py-1.5 text-sm transition ${day === value
        ? "bg-stone-900 font-medium text-white"
        : "text-stone-500 hover:text-stone-900"}`}
    >
      {text}
    </button>
  );

  return (
    <section aria-labelledby="board-title" className="rounded-3xl border border-stone-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="flex items-center justify-between gap-3">
        <h2 id="board-title" className="flex items-center gap-2 font-semibold">
          <span className="relative flex h-2.5 w-2.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60 motion-reduce:hidden" />
            <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
          </span>
          지금 기다리는 사람
        </h2>
        <div className="flex rounded-xl bg-stone-100 p-1">
          {dayTab(today, "오늘")}
          {dayTab(tomorrow, "내일")}
        </div>
      </div>

      <p className="mt-2 text-sm text-stone-500" aria-live="polite">
        {error ? "대기 현황을 불러오지 못했어요. 잠시 후 자동으로 다시 시도합니다."
          : counts === null ? "불러오는 중…"
          : total > 0 ? `${total}명이 밥메이트를 기다리고 있어요. 한 자리가 한 사람이에요.`
          : "아직 기다리는 사람이 없어요. 먼저 신청하면 다음 사람과 바로 묶여요."}
      </p>

      <table className="mt-4 w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-stone-400">
            <th scope="col" className="pb-2 font-normal">지역</th>
            {Object.values(MEAL_TIMES).map((t) => (
              <th key={t} scope="col" className="pb-2 text-center font-normal">{t}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {Object.entries(REGIONS).map(([region, name]) => (
            <tr key={region}>
              <th scope="row" className="py-2.5 text-left font-medium">{name}</th>
              {Object.keys(MEAL_TIMES).map((mealTime) => {
                const n = counts ? countOf(counts, region, day, mealTime) : 0;
                return (
                  <td key={mealTime} className="py-2.5">
                    <span className={`flex items-center justify-center gap-2.5 ${counts === null ? "opacity-40" : ""}`}>
                      <Seats count={n} />
                      <span className={`w-8 tabular-nums ${n > 0 ? "font-semibold text-stone-900" : "text-stone-300"}`}>
                        {n}명
                      </span>
                    </span>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
};

const Home = () => {
  const loggedIn = getToken() !== null;
  return (
    <div className="flex flex-col gap-16 pb-4 sm:gap-20 sm:pt-6">
      <section className="grid items-center gap-10 lg:grid-cols-[1fr_28rem]">
        <div className="flex flex-col gap-6">
          <h1 className="text-4xl font-bold leading-tight tracking-tight break-keep sm:text-5xl">
            오늘 점심,
            <br />
            누구랑 먹지?
          </h1>
          <p className="max-w-md text-lg leading-relaxed text-stone-600 break-keep">
            지역과 시간대만 고르면, 같은 때 같은 동네에서 밥 먹을 3~4명을 30분 안에 묶어드려요.
            약속 조율은 그룹 채팅에서.
          </p>
          <div className="flex flex-col gap-2.5 sm:flex-row">
            <Link to={loggedIn ? "/match" : "/login"} className={`${btn} px-6 py-3 text-base`}>
              밥메이트 신청하기
            </Link>
            {!loggedIn && (
              <Link to="/registration" className={`${btnGhost} px-6 py-3 text-base`}>
                회원가입
              </Link>
            )}
          </div>
        </div>
        <Board />
      </section>

      <section aria-labelledby="steps-title" className="flex flex-col gap-6">
        <h2 id="steps-title" className="text-2xl font-bold tracking-tight">이렇게 만나요</h2>
        <ol className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((s, i) => (
            <li key={s.title} className="flex flex-col gap-2 border-t-2 border-stone-900 pt-4">
              <span className="text-sm font-semibold tabular-nums text-orange-600">{i + 1}</span>
              <h3 className="font-semibold">{s.title}</h3>
              <p className="text-sm leading-relaxed text-stone-600 break-keep">{s.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="safety-title" className="grid gap-6 rounded-3xl bg-stone-900 p-6 text-stone-100 sm:p-8 lg:grid-cols-[1fr_2fr]">
        <div className="flex flex-col gap-2">
          <h2 id="safety-title" className="text-2xl font-bold tracking-tight text-white">처음 보는 사람이라서</h2>
          <p className="text-sm leading-relaxed text-stone-400 break-keep">
            낯선 사람과 마주 앉는 서비스라 안전장치를 먼저 만들었어요.
          </p>
        </div>
        <ul className="grid gap-3 sm:grid-cols-2">
          {SAFETY.map((t) => (
            <li key={t} className="flex items-start gap-2.5 text-sm leading-relaxed break-keep">
              <svg viewBox="0 0 20 20" aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 fill-emerald-400">
                <path d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0Z" />
              </svg>
              {t}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
};

export default Home;
