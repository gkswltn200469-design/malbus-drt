// 예약 데이터 모델. docs/기능규칙.md 4~5장(필드 상태·예약 항목 정의), 12장(저장 형식),
// 15.1(needsCheck 제안)을 따른다. needsCheck 이름과 purpose 필드는 15장에서 "팀 논의 필요"로
// 남아 있는 항목이지만, 기능규칙서가 제안한 값을 그대로 구현해 두었다. 개발자 회의에서
// 다르게 정해지면 이 파일만 고치면 된다.

export type Speaker = '상담원' | '고객' | '기록'

export interface TranscriptLine {
  id: string
  /** 통화 시작 기준 경과 시간, mm:ss */
  time: string
  speaker: Speaker
  text: string
}

/** 양식 칸 하나의 상태. docs/기능규칙.md 4장. */
export type FieldState =
  | 'empty' // 빈칸 · 아직 못 들음
  | 'confirmed' // 확인 완료 · AI가 하나로 결정
  | 'needsCheck' // 확인 필요 · 상담원이 더 물어야 함
  | 'agentConfirmed' // 상담원이 직접 골라서 확인 완료가 된 경우

export type NeedsCheckReason =
  | 'missing' // 누락
  | 'ambiguous' // 모호 (후보 여럿·넓은 표현)
  | 'notFound' // 목록에 없음
  | 'conflict' // 충돌·비정상 (출발=도착 등)
  | 'changed' // 통화 도중 고객이 말을 바꿈 (docs/기능규칙.md 7.3, docsui D-4)
  | null

export interface StopField {
  /** 최종 정류장 ID. 확정되기 전에는 null. */
  id: string | null
  /** 어르신이 실제로 말한 표현. */
  said: string | null
  /** 정류장 목록 안에서 찾은 후보 ID (최대 5곳). */
  candidates: string[]
  needsCheck: boolean
  reason: NeedsCheckReason
  /** 확인 필요 사유를 상담원이 읽을 수 있는 한 줄 설명. */
  note: string | null
  resolvedBy: 'auto' | 'agent' | null
  /** 값이 확정된 뒤 고객이 말을 바꾼 경우, 취소선으로 보여 줄 이전 표시값. */
  previousDisplay: string | null
  /** 다음에 들어오는 통화 내용이 같은 말인지 비교하기 위한 내부 키. */
  lastAutoKey: string | null
}

export interface SimpleField<T> {
  value: T | null
  /** 어르신이 실제로 말한 표현 ("내일", "열 시" 등). */
  raw: string | null
  needsCheck: boolean
  reason: NeedsCheckReason
  note: string | null
  resolvedBy: 'auto' | 'agent' | null
  previousDisplay: string | null
  lastAutoKey: string | null
}

export interface ReservationFields {
  /** 이번 시제품은 이동수단 예약만 다루므로 화면 고정값. */
  purpose: string
  from: StopField
  to: StopField
  date: SimpleField<string> // YYYY-MM-DD
  time: SimpleField<string> // HH:MM, 24시간제
}

export type ReservationStatus = '확정 대기' | '확정'

export interface ReservationRecord {
  id: number
  channel: string
  status: ReservationStatus
  receivedAt: string // 접수 시각 ISO, 상대 날짜 변환 기준
  fields: ReservationFields
  transcript: TranscriptLine[]
  confirmedAt: string | null
  confirmedBy: string | null
}

export const REQUIRED_FIELD_KEYS = ['from', 'to', 'date', 'time'] as const
export type RequiredFieldKey = (typeof REQUIRED_FIELD_KEYS)[number]

export const FIELD_LABEL: Record<RequiredFieldKey, string> = {
  from: '출발 정류장',
  to: '도착 정류장',
  date: '날짜',
  time: '시간',
}
