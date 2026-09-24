/**
 * 4인 식탁 하나. 채워진 자리 = 지금 그 조건으로 기다리는 사람(FR-A-10, FR-M-10).
 * 그룹 최대 인원이 4라 자리도 4개다. 넘치면 호출하는 쪽이 숫자로 따로 적는다.
 */
// 왼쪽 위 → 왼쪽 아래 → 오른쪽 위 → 오른쪽 아래 순으로 앉는다. 마주 보고 앉는 모양이 되도록.
const ORDER = [0, 2, 1, 3];

const SIZES = {
  md: { seat: "h-3 w-3", table: "h-2.5 w-9", gap: "gap-3" },
  lg: { seat: "h-7 w-7", table: "h-6 w-24", gap: "gap-10" },
};

const Seats = ({ count, size = "md" }: { count: number; size?: keyof typeof SIZES }) => {
  const s = SIZES[size];
  const seat = (pos: number) => (
    <span
      className={`${s.seat} rounded-full ${ORDER.indexOf(pos) < count
        ? "bg-orange-500"
        : "border border-dashed border-stone-300 bg-white"}`}
    />
  );
  return (
    <span role="img" aria-label={`${count}명 대기 중`} className="inline-flex flex-col items-center gap-1">
      <span className={`flex ${s.gap}`}>{seat(0)}{seat(1)}</span>
      <span className={`${s.table} rounded-md bg-stone-200`} />
      <span className={`flex ${s.gap}`}>{seat(2)}{seat(3)}</span>
    </span>
  );
};

export default Seats;
