import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { api, setTokens } from "../api";
import { btn, card, h1, input } from "../ui";

const Login = () => {
  const navigate = useNavigate();
  const [error, setError] = useState("");

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
        <button className={btn}>로그인</button>
      </form>
      <p className="text-center text-sm text-stone-500">
        처음이신가요?{" "}
        <Link to="/registration" className="font-medium text-orange-600 hover:underline">회원가입</Link>
      </p>
    </div>
  );
};

export default Login;
