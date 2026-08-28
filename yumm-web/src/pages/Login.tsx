import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { api, setTokens } from "../api";

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
    <form onSubmit={submit} className="max-w-sm mx-auto flex flex-col gap-3">
      <h1 className="text-2xl font-bold">로그인</h1>
      <input name="email" type="email" required placeholder="이메일" className="border rounded p-2" />
      <input name="password" type="password" required placeholder="비밀번호" className="border rounded p-2" />
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button className="bg-blue-600 text-white rounded p-2">로그인</button>
      <Link to="/registration" className="text-sm text-blue-600 hover:underline">회원가입</Link>
    </form>
  );
};

export default Login;
