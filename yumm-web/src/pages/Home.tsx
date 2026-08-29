import { Link } from "react-router-dom";
import { getToken } from "../api";
import { btn } from "../ui";

const Home = () => (
  <div className="flex flex-col items-center gap-6 pt-8 text-center">
    <span className="rounded-full bg-orange-100 px-3 py-1 text-xs font-medium text-orange-700">
      3~4인 자동 매칭
    </span>
    <div className="flex flex-col gap-3">
      <h1 className="text-3xl font-bold leading-snug tracking-tight">
        혼밥 대신,
        <br />
        <span className="text-orange-600">밥메이트</span>
      </h1>
      <p className="text-stone-600">
        지역·시간대·음식 취향이 맞는 3~4명을
        <br />
        시스템이 자동으로 묶어드립니다.
      </p>
    </div>
    <Link to={getToken() ? "/match" : "/login"} className={`${btn} w-full`}>
      밥메이트 찾기
    </Link>
    <p className="text-xs text-stone-400">신청하면 30분 안에 결과를 알려드려요.</p>
  </div>
);

export default Home;
