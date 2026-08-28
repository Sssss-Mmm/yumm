# yumm

혼밥 대신 밥메이트. 조건이 맞는 **3~4인**을 시스템이 자동으로 묶어 같이 밥 먹게 한다.

3~4인인 이유는 그게 밥자리의 자연스러운 크기여서다. 대화가 한 사람에게 몰리지 않고, 한 명이 못 나와도 밥은 먹는다.
이 인원 제약이 설계 전체를 결정한다. 경쟁 상대는 데이팅 앱이 아니라 **오늘 혼자 먹게 될 그 한 끼**다.

## 구성

| 디렉터리 | 스택 | 비고 |
|---|---|---|
| `yumm-server` | Spring Boot 3.4.5 / Java 17 / Maven | JPA + PostgreSQL, Redis, Spring Security + JJWT, STOMP WebSocket, springdoc |
| `yumm-web` | React 19 / TypeScript / Vite / Tailwind 4 | react-router-dom 7 |
| `yumm-inference` | Python (FastAPI + FAISS 스켈레톤) | **어디에서도 호출되지 않는다.** 삭제 대상 — 아래 참조 |
| `docs/` | — | 기획의 단일 출처 |

## 실행

PostgreSQL과 Redis가 필요하다.

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

## 동작 방식

- **버킷** = 지역 + 날짜 + 시간대. 이 셋이 완전히 같은 사람끼리만 묶인다. 성별 조건은 상대에 따라 결과가 달라져 버킷 키가 될 수 없고 합류 시점에 쌍방 검사한다.
- **편성**은 30초 주기 스케줄러가 전담한다. 신청 API는 저장만 하므로 편성이 실패해도 신청이 롤백되지 않는다.
- 오래 기다린 사람을 씨앗으로 삼아 취향이 가장 많이 겹치는 사람을 붙이는 **그리디**다. 전역 최적해보다 "먼저 온 사람이 먼저"라는 공정성을 택했다.
- 신청은 **30분 뒤 만료**된다. 만료는 저장된 상태가 아니라 조회 시 시각 비교로 계산한다. 만료 배치가 없다.
- 매칭되면 **채팅방이 곧 그룹**이다(방 ID = groupId). 별도 확정 버튼은 없고 **채팅방 입장이 곧 참석 의사**다.
- 이탈해서 인원이 3명 밑으로 떨어지면 그룹을 해체하고 남은 사람을 대기열로 되돌린다.

왜 이렇게 했는지는 `docs/matching-design.md`에 있다.

## 현재 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| M0 | 인증/회원, 편성 로직, 그룹 채팅, 매칭 화면 | 완료 |
| M1 | 편성 스케줄러, 지역 enum, 서버 날짜 검증, 채팅 저장·조회 + 웹 채팅 화면 | 완료 |
| M2 | 매칭 후 이탈, 최소 인원 미달 시 그룹 해체 | 완료 |
| M3 | 매칭/해체 알림, 식사 당일 리마인드, 스케줄러 분리 | 진행 중 — 스케줄러 분리만 완료 |
| M4 | 신고·차단·약관·안전수칙·성인 인증, 2인 폴백 | 예정 |
| M5 | 1개 지역 클로즈드 베타 | 예정 |

**M4 이전에는 외부 공개 출시하지 않는다.** 낯선 사람과 대면하는 서비스인데 신고·차단 창구가 아직 없다.

## 알아둘 것

- **Java 17이 필요하다.** 더 높은 JDK가 기본이면 `JAVA_HOME`을 17로 지정해야 빌드된다.
- `ddl-auto=update`를 쓴다. 컬럼을 지우거나 제약을 풀지 않으므로, 엔티티에서 필드를 빼면 DB에 남은 `NOT NULL` 컬럼 때문에 INSERT가 깨진다. 그럴 땐 DDL을 손으로 실행한다. 마이그레이션 도구는 외부 공개 전까지 도입하지 않는다.
- 채팅은 **내장 심플 브로커**를 쓴다. 메시지를 서버 메모리에 들고 있어서 서버를 여러 대로 늘리면 다른 인스턴스에 붙은 사람에게 전달되지 않는다. 스케일아웃 전에 외부 브로커로 바꿔야 한다.
- 그룹을 별도 테이블 없이 `match_requests.group_id` 컬럼으로만 표현한다. 그룹 자체에 속성(만날 시각·장소)이 생기는 시점이 엔티티로 승격할 시점이다.
- `yumm-inference`는 호출부가 없다. 조건을 카테고리 선택으로 하고 자유 텍스트·임베딩을 쓰지 않기로 하면서 남은 잔재다. 취향 임베딩이 필요해지면 다시 만든다.

## 문서

| 파일 | 내용 |
|---|---|
| [`docs/product-plan.md`](docs/product-plan.md) | 기획서. 문제 정의, 타깃, MVP 범위, 마일스톤, 리스크 |
| [`docs/requirements.md`](docs/requirements.md) | 요구사항정의서. 기능별 ID·우선순위·구현 상태 |
| [`docs/matching-design.md`](docs/matching-design.md) | 매칭 설계 근거. 왜 이 알고리즘인지 |

코드와 `docs/`가 어긋나면 임의로 맞추지 말고 먼저 보고한다. 기획의 단일 출처는 `docs/`다.
