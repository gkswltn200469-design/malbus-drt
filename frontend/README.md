# 말로 부르는 버스 · 프론트엔드

상담원 통화 보조 화면입니다.

## 스택

TypeScript, React, Vite, Tailwind CSS, Zustand, TanStack Query

## 실행

Node.js 24 이상과 pnpm이 필요합니다. pnpm이 없으면 `npm install -g pnpm`으로 설치합니다.

```sh
cd frontend
pnpm install
pnpm dev
```

## 명령어

| 명령어              | 내용                                          |
| ------------------- | --------------------------------------------- |
| `pnpm dev`          | 개발 서버 실행                                |
| `pnpm build`        | 타입 검사 후 `dist/`에 빌드                   |
| `pnpm preview`      | 빌드 결과 미리보기                            |
| `pnpm lint`         | ESLint 검사                                   |
| `pnpm format`       | Prettier로 코드 정리                          |
| `pnpm format:check` | 코드 정리 여부만 검사                         |
| `pnpm test`         | Vitest 테스트 실행 (`src/**/*.test.{ts,tsx}`) |

## 참고

- `@/`는 `src/`를 가리킵니다. 예: `import App from '@/App'`
- 린트가 쓰는 `typescript-eslint`가 TypeScript 7을 아직 지원하지 않아 TypeScript는 6.0을 씁니다.
- API 키는 서버 환경 변수로만 다룹니다. `VITE_`로 시작하는 환경 변수는 브라우저 코드에 그대로 노출되므로 키를 넣지 않습니다.
