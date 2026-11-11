// 통화체 한글 숫자(아홉 시, 혼자, 둘이 등)를 다루는 작은 도우미.
// prototype/engine.js의 숫자 처리 방식을 TypeScript로 옮겼다.

const KOREAN_NUMBER_WORDS: Array<[string, number]> = [
  ['열두', 12],
  ['열한', 11],
  ['다섯', 5],
  ['여섯', 6],
  ['일곱', 7],
  ['여덟', 8],
  ['아홉', 9],
  ['한', 1],
  ['두', 2],
  ['세', 3],
  ['네', 4],
  ['열', 10],
]

/** "아홉" · "9" 같은 한 토큰을 숫자로 바꾼다. 모르면 null. */
export function wordToNumber(word: string): number | null {
  if (/^\d+$/.test(word)) return Number(word)
  const found = KOREAN_NUMBER_WORDS.find(([w]) => w === word)
  return found ? found[1] : null
}

/** 시·분·명 앞에 오는 숫자 표현을 잡는 정규식 조각. */
export const NUMBER_WORD_PATTERN =
  '(\\d{1,2}|열두|열한|다섯|여섯|일곱|여덟|아홉|한|두|세|네|열)'

/** 인원수를 "한 분" "두 분"처럼 높임 단위로 읽어 준다. 10명을 넘으면 "N명". */
const PEOPLE_COUNTER = [
  '',
  '한',
  '두',
  '세',
  '네',
  '다섯',
  '여섯',
  '일곱',
  '여덟',
  '아홉',
  '열',
]
export function formatPeopleKorean(people: number): string {
  if (people >= 1 && people < PEOPLE_COUNTER.length) {
    return `${PEOPLE_COUNTER[people]} 분`
  }
  return `${people}명`
}
