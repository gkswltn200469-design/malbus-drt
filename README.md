# 말로 부르는 버스 (가칭)

청년 공공AX 서포터즈 · 공드리팀

수요응답형 교통(DRT, 부르면 오는 버스) 예약 상담원을 돕는 AI입니다. 어르신은 지금처럼 사람과 통화하고, AI는 상담원 옆에서 통화 내용을 예약 양식으로 정리하고 정류장 후보를 추천합니다. 모든 예약은 상담원이 확정합니다.

- 개발 범위: 필수 기능 2개 (예약 양식 자동 채움, 정류장 후보 추천과 상담원 확정)
- 개발 완료: 2026년 11월 17일, 공공 GitLab 등록
- 팀 규칙과 최신 결정: [CLAUDE.md](CLAUDE.md)
- 서비스 설명과 근거: [서비스소개.md](서비스소개.md)
- 제출한 계획서: [docs/](docs/)

## 폴더

| 위치 | 내용 |
|---|---|
| `prototype/` | 기획 단계에서 만든 **참고용 시제품** (규칙 기반, 평창 예시 정류장). 개발 구조를 그대로 따를 필요는 없습니다 |
| `docs/` | 제출한 「AI를 활용한 공공서비스 직접개발 계획서」 |
| `backend/` | 예약 API 서버 (Spring Boot, SQLite). API 형식은 `docs/api.md` |

개발 코드의 폴더 구조는 개발자 회의에서 정한 뒤 여기에 적습니다.

## 백엔드 실행

JDK 21 이상이 필요합니다. 따로 설치할 DB는 없습니다.

```sh
cd backend
./gradlew bootRun   # http://localhost:8080
./gradlew test
./gradlew grade     # 대본 20개 채점 → test/채점결과_RULE.md
./gradlew gradeLlm  # 같은 채점을 LLM 모드로 → test/채점결과_LLM.md (키 필요, API 20번 호출, 2~3분)
```

- 처음 실행하면 `data/malbus.db`가 생기고 `data/정류장.csv`의 정류장이 들어갑니다. DB 파일은 저장소에 올리지 않습니다.
- `backend` 폴더에서 실행해야 합니다. DB와 CSV 경로가 `../data` 기준입니다.
- 스키마나 정류장 CSV를 바꿨으면 `data/malbus.db`를 지우고 다시 실행합니다.
- 확인: `curl localhost:8080/health` → `{"status":"ok","mode":"RULE","stops":17}`
- LLM 키는 저장소 루트의 `.env`(`.env.example`을 복사해서 만듦, 따옴표 없이 `키=값`)나 환경 변수로 넣습니다. `.env`는 `.gitignore`에 있어서 올라가지 않습니다. `GEMINI_API_KEY`가 있으면 Gemini(`gemini-3.6-flash`, `.env`의 `GEMINI_MODEL`로 바꿀 수 있음), 없고 `ANTHROPIC_API_KEY`가 있으면 Claude(`claude-sonnet-5-5`), 둘 다 없으면 규칙 모드(`mode: RULE`)로 동작합니다.
- Gemini 키는 Google AI Studio에서 무료로 받을 수 있습니다. 무료 등급은 입력이 Google 서비스 개선에 쓰일 수 있으니 실제 통화 내용·개인정보는 넣지 않습니다. 분당 호출 수 제한이 있어서 `gradeLlm`은 대본 사이에 7초씩 쉽니다.
- LLM 모드에서 호출이 실패하면 그 요청은 규칙 추출로 대신하고 서버 로그에 경고를 남깁니다. 끄려면 `malbus.llm.fallback-to-rule=false`.
- 프롬프트는 `backend/src/main/resources/llm/system-prompt.md`에 있습니다. 채점용 대본 20개는 프롬프트에 넣지 않습니다.
- `./gradlew test`는 키가 있어도 규칙 모드로 돌아서 API를 부르지 않습니다.

## 참고용 시제품 실행

설치할 것은 없습니다. 크롬에서 파일을 열면 됩니다(API 키 불필요).

**`prototype/agent.html`: 상담원 통화 보조 (필수 기능의 참고)**
- "▶ 같은 이름 회관" 같은 대본 버튼을 누르면 통화가 한 줄씩 들어오고, 오른쪽 양식이 채워집니다.
- "확인 필요"가 붙은 칸만 고르고 **확정**을 누르면 아래에 접수 시간이 기록됩니다.

**`prototype/index.html`: 상담원 부재 시 AI 접수 (선택 기능, 이번 범위 밖)**

## 범위와 한계

- 실제 콜센터 전화망·ARS와 연결하지 않습니다. 통화는 대본·텍스트 입력으로 재현합니다.
- 시연 데이터는 공공데이터포털 「대구교통공사_DRT 정류장 위치 정보」이며, 생활 지명 별칭은 팀이 만든 예시입니다.
- 배차 시스템 연동, 노쇼 처리, 도착 알림 전화는 포함하지 않습니다.
