import { Link } from "react-router-dom";
import { getToken } from "../api";

const Home = () => (
  <div className="max-w-sm mx-auto flex flex-col gap-4 text-center">
    <h1 className="text-2xl font-bold">혼밥 대신, 밥메이트</h1>
    <p className="text-gray-600">지역·시간대·음식 취향이 맞는 3~4명을 묶어드립니다.</p>
    <Link to={getToken() ? "/match" : "/login"} className="bg-blue-600 text-white rounded p-2">
      밥메이트 찾기
    </Link>
  </div>
);

export default Home;
