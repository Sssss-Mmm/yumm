import { Link, useLocation, useNavigate } from "react-router-dom";
import { getToken, logout } from "../api";

const Navbar = () => {
  const navigate = useNavigate();
  useLocation(); // 페이지 이동 때마다 로그인 여부를 다시 읽는다

  const signOut = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <nav className="bg-blue-600 text-white p-4 flex justify-between items-center">
      <div className="text-xl font-bold">
        <Link to="/">YUMM</Link>
      </div>
      <div className="flex gap-6">
        <Link to="/match" className="hover:underline">밥메이트</Link>
      </div>
      <div>
        {getToken()
          ? <button onClick={signOut} className="hover:underline">로그아웃</button>
          : <Link to="/login" className="hover:underline">로그인</Link>}
      </div>
    </nav>
  );
};

export default Navbar;
