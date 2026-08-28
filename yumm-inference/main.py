from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn
import logging

# 로거 설정
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = FastAPI()

# Spring에서 받을 요청 본문의 데이터 모델 정의
# 실제 사용자의 임베딩된 매칭 조건들에 맞춰 필드를 정의해야 합니다.
class MatchingConditions(BaseModel):
    user_embedding: list[float]  # 사용자 임베딩 벡터 (예: [0.1, 0.2, ...])
    top_k: int                   # 검색할 후보 수 (예: 5)
    # 필요한 다른 매칭 조건 필드 추가 (예: location, age_range 등)
    # query_id: str = None # 요청 추적을 위한 ID (선택 사항)


@app.get("/")
async def read_root():
    return {"message": "FastAPI Inference Server is running!"}

@app.post("/predict")
async def predict_top_k_matches(conditions: MatchingConditions):
    logger.info(f"FastAPI Server: Received request for prediction.")
    logger.info(f"FastAPI Server: User Embedding: {conditions.user_embedding[:5]}... (showing first 5 elements)") # 전체 벡터 출력은 너무 길 수 있음
    logger.info(f"FastAPI Server: Top K: {conditions.top_k}")
    # logger.info(f"FastAPI Server: Full received data: {conditions.model_dump_json()}") # 전체 데이터 로깅 (Pydantic v2 이상)

    # 여기에 FAISS를 이용한 벡터 계산 및 top-k 검색 로직을 구현합니다.
    # 예시:
    # from faiss_implementation import search_faiss_index
    # results = search_faiss_index(conditions.user_embedding, conditions.top_k)
    results = [{"id": "match_1", "score": 0.95}, {"id": "match_2", "score": 0.92}] # 가상 결과

    logger.info(f"FastAPI Server: Sent top-k matches: {results}")

    return {"status": "success", "matches": results, "received_data": conditions}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)


"""
app = FastAPI()

DIM = 8 # 벡터 차원 수
vector_store = {} # {user_id : vector}
index = faiss.IndexFlat2(DIM) # L2 거리 인덱스
id_map = [] # user_id -> FAISS 인덱스 간 매핑

# ==================== 데이터 모델 ====================
class AddRequest(BaseModel):
    user_id: int
    vector: list[float]     # 사용자의 조건 벡터
    weights: list[float]    # 각 feature에 대한 가중치

class SearchRequest(BaseModel):
    query_id: int
    candidate_ids: list[int]
    weights: list[float]
    top_k: int

# ====================== 유틸 ======================
"""