import AppRouter from "./router";
import Navbar from "./components/Navbar";

function App() {
  return (
    <div>
      <Navbar />
      <main className="p-4">
        <AppRouter />
      </main>
    </div>
  );
}

export default App;

