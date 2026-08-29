# 개발 문서

로컬 실행, 환경 설정, 배포 전 수동 마이그레이션. 프로젝트 소개는 [`README.md`](../README.md).

## 실행

PostgreSQL과 Redis가 필요하다.

**Java 17이 필요하다.** 더 높은 JDK가 기본이면 `JAVA_HOME`을 17로 지정해야 빌드된다.

```bash
# 서버 (8080)
cd yumm-server && ./mvnw spring-boot:run

# 웹 (5173)
cd yumm-web && npm install && npm run dev
```

웹 개발 서버가 `/api`와 `/ws`를 8080으로 프록시한다. 그래서 CORS 설정이 아예 없다.

접속 정보는 환경변수로 덮어쓴다. 괄호 안이 기본값이다.

| 변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/demo_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `demo_user` / `demo_pass` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (없음) |

API 문서는 서버를 띄운 뒤 `/swagger-ui.html`.

### 빌드와 테스트

```bash
cd yumm-server && ./mvnw test    # 테스트
cd yumm-web && npm run build     # tsc -b + vite build
```

`DemoApplicationTests.contextLoads`는 실행 중인 PostgreSQL이 필요해서 기본으로는 스킵된다.
스프링 컨텍스트가 실제로 부팅되는지 보는 유일한 테스트라 **배포 전에 한 번은 돌린다.**

```bash
DB_URL=jdbc:postgresql://localhost:5432/demo_db ./mvnw test
```

### 기존 DB에 배포하기 전 (수동 마이그레이션)

`ddl-auto=update`는 컬럼을 지우지도 `NOT NULL`을 풀지도 않고, 기존 컬럼 **값**도 손대지 않는다.
아래 두 블록은 **앱을 새 버전으로 띄우기 전에** psql에서 손으로 실행한다. 마이그레이션 도구는 외부 공개 전까지 도입하지 않는다.

**1) `users` — 죽은 `NOT NULL` 컬럼 때문에 신규 가입 INSERT가 전부 실패한다**

엔티티에서 `phone_number`·`age`를 뺐고 `birth_year`를 추가했다.

```sql
-- 백필 대상 확인. age가 NULL인 행이 있으면 4번이 실패하므로 먼저 값을 정한다.
SELECT count(*) FROM users WHERE age IS NULL;

BEGIN;
-- 1. 컬럼 추가 (일단 nullable)
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_year integer;

-- 2. age에서 백필. 만 나이 역산이라 생일 전인 사람은 1년 어긋날 수 있다.
UPDATE users
   SET birth_year = EXTRACT(YEAR FROM CURRENT_DATE)::int - age
 WHERE birth_year IS NULL AND age IS NOT NULL;

-- 3. 남은 NULL 확인. 0이 아니면 여기서 멈추고 값을 채운 뒤 진행한다.
SELECT count(*) FROM users WHERE birth_year IS NULL;

-- 4. NOT NULL 설정
ALTER TABLE users ALTER COLUMN birth_year SET NOT NULL;

-- 5. 죽은 컬럼 드롭
ALTER TABLE users DROP COLUMN IF EXISTS phone_number;
ALTER TABLE users DROP COLUMN IF EXISTS age;
COMMIT;
```

드롭이 불안하면 5번 대신 제약만 풀어도 INSERT는 산다. 컬럼은 남지만 앱은 쓰지 않는다.

```sql
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE users ALTER COLUMN age DROP NOT NULL;
```

백필값은 추정치다. 성인 판정(FR-A-04)은 가입 시점에만 돌아서 기존 행에는 영향이 없다.

**2) `match_requests` — enum 밖 `region` 문자열이 남으면 편성 스케줄러가 정지한다**

`region`은 `domain/Region.java`의 7개 값만 허용한다. 그 밖의 값이 있으면 조회 시 Hibernate가 매핑에 실패하고 30초 주기 편성이 통째로 멈춘다.

```sql
-- 확인
SELECT DISTINCT region FROM match_requests;

-- enum 밖 값만 추리기
SELECT id, region, status FROM match_requests
 WHERE region NOT IN ('GANGNAM','HONGDAE','SINCHON','KONDAE','JONGNO','YEOUIDO','PANGYO');
```

살릴 수 있는 값이면 매핑하고(예: `UPDATE match_requests SET region = 'GANGNAM' WHERE region = '강남';`),
아니면 지운다. `match_request_foods`가 FK로 물려 있어 자식부터 지운다.

```sql
BEGIN;
DELETE FROM match_request_foods
 WHERE match_request_id IN (
   SELECT id FROM match_requests
    WHERE region NOT IN ('GANGNAM','HONGDAE','SINCHON','KONDAE','JONGNO','YEOUIDO','PANGYO'));

DELETE FROM match_requests
 WHERE region NOT IN ('GANGNAM','HONGDAE','SINCHON','KONDAE','JONGNO','YEOUIDO','PANGYO');
COMMIT;
```

