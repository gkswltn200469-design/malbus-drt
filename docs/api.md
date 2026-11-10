# API 명세서

> 작성 강대운(BE) · 2026-11-10 · 초안
> 데이터 형식의 기준 문서. 바꿀 때는 여기 먼저 고치고 공유. CLAUDE.md와 다르면 CLAUDE.md 우선.

## 공통

| 항목 | 규칙 |
|---|---|
| 기술 | Spring Boot, Spring Data JPA, SQLite (`sqlite-jdbc` + Hibernate `SQLiteDialect`) |
| Base URL | `http://localhost:8080` |
| 형식 | JSON, UTF-8 |
| 날짜 / 시간 / 시각 | `YYYY-MM-DD` / `HH:MM` (24h) / ISO 8601 `+09:00` |
| 담당 | `BE` 백엔드 규칙 · `AI` LLM 호출(곽도윤). 키 없으면 AI 단계는 규칙 모드 |
| 저장 | SQLite `data/malbus.db` (테이블은 `docs/erd.md`). 서버 시작 시 `schema.sql` + `data/정류장.csv`로 생성, DB 파일은 저장소에 올리지 않음 |

## 엔드포인트 목록

| # | Method | URL | 설명 | 담당 |
|---|---|---|---|---|
| 1 | GET | `/health` | 서버 상태, 모드 | BE |
| 2 | GET | `/stops` | 정류장 목록 | BE |
| 3 | POST | `/reservations` | 통화 시작, 빈 예약 생성 | BE |
| 4 | POST | `/reservations/{id}/lines` | 통화 한 줄 추가 → 양식 다시 추출 | BE + AI |
| 5 | PATCH | `/reservations/{id}/fields/{name}` | 상담원이 칸 선택·수정 | BE |
| 6 | POST | `/reservations/{id}/confirm` | 예약 확정 | BE |
| 7 | GET | `/reservations` | 예약 목록 | BE |
| 8 | GET | `/reservations/{id}` | 예약 상세 | BE |
| 9 | POST | `/extract` | 저장 없는 추출 (채점·테스트용) | BE + AI |
| 10 | POST | `/aliases` | 별칭 추가 (모달) | BE |
| 11 | GET | `/aliases` | 별칭 목록 | BE |
| 12 | POST | `/aliases/{id}/approve` · `/reject` | 별칭 승인·거절 | BE |

---

## 데이터 모델

`?` = 생략 불가지만 `null` 가능. 표기는 TypeScript 형식.

```ts
type Stop = {
  id: string;            // "S01"
  name: string;          // 원본 정류장명
  lat: number;
  lng: number;
  aliases: string[];     // CSV 별칭 '|' 분리
};

type Field<T> = {
  value: T | null;       // 빈칸이면 null. 확인 필요여도 제안값 가능 ("10:00")
  said: string | null;   // 고객이 한 말
  needsCheck: boolean;   // true = 확인 필요 (빈칸도 true)
  reason: "MISSING" | "AMBIGUOUS" | "INVALID" | "CORRECTED" | "NOT_IN_LIST" | null;
  askHint: string | null;      // 물어볼 말
  evidence: { lineNo: number; text: string } | null;  // 근거
  previous: T | null;          // 바뀌기 전 값 ("바뀜" 표시)
  source: "AI" | "RULE" | "AGENT" | null;  // AGENT = 상담원이 고름
  decidedAt: string | null;
  note: string | null;         // 상담원 메모
};

type StopField = Field<string> & {          // value = 정류장 ID
  candidates: {
    stopId: string;
    matchType: "NAME" | "ALIAS" | "LEARNED" | "PARTIAL" | "FUZZY";
    reason: string;            // "별칭 '아파트' 일치"
  }[];                         // 최대 5개, 없으면 []
  moreCount: number;           // 5개 초과분
};

type TranscriptLine = {
  lineNo: number;              // 1부터
  at: string;                  // 시각. 화면 "00:18"은 FE가 계산
  speaker: "AGENT" | "CUSTOMER" | "SYSTEM";
  text: string;
};

type Reservation = {
  id: number;
  channel: "AGENT_CALL";
  status: "DRAFT" | "CONFIRMED";
  mode: "LLM" | "RULE";
  receivedAt: string;          // 상대 날짜 변환 기준
  purpose: Field<"RESERVE" | "CHANGE" | "CANCEL" | "INQUIRY">;  // 기본 RESERVE (화면 고정)
  from: StopField;
  to: StopField;
  date: Field<string>;         // YYYY-MM-DD
  time: Field<string>;         // HH:MM
  people: Field<number>;       // 1 이상. 말 안 하면 null (1 기본값 금지)
  assist: [];                  // 선택 기능, 항상 빈 배열
  transcript: TranscriptLine[];
  createdAt: string;
  updatedAt: string;
  confirmedAt: string | null;
  confirmedBy: string | null;  // 상담원 이름
};

type LearnedAlias = {
  id: number;
  said: string;                // 조사 뗀 표현
  stopId: string;
  count: number;               // 이 정류장을 고른 횟수
  conflicts: Record<string, number>;  // 같은 표현으로 다른 정류장을 고른 횟수 (저장 안 함, 조회 시 계산)
  status: "PENDING" | "APPROVED" | "REJECTED";
  firstSeen: string;
  lastSeen: string;
  reservationIds: number[];
  recommended: boolean;        // count ≥ 2 이고 conflicts 없음 → 승인 추천 표시 (계산값)
};
```

