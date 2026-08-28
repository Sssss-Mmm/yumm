import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

const Registration = () => {
  const navigate = useNavigate();
  const [error, setError] = useState("");

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      await api("/user/signup", "POST", {
        email: form.get("email"),
        password: form.get("password"),
        nickname: form.get("nickname"),
        gender: form.get("gender"),
        age: Number(form.get("age")),
        phoneNumber: form.get("phoneNumber"),
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
      <input name="age" type="number" required min={1} max={120} placeholder="나이" className="border rounded p-2" />
      <input name="phoneNumber" required placeholder="전화번호" className="border rounded p-2" />
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button className="bg-blue-600 text-white rounded p-2">가입하기</button>
    </form>
  );
};

export default Registration;
