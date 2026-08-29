import AppRouter from "./router";
import Navbar from "./components/Navbar";

function App() {
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900 antialiased">
      <Navbar />
      <main className="mx-auto w-full max-w-md px-5 py-8">
        <AppRouter />
      </main>
    </div>
  );
}

export default App;
