FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 빌드 결과는 정적 파일이다. nginx가 그걸 서빙하면서 /api·/ws만 서버로 넘긴다
# — 개발 때 Vite 프록시가 하던 역할 그대로라 프론트 코드는 손댈 게 없다(경로가 전부 상대).
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
