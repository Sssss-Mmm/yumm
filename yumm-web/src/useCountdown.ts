import { useEffect, useRef, useState } from "react";

/**
 * 남은 초를 세는 카운트다운. `start(초)`로 (다시) 시작하고 0에서 멈춘다.
 * 서버가 429로 남은 초를 주면 그 값으로 다시 start 해서 덮어쓴다 — 서버 값이 진실이다.
 *
 * ponytail: 마감 시각(Date.now 기준)만 들고 0.5초마다 다시 계산한다. 탭이 백그라운드로
 * 내려가 타이머가 밀려도 값이 어긋나지 않는다. 화면을 떠나면 effect 정리로 타이머가 죽는다.
 */
export function useCountdown(): [number, (seconds: number) => void] {
  const [left, setLeft] = useState(0);
  const deadline = useRef(0);
  const running = left > 0;

  useEffect(() => {
    if (!running) return;
    const id = setInterval(
      () => setLeft(Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000))),
      500,
    );
    return () => clearInterval(id);
  }, [running]);

  const start = (seconds: number) => {
    const s = Math.max(0, Math.ceil(seconds));
    deadline.current = Date.now() + s * 1000;
    setLeft(s);
  };

  return [left, start];
}
