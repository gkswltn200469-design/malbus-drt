# handoff

## BE 강대운

### 2026-11-10

- API 명세서 초안 작성: `docs/api.md`
- ERD 초안 작성: `docs/erd.md`
- 미결 결정 반영: 변경·취소·문의는 확정 안 함, 복수 예약 미분리(인원 칸 유지), 별칭은 사람이 승인
- 기술 스택 반영: Spring Boot + JPA + SQLite, 포트 8080

### 2026-11-11

- 백엔드 초기 세팅(#3): `backend/` Spring Boot 3.5 + JPA + SQLite, 시작 시 `data/malbus.db` 생성·정류장 CSV 적재, FK 켜짐 테스트, `GET /health`, `GET /stops`, 공통 에러 형식
