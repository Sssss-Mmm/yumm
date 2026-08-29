// 화면 전체가 같은 톤을 쓰도록 클래스 문자열만 모아둔다.
// ponytail: 컴포넌트가 아니라 상수다. 래퍼를 만들면 props 설계가 따라붙는데
// 지금 필요한 건 "여섯 화면에서 버튼이 따로 놀지 않는 것"뿐이다.

/** 주 동작 버튼. 화면당 하나만 쓴다. */
export const btn =
  "rounded-xl bg-orange-600 px-4 py-2.5 text-center font-medium text-white transition " +
  "hover:bg-orange-700 active:bg-orange-800 disabled:bg-stone-300 disabled:hover:bg-stone-300";

/** 보조 동작 버튼(취소·나가기 등). */
export const btnGhost =
  "rounded-xl border border-stone-300 bg-white px-4 py-2.5 text-center font-medium text-stone-700 " +
  "transition hover:bg-stone-50 disabled:opacity-50";

/** 작은 인라인 버튼(차단 등). */
export const btnSm =
  "rounded-lg border border-stone-300 bg-white px-2.5 py-1 text-sm text-stone-600 " +
  "transition hover:bg-stone-50 disabled:opacity-50";

export const input =
  "w-full rounded-xl border border-stone-300 bg-white px-3.5 py-2.5 text-stone-900 outline-none " +
  "transition placeholder:text-stone-400 focus:border-orange-500 focus:ring-2 focus:ring-orange-100 " +
  "disabled:bg-stone-100 disabled:text-stone-400";

export const card = "rounded-2xl border border-stone-200 bg-white p-5 shadow-sm";

/** 입력 위에 붙는 작은 라벨. */
export const label = "text-sm font-medium text-stone-600";

export const h1 = "text-2xl font-bold tracking-tight text-stone-900";

export const muted = "text-sm text-stone-500";
