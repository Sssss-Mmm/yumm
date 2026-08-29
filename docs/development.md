# 개발 문서

로컬 실행, 환경 설정, 배포 전 수동 마이그레이션. 프로젝트 소개는 [`README.md`](../README.md).

## 실행

PostgreSQL과 Redis가 필요하다.

**Java 17이 필요하다.** 더 높은 JDK가 기본이면 `JAVA_HOME`을 17로 지정해야 빌드된다.

```bash
# 서버 (8080) — JWT_SECRET 없으면 기동 실패한다. 아래 「환경변수」 참조
cd yumm-server && JWT_SECRET=$(openssl rand -base64 32) ./mvnw spring-boot:run

# 웹 (5173)
cd yumm-web && npm install && npm run dev
```

웹 개발 서버가 `/api`와 `/ws`를 8080으로 프록시한다. 다만 프록시는 `Origin` 헤더를 그대로
넘기므로 서버 입장에서는 여전히 교차 출처 요청이다 — `SecurityConfig`가 루프백 주소를
허용 목록에 두는 이유다.

`localhost` · `127.0.0.1` · `[::1]`은 같은 곳을 가리키지만 `Origin` **문자열**은 서로 달라서
CORS 매칭은 셋을 다른 출처로 본다. 그래서 셋 다 등록해뒀다. **브라우저를 LAN IP로 열면
(`http://172.x.x.x:5173`) 여전히 403이 난다** — `localhost:5173`으로 접속하거나 그 IP를
`SecurityConfig.corsConfigurationSource()`의 패턴 목록에 추가한다.

### 환경변수

**`JWT_SECRET`은 기본값이 없다. 설정하지 않으면 서버가 기동하지 않는다**
(`PlaceholderResolutionException: Could not resolve placeholder 'JWT_SECRET'`).
토큰 서명 키에 기본값을 두면 그 값이 그대로 운영까지 따라가므로 일부러 비워뒀다. 직접 만들어 쓴다.

```bash
export JWT_SECRET=$(openssl rand -base64 32)   # HS256, 256비트
```

나머지는 기본값이 있어서 로컬에서는 그대로 두면 된다.

| 변수 | 기본값 |
|---|---|
| `JWT_SECRET` | **없음 — 필수** |
| `DB_URL` | `jdbc:postgresql://localhost:5432/demo_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `demo_user` / `demo_pass` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (없음) |
| `SMTP_HOST` | (비어 있음 — 비면 알림 메일을 보내지 않는다) |
| `SMTP_PORT` | `587` (STARTTLS) |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | (비어 있음) |
| `SMTP_FROM` | `no-reply@yumm.local` — 보통 `SMTP_USERNAME`과 같은 주소로 둔다 |

**SMTP는 알림 메일(FR-N-01·02·04)용이다.** `SMTP_HOST`가 비어 있으면 `EmailServiceImpl`이 발송을
건너뛰므로 로컬에서는 설정하지 않아도 매칭이 정상 동작한다. 발송이 실패해도 예외를 삼키고
로그만 남긴다 — 알림은 부가 기능이라 매칭 편성 트랜잭션을 되돌리면 안 된다.

**자격증명은 OS 환경변수로만 준다. 저장소에는 값을 넣지 않는다.** 키 목록은 `.env.example`에 있고,
`.env`는 `.gitignore` 대상이다. 앱은 `.env`를 읽지 않으므로 셸에서 export 해야 한다.

```bash
set -a; source .env; set +a   # .env의 키를 환경변수로 올린다
cd yumm-server && ./mvnw spring-boot:run
```

API 문서는 서버를 띄운 뒤 `/swagger-ui.html`.

### 빌드와 테스트

```bash
cd yumm-server && ./mvnw test    # 테스트
cd yumm-web && npm run build     # tsc -b + vite build
```

`DemoApplicationTests.contextLoads`는 실행 중인 PostgreSQL이 필요해서 기본으로는 스킵된다.
스프링 컨텍스트가 실제로 부팅되는지 보는 유일한 테스트라 **배포 전에 한 번은 돌린다.**

```bash
DB_URL=jdbc:postgresql://localhost:5432/demo_db JWT_SECRET=$(openssl rand -base64 32) ./mvnw test
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

