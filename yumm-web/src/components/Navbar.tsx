import { Link } from "react-router-dom";

const Navbar = () => {
  return (
    <nav className="bg-blue-600 text-white p-4 flex justify-between items-center">
      <div className="text-xl font-bold">
        <Link to="/">YUMM</Link>
      </div>
      <div className="flex gap-6">
        <Link to="#" className="hover:underline">
          About Us
        </Link>
        <Link to="#" className="hover:underline">
          Contact Us
        </Link>
      </div>
      <div>
        <Link to="/login" className="hover:underline">
          로그인
        </Link>
      </div>
    </nav>
  );
};

export default Navbar;
