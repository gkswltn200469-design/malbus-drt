# handoff

## BE 강대운

### 2026-11-10

- API 명세서 초안 작성: `docs/api.md`
- ERD 초안 작성: `docs/erd.md`
- 미결 결정 반영: 변경·취소·문의는 확정 안 함, 복수 예약 미분리(인원 칸 유지), 별칭은 사람이 승인
- 기술 스택 반영: Spring Boot + JPA + SQLite, 포트 8080

### 2026-11-11

- 백엔드 초기 세팅(#3): `backend/` Spring Boot 3.5 + JPA + SQLite, 시작 시 `data/malbus.db` 생성·정류장 CSV 적재, FK 켜짐 테스트, `GET /health`, `GET /stops`, 공통 에러 형식
- 예약 API(#5): 생성·목록·상세, 칸 수정(`PATCH`) 검증, 확정 검증(`CONFIRM_BLOCKED`, `PURPOSE_NOT_SUPPORTED`, `ALREADY_CONFIRMED`), 확정 시 SYSTEM 줄 기록
- 규칙 기반 추출(#7): `Extractor` 인터페이스(AI 모듈 출력 형식) + `RuleExtractor`, 날짜·시간·인원·정류장 판정, `POST /reservations/{id}/lines`, `POST /extract`. LLM 구현은 `ExtractorConfig`에서 바꿔 끼우면 됨(곽도윤)
- 별칭 사전 API(#9): `POST/GET /aliases`, 승인·거절(충돌 시 `warning`), `conflicts`·`recommended` 계산, PATCH 응답 `aliasSuggestion`, 매칭에 배운 별칭 반영(PENDING은 후보 순서만, APPROVED부터 확인 완료)
- 대본 채점(#11): `./gradlew grade` → `test/채점결과_RULE.md`. RULE 모드 전체 칸 109/140(78%). 목적·출발·날짜·시간 20/20, 도착 19/20, 인원 7/20, 확인필요 3/20. 인원·확인필요가 낮은 건 정답지가 말 안 한 인원을 1로 적고 변경·취소·문의의 확인필요를 비워 둔 것과 api.md 규칙(1 기본값 금지, 빈칸도 확인 필요)이 달라서. 정답지 기준을 지수와 맞춰야 함
- 인원 기본값 1명(#13): 예약인데 인원 말 안 하면 `value = 1`, `said = null`, `source = RULE`. 변경·취소·문의는 null. 채점 전체 칸 135/140(96%), 인원 20/20, 확인필요 16/20
- 채점 기준 변경(#11): 정답지 새 형식(목적·인원 열 없음, master `641d321`)에 맞춰 출발·도착·날짜·시간·확인필요 5칸만 채점, 열 이름으로 읽어서 옛 형식도 됨. 새 정답지 기준 RULE 97/100 (18번 도착, 19번 도착, 18번 확인필요 틀림)
- 인원 화면·채점 제외(#13): 인원은 추출 안 하고 항상 `value = 1`, `needsCheck = false`, `source = RULE`, `said = null`로 저장(나중 인원 기능용으로 칸만 남김). PATCH `people`은 400, 확정 조건·`CONFIRM_BLOCKED`에서 뺌. 새 정답지 기준 RULE 97/100

### 2026-11-15

- 프론트 API 연결(#17): #16 상담원 화면을 백엔드에 붙임. 대본·한 줄 입력은 `POST /reservations` + `/lines`, 후보 선택·날짜·시간 수정은 `PATCH`, 확정은 `POST /confirm`, 정류장은 `GET /stops`. 확정 화면 JSON은 서버 응답 그대로(SQLite 저장)
- 별칭 추가 모달: PATCH 응답에 `aliasSuggestion` 오면 띄우고 "추가"면 `POST /aliases`(승인 대기)
- 서버 에러는 화면 위 빨간 줄로 표시. 인원 카드 삭제, 목적 고정
- 화면 쪽 추출 로직(`extract.ts`, `resolveFields.ts` 등) 삭제. 추출 규칙은 백엔드에만 있음
- 실행: `cd backend && ./gradlew bootRun`(8080) + `cd frontend && pnpm dev`(5173). 주소 바꾸려면 `VITE_API_URL`
