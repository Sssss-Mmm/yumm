import { Routes, Route, Navigate } from "react-router-dom";
import Home from "../pages/Home";
import Login from "../pages/Login";
import Registration from "../pages/Registration";
import Match from "../pages/Match";
import Chat from "../pages/Chat";
import { getToken } from "../api";

const AppRouter = () => (
  <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/login" element={<Login />} />
    <Route path="/registration" element={<Registration />} />
    <Route path="/match" element={getToken() ? <Match /> : <Navigate to="/login" replace />} />
    <Route path="/chat/:groupId" element={getToken() ? <Chat /> : <Navigate to="/login" replace />} />
  </Routes>
);

export default AppRouter;
