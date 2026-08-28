import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // ponytail: 프록시로 붙어서 CORS 자체를 없앤다. 서버 CORS 설정 손댈 필요 없음.
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
})
