import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { signup } from "../api";
import { btn, card, h1, input, label } from "../ui";

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
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h1 className={h1}>회원가입</h1>
        <p className="text-sm text-stone-500">만 19세 이상만 가입할 수 있습니다.</p>
      </div>
      <form onSubmit={submit} className={`${card} flex flex-col gap-3`}>
        <input name="email" type="email" required placeholder="이메일" className={input} />
        <input name="password" type="password" required minLength={8} placeholder="비밀번호 (8자 이상)" className={input} />
        <input name="nickname" required placeholder="닉네임" className={input} />
        <select name="gender" required className={input}>
          <option value="MALE">남성</option>
          <option value="FEMALE">여성</option>
        </select>
        <label className="flex flex-col gap-1.5">
          <span className={label}>출생연도 (4자리)</span>
          <input name="birthYear" type="number" required min={maxBirthYear - 80} max={maxBirthYear} placeholder="예: 1998" className={input} />
        </label>

        <label className="flex items-start gap-2.5 rounded-xl bg-stone-50 p-3 text-sm text-stone-700">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="mt-0.5 h-4 w-4 accent-orange-600"
          />
          <span>
            <span className="font-medium text-orange-700">[필수]</span>{" "}
            이용약관 및 개인정보 처리방침에 동의합니다.
          </span>
        </label>

        {error && <p className="text-sm text-red-600">{error}</p>}
        <button disabled={!agreed} className={btn}>가입하기</button>
      </form>
    </div>
  );
};

export default Registration;
