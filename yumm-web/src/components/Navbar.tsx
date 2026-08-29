import { Link, useLocation, useNavigate } from "react-router-dom";
import { getToken, logout } from "../api";

const Navbar = () => {
  const navigate = useNavigate();
  const { pathname } = useLocation(); // 페이지 이동 때마다 로그인 여부를 다시 읽는다

  const signOut = async () => {
    await logout();
    navigate("/login");
  };

  const link = (active: boolean) =>
    `text-sm transition ${active ? "font-semibold text-stone-900" : "text-stone-500 hover:text-stone-900"}`;

  return (
    <nav className="sticky top-0 z-10 border-b border-stone-200 bg-white/80 backdrop-blur">
      <div className="mx-auto flex w-full max-w-md items-center justify-between px-5 py-3.5">
        <Link to="/" className="flex items-center gap-1.5 text-lg font-bold tracking-tight">
          <span className="h-2 w-2 rounded-full bg-orange-500" />
          yumm
        </Link>
        <div className="flex items-center gap-5">
          <Link to="/match" className={link(pathname.startsWith("/match"))}>밥메이트</Link>
          {getToken()
            ? <button onClick={signOut} className={link(false)}>로그아웃</button>
            : <Link to="/login" className={link(pathname === "/login")}>로그인</Link>}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
