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

const AppRouter = () => (
  <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/login" element={<Login />} />
    <Route path="/registration" element={<Registration />} />
    {/* 가입 전에 읽는 문서다. 로그인 가드를 걸지 않는다. */}
    <Route path="/terms" element={<Terms />} />
    <Route path="/privacy" element={<Privacy />} />
    <Route path="/match" element={getToken() ? <Match /> : <Navigate to="/login" replace />} />
    <Route path="/chat/:groupId" element={getToken() ? <Chat /> : <Navigate to="/login" replace />} />
    {/* 화면 감추기일 뿐이다. 실제 차단은 서버가 /api/admin/**에 ROLE_ADMIN을 요구하는 것으로 한다. */}
    <Route path="/admin" element={isAdmin() ? <Admin /> : <Navigate to="/" replace />} />
  </Routes>
);

export default AppRouter;
