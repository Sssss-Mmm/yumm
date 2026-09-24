import type { ReactNode } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import Home from "../pages/Home";
import Login from "../pages/Login";
import Registration from "../pages/Registration";
import Match from "../pages/Match";
import Chat from "../pages/Chat";
import Admin from "../pages/Admin";
import Terms from "../pages/Terms";
import Privacy from "../pages/Privacy";
import { getToken, isAdmin } from "../api";

const loggedIn = () => getToken() !== null;

/**
 * 로그인 여부를 element 자리에서 바로 계산하면 AppRouter가 처음 그릴 때의 값이 그대로 굳는다.
 * navigate()는 Routes만 다시 그리고 AppRouter는 다시 그리지 않으므로, 로그인 직후 /match로 가도
 * "토큰 없음" 시절에 만들어진 <Navigate to="/login">이 렌더되어 로그인 화면으로 되돌아온다.
 * 컴포넌트 안에서 읽으면 렌더 시점의 localStorage를 본다. Navbar가 useLocation을 쓰는 것과 같은 이유다.
 */
const Guard = ({ allow, to, children }: { allow: () => boolean; to: string; children: ReactNode }) =>
  allow() ? children : <Navigate to={to} replace />;

const AppRouter = () => (
  <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/login" element={<Login />} />
    <Route path="/registration" element={<Registration />} />
    {/* 가입 전에 읽는 문서다. 로그인 가드를 걸지 않는다. */}
    <Route path="/terms" element={<Terms />} />
    <Route path="/privacy" element={<Privacy />} />
    <Route path="/match" element={<Guard allow={loggedIn} to="/login"><Match /></Guard>} />
    <Route path="/chat/:groupId" element={<Guard allow={loggedIn} to="/login"><Chat /></Guard>} />
    {/* 화면 감추기일 뿐이다. 실제 차단은 서버가 /api/admin/**에 ROLE_ADMIN을 요구하는 것으로 한다. */}
    <Route path="/admin" element={<Guard allow={isAdmin} to="/"><Admin /></Guard>} />
  </Routes>
);

export default AppRouter;