**2) `match_requests` — `allow_pair` 컬럼 추가**

2인 허용 옵트인(FR-M-12). `ddl-auto=update`는 컬럼을 추가하지만 `NOT NULL` 컬럼을 기존 행에
채워주지 못해 부팅이나 INSERT가 깨진다. 기본값을 먼저 주고 제약을 건다.

```sql
BEGIN;
ALTER TABLE match_requests ADD COLUMN IF NOT EXISTS allow_pair boolean;
UPDATE match_requests SET allow_pair = false WHERE allow_pair IS NULL;
ALTER TABLE match_requests ALTER COLUMN allow_pair SET NOT NULL;
ALTER TABLE match_requests ALTER COLUMN allow_pair SET DEFAULT false;
COMMIT;
```

기존 신청을 전부 `false`로 두는 게 맞다. 고른 적 없는 사람을 2인 대상에 넣지 않는다.

**3) `match_requests` — enum 밖 `region` 문자열이 남으면 편성 스케줄러가 정지한다**

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

**4) `users` — `withdrawn_at` 컬럼 추가 (탈퇴 익명화)**

회원 탈퇴를 하드 삭제에서 익명화로 바꿨다(FR-A-08). 신고·차단·채팅·매칭 신청이 `users` 행을
NOT NULL FK로 물고 있어 삭제가 FK 위반으로 실패했고, 성공했다면 보존하기로 한 신고 이력까지
사라진다(`docs/privacy-policy.md` 5절). nullable 컬럼이라 `ddl-auto=update`가 알아서 추가하지만,
배포 전에 확인해 두면 첫 탈퇴 요청에서 놀랄 일이 없다.

```sql
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS withdrawn_at timestamp;
COMMIT;

-- 확인. 기존 행은 전부 NULL(= 정상 계정)이어야 한다.
SELECT count(*) FROM users WHERE withdrawn_at IS NOT NULL;
```

`NULL`이 정상 계정이고 값이 있으면 탈퇴 계정이다. 백필할 값이 없다 — 이전 버전에서 탈퇴한
계정은 이미 행째로 사라졌으므로 되살릴 수 없다.

탈퇴 계정은 이메일이 `withdrawn-{id}@yumm.invalid`로 덮이고 닉네임·비밀번호·프로필 이미지가
지워진다. 원래 이메일은 비므로 같은 주소로 다시 가입할 수 있다.

```sql
-- 운영 중 확인용: 탈퇴 계정 목록
SELECT id, email, nickname, withdrawn_at FROM users WHERE withdrawn_at IS NOT NULL;
```

**5) `match_requests` — `reminded_at` 컬럼 추가 (식사 당일 아침 리마인드)**

FR-N-04. 하루 한 통을 넘기지 않기 위한 유일한 수단이라 이 컬럼이 없으면 10분마다 같은 사람에게
리마인드가 다시 나간다. nullable이라 `ddl-auto=update`가 알아서 추가하지만, 배포 전에 확인해 두면
첫 아침 주기에서 놀랄 일이 없다.

```sql
BEGIN;
ALTER TABLE match_requests ADD COLUMN IF NOT EXISTS reminded_at timestamp;
COMMIT;

-- 확인. 기존 행은 전부 NULL(= 아직 안 보냄)이어야 한다.
SELECT count(*) FROM match_requests WHERE reminded_at IS NOT NULL;
```

`NULL`이면 아직 안 보낸 것이고 값이 있으면 보낸 시각이다. **백필하지 않는다** — 다만 배포일 당일
식사 예정인 기존 `MATCHED` 행은 전부 NULL이라 배포 직후 첫 주기(08:00 이후)에 리마인드를 한 통 받는다.
그게 싫으면 배포 전에 그 행만 막아둔다.

```sql
UPDATE match_requests SET reminded_at = now()
 WHERE status = 'MATCHED' AND meal_date = CURRENT_DATE;
```