### 칸 상태 ↔ 화면

| 화면 | 조건 |
|---|---|
| 빈칸 | `value = null` |
| 확인 필요 | `needsCheck = true` |
| 확인 완료 | `needsCheck = false`, `source = AI \| RULE` |
| 상담원이 고름 | `needsCheck = false`, `source = AGENT` |

### 판정 규칙 (BE)

| 규칙 | 내용 |
|---|---|
| 정류장 확인 완료 | `NAME` / `ALIAS` / `LEARNED` 일치가 정확히 1곳일 때만 |
| 자동 선택 금지 | 후보가 여러 개면 맨 위라도 고르지 않음 |
| 후보 순서 | 이름 → 별칭 → 배운 별칭 → 부분 일치 → 비슷한 글자 |
| 목록 밖 | 후보 `[]`, `reason = NOT_IN_LIST`. 정류장 생성 금지 |
| 상대 날짜 | 오늘 / 내일 / 모레 → `receivedAt` 기준 날짜 |
| 시간 | 오전·오후 없으면 제안값 + `needsCheck = true` |
| 상담원 값 우선 | `source = AGENT` 칸은 재추출로 덮어쓰지 않음. 고객이 다른 값으로 바꾸면 `previous` 저장, `CORRECTED`, `needsCheck = true` |
| 배운 별칭 | `PENDING`은 후보 순서만 올림. `APPROVED`부터 확인 완료 판정에 사용 |

### 에러

```json
{ "error": { "code": "CONFIRM_BLOCKED", "message": "확인 필요 2칸 남음", "details": { "fields": ["from", "time"] } } }
```

| code | HTTP | 상황 |
|---|---|---|
| `NOT_FOUND` | 404 | 예약·별칭 없음 |
| `VALIDATION_FAILED` | 400 | 형식 오류 |
| `STOP_NOT_FOUND` | 400 | 목록에 없는 정류장 ID |
| `SAME_STOP` | 400 | 출발 = 도착 |
| `PAST_DATE` | 400 | 접수일 이전 날짜 |
| `INVALID_PEOPLE` | 400 | 인원 1 미만 |
| `CONFIRM_BLOCKED` | 409 | 확인 필요·빈칸 남음 |
| `ALREADY_CONFIRMED` | 409 | 확정된 예약 수정·재확정 |
| `PURPOSE_NOT_SUPPORTED` | 409 | 목적이 예약이 아닌 건을 확정 |

---

## 엔드포인트 상세

### 1. `GET /health`

```json
// 200
{ "status": "ok", "mode": "RULE", "stops": 17 }
```

### 2. `GET /stops`

```json
// 200  Stop[]
[{ "id": "S02", "name": "범물청구타운앞", "lat": 35.81943545, "lng": 128.6427029, "aliases": ["청구타운", "청구아파트"] }]
```

### 3. `POST /reservations`

```json
// Request  (receivedAt 생략 시 서버 현재 시각)
{ "receivedAt": "2026-11-17T14:00:00+09:00" }
```
`201` Reservation — 모든 칸 빈칸, `purpose.value = "RESERVE"`, `status = "DRAFT"`

### 4. `POST /reservations/{id}/lines`

통화 한 줄 추가 후 전체 통화로 다시 추출(AI) → BE 규칙으로 판정.

```json
// Request
{ "speaker": "CUSTOMER", "text": "내일 열 시에." }
```
`200` Reservation · 확정된 예약은 줄만 추가하고 칸은 그대로 · `404`

### 5. `PATCH /reservations/{id}/fields/{name}`

`{name}`: `purpose` `from` `to` `date` `time` `people`

```json
// Request
{ "value": "S10", "note": null }

// 200
{
  "reservation": { /* Reservation */ },
  "aliasSuggestion": { "said": "아파트 앞", "stopId": "S10" }   // 없으면 null
}
```
- 통과 시 `needsCheck = false`, `source = "AGENT"`
- `aliasSuggestion`: 출발·도착에서 고객이 한 말이 그 정류장의 이름·별칭에 없을 때 → FE가 **별칭 추가 모달** 띄움
- 에러: `STOP_NOT_FOUND` `SAME_STOP` `PAST_DATE` `INVALID_PEOPLE` `ALREADY_CONFIRMED`

