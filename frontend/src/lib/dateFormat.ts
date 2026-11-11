// 날짜·시간 표시 도우미. 저장 형식은 docs/기능규칙.md 5.3·5.4를 따른다
// (날짜 YYYY-MM-DD, 시간 24시간제 HH:MM).

const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토']

function toDateOnly(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

export function addDays(d: Date, days: number): Date {
  const next = toDateOnly(d)
  next.setDate(next.getDate() + days)
  return next
}

export function formatISODate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** "11월 11일 (수)" */
export function formatKoreanDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY_KO[d.getDay()]})`
}

/** "오전 10시" · "오후 2시 30분" */
export function formatKoreanTime(hhmm: string): string {
  const [h, m] = hhmm.split(':').map(Number)
  const part = h < 12 ? '오전' : '오후'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return m ? `${part} ${h12}시 ${m}분` : `${part} ${h12}시`
}

/** 접수 시각 기준으로 오늘보다 이전 날짜인지 (과거 날짜 확인 필요 판정용). */
export function isPastDate(iso: string, receivedAt: Date): boolean {
  const target = new Date(`${iso}T00:00:00`).getTime()
  return target < toDateOnly(receivedAt).getTime()
}
