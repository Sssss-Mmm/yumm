import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { signup } from "../api";

// ponytail: 서버가 "올해 - birthYear >= 20"으로 판정하므로 max도 같은 식
const maxBirthYear = new Date().getFullYear() - 20;

const Registration = () => {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [agreed, setAgreed] = useState(false);

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      await signup({
        email: String(form.get("email")),
        password: String(form.get("password")),
        nickname: String(form.get("nickname")),
        gender: String(form.get("gender")),
        birthYear: Number(form.get("birthYear")),
        agreedToTerms: agreed,
      });
      navigate("/login");
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <form onSubmit={submit} className="max-w-sm mx-auto flex flex-col gap-3">
      <h1 className="text-2xl font-bold">회원가입</h1>
      <input name="email" type="email" required placeholder="이메일" className="border rounded p-2" />
      <input name="password" type="password" required minLength={8} placeholder="비밀번호 (8자 이상)" className="border rounded p-2" />
      <input name="nickname" required placeholder="닉네임" className="border rounded p-2" />
      <select name="gender" required className="border rounded p-2">
        <option value="MALE">남성</option>
        <option value="FEMALE">여성</option>
      </select>
      <label className="flex flex-col gap-1">출생연도 (4자리)
        <input name="birthYear" type="number" required min={maxBirthYear - 80} max={maxBirthYear} placeholder="예: 1998" className="border rounded p-2" />
      </label>
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-1" />
        <span>[필수] 이용약관 및 개인정보 처리방침에 동의합니다.</span>
      </label>
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button disabled={!agreed} className="bg-blue-600 text-white rounded p-2 disabled:opacity-50">가입하기</button>
    </form>
  );
};

export default Registration;