### 6. `POST /reservations/{id}/confirm`

되읽기 창의 "고객이 맞다고 함 · 확정" 버튼.

```json
// Request
{ "confirmedBy": "김채영" }
```
- 조건(`purpose = RESERVE`): `from` `to` `date` `time` `people` 모두 `value ≠ null` · `needsCheck = false` · 출발≠도착 · 인원≥1 · 접수일 이후
- `200` Reservation — `status = "CONFIRMED"`, transcript에 `SYSTEM` 기록 줄 추가, DB 저장 (D-6의 JSON 화면은 이 응답을 그대로 표시)
- 목적이 변경·취소·문의면 `409 PURPOSE_NOT_SUPPORTED`. 분류 결과는 양식에 보여 주되 이번 범위에서는 확정하지 않음 (상담원이 목적을 예약으로 고치면 확정 가능, 아니면 DRAFT로 남김)
- `409 CONFIRM_BLOCKED` (`details.fields`) · `409 ALREADY_CONFIRMED`

### 7. `GET /reservations?status=DRAFT|CONFIRMED`

```json
// 200  (status 생략 시 전체)
[{ "id": 12, "status": "CONFIRMED", "receivedAt": "...", "from": "S10", "to": "S01", "date": "2026-11-11", "time": "10:00", "people": 1 }]
```

### 8. `GET /reservations/{id}`

`200` Reservation · `404`

### 9. `POST /extract`

```json
// Request
{
  "receivedAt": "2026-11-17T14:00:00+09:00",
  "transcript": [{ "speaker": "CUSTOMER", "text": "내일 오전 아홉 시 반에 범물청구타운 앞에서 동아백화점 수성점 앞까지 갈라고요" }]
}

// 200
{ "mode": "RULE", "purpose": {}, "from": {}, "to": {}, "date": {}, "time": {}, "people": {} }  // 각 Field / StopField
```

### 10. `POST /aliases`

모달의 "추가". 같은 `said` + `stopId`가 있으면 `count`만 +1.

```json
// Request
{ "said": "아파트 앞", "stopId": "S10", "reservationId": 12 }
```
`201` 새로 생성 · `200` 기존 갱신 — LearnedAlias · `400 STOP_NOT_FOUND`

### 11. `GET /aliases?status=PENDING|APPROVED|REJECTED`

`200` LearnedAlias[]

### 12. `POST /aliases/{id}/approve` · `POST /aliases/{id}/reject`

`200` LearnedAlias · approve 시 `conflicts`가 있으면 응답에 `"warning": "다른 정류장 선택 기록 있음"` 포함 · `404`
- 승인·거절은 사람이 한다. 시제품이라 권한 구분 없이 상담원 화면에서 누구나 가능
- 자동 승인 없음. `recommended = true`(같은 표현으로 같은 정류장을 2번 이상, 다른 정류장 기록 없음)인 건을 목록 위에 "승인 추천"으로 표시

---

## AI 모듈 출력 (곽도윤 합의 필요)

AI는 말·근거·후보 순서만 반환. **정류장 확정과 `needsCheck` 판정은 BE.**

```json
{
  "purpose": { "value": "RESERVE", "evidenceLineNo": 2 },
  "from":   { "said": "아파트 앞", "evidenceLineNo": 4, "rankedStopIds": ["S10", "S07"], "askHint": "어느 아파트에 사세요?" },
  "to":     { "said": "용지역", "evidenceLineNo": 4, "rankedStopIds": ["S01", "S12"], "askHint": null },
  "date":   { "said": "내일", "evidenceLineNo": 6 },
  "time":   { "said": "열 시", "hour": 10, "minute": 0, "meridiem": null, "evidenceLineNo": 6 },
  "people": { "said": "나 혼자요", "value": 1, "evidenceLineNo": 8 }
}
```

| 규칙 | 내용 |
|---|---|
| `rankedStopIds` | BE 후보 안에서만 순서. 목록 밖 ID는 BE가 버림 |
| `meridiem` | `AM` / `PM` / `null` (null이면 BE가 시간 확인 필요) |
| 말 안 한 칸 | `said: null` |

---

## 결정 사항 (2026-11-10)

| # | 내용 | 결정 |
|---|---|---|
| 1 | 목적이 변경·취소·문의일 때 | 분류만 하고 확정은 예약만 가능 (`PURPOSE_NOT_SUPPORTED`) |
| 2 | "인원 따로 고려 안 함" | 한 통화에 여러 예약으로 나누지 않음. 인원 칸은 유지 |
| 3 | 별칭 승인 기준 | 사람이 승인. 2번 이상 같은 선택 + 충돌 없음이면 승인 추천 표시 |
| 4 | 기술 스택 | Spring Boot + JPA + SQLite, 포트 8080 |
