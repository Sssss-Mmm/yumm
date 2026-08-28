# 1. 베이스 이미지
FROM python:3.9-slim-buster

# 2. 시스템 의존성 설치 (FAISS 빌드)
# build-essential : C / C++ 컴파일러 등 빌드 도구
# libblas-dev, liblapack, libfaiss-dev : FAISS의 선형대수 라이브러리 및 FAISS 자체 라이브러리
# pkg-config : 라이브러리 검색 도구
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    build-essential \
    libblas-dev \
    liblapack-dev

# 3. apt 캐시 정리 (이전 RUN 명령에서 다운로드된 패키지 목록 삭제해서 이미지 크기 축소)
RUN rm -rf /var/lib/apt/lists/*

# 3. 작업 디렉토리 설정
WORKDIR /app

# 4. Python 의존성 파일 복사 및 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 5. 애플리케이션 코드 복사
COPY . .

# 6. FastAPI 애플리케이션 포트
EXPOSE 8000

# 7. 컨테이너 시작 명령
# Uvicorn을 사용하여 FastAPI 애플리케이션 실행
# 'main.app'은 main.py 파일에 'app = FastAPI()'와 같이 정의된 것
# --host 0.0.0.0 은 컨테이너 외부에서 접근 가능하도록 함
# --port 8000은 노출된 포트와 일치 필요
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

# 추가적으로 필요한 경우 (예: 모델 파일 복사, 환경 변수 설정)
# COPY models/ /app/models/
# ENV MY_VARIABLE="some_value"