import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { ApiError, api, setTokens } from "../api";
import { btn, card, h1, input } from "../ui";
import { useCountdown } from "../useCountdown";

// 남은 잠금 시간. 초만 0을 채워 자릿수를 고정한다(9:07).
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

const Login = () => {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  // 로그인 5회 실패 시 서버가 429 + 남은 초를 준다. 그 값이 없으면 남은 시간을 표시하지 않는다.
  const [lockedFor, startLock] = useCountdown();

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      const data = await api<{ accessToken: string; refreshToken: string }>("/auth/login", "POST", {
        email: form.get("email"),
        password: form.get("password"),
      });
      setTokens(data.accessToken, data.refreshToken);
      navigate("/match");
    } catch (err) {
      // 재시도가 잠금 카운터를 다시 올리므로, 남은 시간 동안은 화면에서 먼저 막는다.
      if (err instanceof ApiError && err.retryAfterSeconds !== undefined) {
        startLock(err.retryAfterSeconds);
      }
      setError((err as Error).message);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <h1 className={h1}>로그인</h1>
      <form onSubmit={submit} className={`${card} flex flex-col gap-3`}>
        <input name="email" type="email" required placeholder="이메일" className={input} />
        <input name="password" type="password" required placeholder="비밀번호" className={input} />
        {error && <p className="text-sm text-red-600">{error}</p>}
        {lockedFor > 0 && (
          <p className="text-sm text-red-600 tabular-nums">다시 시도할 수 있을 때까지 {mmss(lockedFor)}</p>
        )}
        <button disabled={lockedFor > 0} className={btn}>
          {lockedFor > 0 ? "잠시 후 다시 시도해 주세요" : "로그인"}
        </button>
      </form>
      <p className="text-center text-sm text-stone-500">
        처음이신가요?{" "}
        <Link to="/registration" className="font-medium text-orange-600 hover:underline">회원가입</Link>
      </p>
    </div>
  );
};

export default Login;
