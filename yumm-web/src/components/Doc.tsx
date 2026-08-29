import type { ReactNode } from "react";

// 약관·개인정보처리방침처럼 서버 호출이 없는 긴 본문 문서용 조각들.
// ponytail: 마크다운 렌더러를 새로 깔지 않는다. 문서가 둘뿐이라 본문은 JSX로 직접 옮긴다.
// 문서가 늘거나 자주 바뀌면 그때 렌더러를 검토한다.

/** 본문 안의 강조. */
export const B = ({ children }: { children: ReactNode }) => (
  <strong className="font-semibold text-stone-900">{children}</strong>
);

/** 문단. break-keep으로 좁은 화면에서 한국어 단어가 쪼개지지 않게 한다. */
export const P = ({ children }: { children: ReactNode }) => (
  <p className="break-keep text-sm leading-relaxed text-stone-700">{children}</p>
);

/** 번호 목록. 항목은 <li>로 직접 쓴다. */
export const OL = ({ children }: { children: ReactNode }) => (
  <ol className="list-decimal space-y-1.5 break-keep pl-5 text-sm leading-relaxed text-stone-700 marker:text-stone-400">
    {children}
  </ol>
);

/** 원문의 인용 블록(> ...). */
export const Note = ({ children }: { children: ReactNode }) => (
  <div className="break-keep rounded-r-xl border-l-4 border-orange-200 bg-orange-50/60 px-3 py-2.5 text-sm leading-relaxed text-stone-600">
    {children}
  </div>
);

/** 조·절 단위. */
export const Section = ({ title, children }: { title: string; children: ReactNode }) => (
  <section className="flex flex-col gap-2">
    <h2 className="break-keep text-base font-semibold text-stone-900">{title}</h2>
    {children}
  </section>
);

/** 절 안의 소제목. */
export const H3 = ({ children }: { children: ReactNode }) => (
  <h3 className="break-keep pt-1 text-sm font-semibold text-stone-800">{children}</h3>
);

/**
 * 표. 360px에서 열을 나란히 두면 읽을 수 없어 행 단위로 쌓는다.
 * 첫 칸은 제목, 나머지 칸은 헤더 이름을 배지로 달아 값을 보여준다.
 */
export const DocTable = ({ head, rows }: { head: string[]; rows: ReactNode[][] }) => (
  <dl className="overflow-hidden rounded-xl border border-stone-200 bg-white text-sm">
    <div className="border-b border-stone-200 bg-stone-50 px-3 py-1.5 text-xs text-stone-500">
      {head.join(" · ")}
    </div>
    {rows.map((row, i) => (
      <div key={i} className="flex flex-col gap-1 border-b border-stone-100 px-3 py-2.5 last:border-b-0">
        <dt className="break-keep font-medium text-stone-900">{row[0]}</dt>
        {row.slice(1).map((cell, j) => (
          <dd key={j} className="break-keep leading-relaxed text-stone-600">
            {row.length > 2 && (
              <span className="mr-1.5 rounded bg-stone-100 px-1.5 py-0.5 text-xs text-stone-500">
                {head[j + 1]}
              </span>
            )}
            {cell}
          </dd>
        ))}
      </div>
    ))}
  </dl>
);
