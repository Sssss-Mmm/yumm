import { Link, useLocation } from "react-router-dom";
import AppRouter from "./router";
import Navbar from "./components/Navbar";
import { pageWidth } from "./ui";

function App() {
  const { pathname } = useLocation();
  return (
    <div className="flex min-h-screen flex-col bg-stone-50 text-stone-900 antialiased">
      <Navbar />
      <main className={`mx-auto w-full flex-1 px-5 py-8 ${pageWidth(pathname)}`}>
        <AppRouter />
      </main>
      <footer className="border-t border-stone-200">
        <div className={`mx-auto flex w-full items-center justify-between gap-4 px-5 py-5 text-xs text-stone-500 ${pageWidth(pathname)}`}>
          <span>yumm</span>
          <span className="flex gap-4">
            <Link to="/terms" className="hover:text-stone-800">이용약관</Link>
            <Link to="/privacy" className="font-medium hover:text-stone-800">개인정보 처리방침</Link>
          </span>
        </div>
      </footer>
    </div>
  );
}

export default App;
