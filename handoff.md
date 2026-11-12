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
