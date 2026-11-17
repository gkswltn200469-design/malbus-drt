// 백엔드 예약 API 호출 + 응답을 화면 타입으로 바꾸는 곳. 형식은 docs/api.md
import { formatKoreanDate, formatKoreanTime } from '@/lib/dateFormat'
import type {
  NeedsCheckReason,
  ReservationFields,
  SimpleField,
  Speaker,
  StopField,
  TranscriptLine,
} from '@/types/reservation'

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

type ApiSpeaker = 'AGENT' | 'CUSTOMER' | 'SYSTEM'
type ApiReason =
  'MISSING' | 'AMBIGUOUS' | 'INVALID' | 'CORRECTED' | 'NOT_IN_LIST' | null

interface ApiField<T> {
  value: T | null
  said: string | null
  needsCheck: boolean
  reason: ApiReason
  askHint: string | null
  previous: T | null
  source: 'AI' | 'RULE' | 'AGENT' | null
}

interface ApiStopField extends ApiField<string> {
  candidates: { stopId: string; matchType: string; reason: string }[]
  moreCount: number
}

export interface ApiReservation {
  id: number
  status: 'DRAFT' | 'CONFIRMED'
  mode: 'LLM' | 'RULE'
  receivedAt: string
  purpose: ApiField<string>
  from: ApiStopField
  to: ApiStopField
  date: ApiField<string>
  time: ApiField<string>
  transcript: {
    lineNo: number
    at: string
    speaker: ApiSpeaker
    text: string
  }[]
  confirmedAt: string | null
  confirmedBy: string | null
}

export interface Stop {
  id: string
  name: string
  lat: number
  lng: number
  aliases: string[]
}

export interface AliasSuggestion {
  said: string
  stopId: string
}

export class ApiError extends Error {
  readonly code: string
  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

async function call<T>(method: string, path: string, body?: unknown) {
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError('NETWORK', `백엔드 서버에 연결이 안 됩니다 (${BASE})`)
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(
      data?.error?.code ?? 'UNKNOWN',
      data?.error?.message ?? `요청 실패 (${res.status})`,
    )
  }
  return data as T
}

const TO_API_SPEAKER: Record<Speaker, ApiSpeaker> = {
  상담원: 'AGENT',
  고객: 'CUSTOMER',
  기록: 'SYSTEM',
}

const FROM_API_SPEAKER: Record<ApiSpeaker, Speaker> = {
  AGENT: '상담원',
  CUSTOMER: '고객',
  SYSTEM: '기록',
}

export const api = {
  stops: () => call<Stop[]>('GET', '/stops'),
  // receivedAt 안 넘기면 서버 현재 시각
  create: (receivedAt?: string) =>
    call<ApiReservation>('POST', '/reservations', { receivedAt }),
  addLine: (id: number, speaker: Speaker, text: string) =>
    call<ApiReservation>('POST', `/reservations/${id}/lines`, {
      speaker: TO_API_SPEAKER[speaker],
      text,
    }),
  patchField: (id: number, name: string, value: string) =>
    call<{
      reservation: ApiReservation
      aliasSuggestion: AliasSuggestion | null
    }>('PATCH', `/reservations/${id}/fields/${name}`, { value, note: null }),
  confirm: (id: number, confirmedBy: string) =>
    call<ApiReservation>('POST', `/reservations/${id}/confirm`, {
      confirmedBy,
    }),
  addAlias: (said: string, stopId: string, reservationId: number) =>
    call<unknown>('POST', '/aliases', { said, stopId, reservationId }),
}

// 대본의 "2026-11-17 14:00"을 서버 형식으로
export function toServerTime(local: string): string {
  return `${local.replace(' ', 'T')}:00+09:00`
}

const REASON: Record<NonNullable<ApiReason>, NeedsCheckReason> = {
  MISSING: 'missing',
  AMBIGUOUS: 'ambiguous',
  INVALID: 'conflict',
  CORRECTED: 'changed',
  NOT_IN_LIST: 'notFound',
}

function common<T>(f: ApiField<T>) {
  return {
    needsCheck: f.needsCheck,
    reason: f.reason ? REASON[f.reason] : null,
    note: f.askHint,
    resolvedBy:
      f.source === 'AGENT'
        ? ('agent' as const)
        : f.source
          ? ('auto' as const)
          : null,
    lastAutoKey: null,
  }
}

function toSimple(
  f: ApiField<string>,
  format: (v: string) => string,
): SimpleField<string> {
  return {
    ...common(f),
    value: f.value,
    raw: f.said,
    previousDisplay: f.previous ? format(f.previous) : null,
  }
}

function toStop(f: ApiStopField, stopName: (id: string) => string): StopField {
  return {
    ...common(f),
    id: f.value,
    said: f.said,
    candidates: f.candidates.map((c) => c.stopId),
    previousDisplay: f.previous ? stopName(f.previous) : null,
  }
}

export function toFields(
  r: ApiReservation,
  stopName: (id: string) => string,
): ReservationFields {
  return {
    purpose: '이동수단 예약',
    from: toStop(r.from, stopName),
    to: toStop(r.to, stopName),
    date: toSimple(r.date, formatKoreanDate),
    time: toSimple(r.time, formatKoreanTime),
  }
}

export function toTranscript(
  r: ApiReservation,
  elapsed: (lineNo: number) => string,
): TranscriptLine[] {
  return r.transcript.map((l) => ({
    id: String(l.lineNo),
    time: elapsed(l.lineNo),
    speaker: FROM_API_SPEAKER[l.speaker],
    text: l.text,
  }))
}
