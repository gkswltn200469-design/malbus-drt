// 통화 내용 추출 결과(src/lib/extract.ts)를 예약 양식 칸 상태로 바꾸는 규칙.
// docs/기능규칙.md 4장(필드 상태)·8장(확인 필요 판정표)·9장(상담원 수정 규칙),
// docsui/D-2(양식 칸 4가지 상태), D-3(정류장 고르기), D-4(확정 버튼·말 바꿈)를 그대로 따른다.

import { STOP_BY_ID } from '@/data/stops'
import { isPastDate } from '@/lib/dateFormat'
import type { ExtractResult } from '@/lib/extract'
import type {
  ReservationFields,
  RequiredFieldKey,
  SimpleField,
  StopField,
} from '@/types/reservation'
import { FIELD_LABEL, REQUIRED_FIELD_KEYS } from '@/types/reservation'

const EMPTY_STOP_FIELD: StopField = {
  id: null,
  said: null,
  candidates: [],
  needsCheck: false,
  reason: null,
  note: null,
  resolvedBy: null,
  previousDisplay: null,
  lastAutoKey: null,
}

function emptySimpleField<T>(): SimpleField<T> {
  return {
    value: null,
    raw: null,
    needsCheck: false,
    reason: null,
    note: null,
    resolvedBy: null,
    previousDisplay: null,
    lastAutoKey: null,
  }
}

export function createEmptyFields(): ReservationFields {
  return {
    purpose: '이동수단 예약',
    from: { ...EMPTY_STOP_FIELD },
    to: { ...EMPTY_STOP_FIELD },
    date: emptySimpleField<string>(),
    time: emptySimpleField<string>(),
    people: emptySimpleField<number>(),
  }
}

function stopDisplayName(field: StopField): string {
  if (field.id) return STOP_BY_ID[field.id]?.name ?? field.id
  return field.said ?? ''
}

function resolveStopField(
  current: StopField,
  extracted: ExtractResult['from'],
): StopField {
  const autoKey = extracted?.said ?? null

  // 이미 확인 완료되었거나 상담원이 고른 칸: 같은 말이면 그대로 둔다.
  if (current.resolvedBy) {
    if (autoKey === null || autoKey === current.lastAutoKey) return current
    // 고객이 말을 바꿈: 이전 값은 취소선으로 보여주고 다시 확인 필요로 바꾼다.
    const single = extracted!.candidates.length === 1
    return {
      id: single ? extracted!.candidates[0] : null,
      said: extracted!.said,
      candidates: extracted!.candidates,
      needsCheck: true,
      reason: 'changed',
      note: `'${extracted!.said}' · 고객이 말을 바꿈`,
      resolvedBy: null,
      previousDisplay: stopDisplayName(current),
      lastAutoKey: autoKey,
    }
  }

  if (!extracted)
    return current.lastAutoKey === null ? current : { ...EMPTY_STOP_FIELD }

  if (extracted.candidates.length === 0) {
    return {
      id: null,
      said: extracted.said,
      candidates: [],
      needsCheck: true,
      reason: 'notFound',
      note: '정류장 목록에 없음 · 장소를 다시 물어야 함',
      resolvedBy: null,
      previousDisplay: null,
      lastAutoKey: autoKey,
    }
  }

  if (extracted.candidates.length === 1) {
    return {
      id: extracted.candidates[0],
      said: extracted.said,
      candidates: extracted.candidates,
      needsCheck: false,
      reason: null,
      note: `근거 '${extracted.said}'`,
      resolvedBy: 'auto',
      previousDisplay: null,
      lastAutoKey: autoKey,
    }
  }

  return {
    id: null,
    said: extracted.said,
    candidates: extracted.candidates,
    needsCheck: true,
    reason: 'ambiguous',
    note: `같은 이름 정류장 ${extracted.candidates.length}곳 · 후보 중에서 골라야 함`,
    resolvedBy: null,
    previousDisplay: null,
    lastAutoKey: autoKey,
  }
}

function resolveDateField(
  current: SimpleField<string>,
  extracted: ExtractResult['date'],
  receivedAt: Date,
): SimpleField<string> {
  const autoKey = extracted?.raw ?? null
  if (current.resolvedBy) {
    if (autoKey === null || autoKey === current.lastAutoKey) return current
    return {
      value: extracted!.value,
      raw: extracted!.raw,
      needsCheck: true,
      reason: 'changed',
      note: `'${extracted!.raw}' · 고객이 말을 바꿈`,
      resolvedBy: null,
      previousDisplay: current.raw,
      lastAutoKey: autoKey,
    }
  }
  if (!extracted)
    return current.lastAutoKey === null ? current : emptySimpleField<string>()

  if (isPastDate(extracted.value, receivedAt)) {
    return {
      value: extracted.value,
      raw: extracted.raw,
      needsCheck: true,
      reason: 'conflict',
      note: '과거 날짜 · 다시 확인 필요',
      resolvedBy: null,
      previousDisplay: null,
      lastAutoKey: autoKey,
    }
  }
  return {
    value: extracted.value,
    raw: extracted.raw,
    needsCheck: false,
    reason: null,
    note: `'${extracted.raw}'을 실제 날짜로 바꿈`,
    resolvedBy: 'auto',
    previousDisplay: null,
    lastAutoKey: autoKey,
  }
}

function resolveTimeField(
  current: SimpleField<string>,
  extracted: ExtractResult['time'],
): SimpleField<string> {
  const autoKey = extracted?.raw ?? null
  if (current.resolvedBy) {
    if (autoKey === null || autoKey === current.lastAutoKey) return current
    return {
      value: extracted!.value,
      raw: extracted!.raw,
      needsCheck: true,
      reason: 'changed',
      note: `'${extracted!.raw}' · 고객이 말을 바꿈`,
      resolvedBy: null,
      previousDisplay: current.value,
      lastAutoKey: autoKey,
    }
  }
  if (!extracted)
    return current.lastAutoKey === null ? current : emptySimpleField<string>()

  if (extracted.ambiguous) {
    return {
      value: extracted.value,
      raw: extracted.raw,
      needsCheck: true,
      reason: 'ambiguous',
      note: `'${extracted.raw}' · 오전/오후 확인 필요`,
      resolvedBy: null,
      previousDisplay: null,
      lastAutoKey: autoKey,
    }
  }
  return {
    value: extracted.value,
    raw: extracted.raw,
    needsCheck: false,
    reason: null,
    note: `근거 '${extracted.raw}'`,
    resolvedBy: 'auto',
    previousDisplay: null,
    lastAutoKey: autoKey,
  }
}

function resolvePeopleField(
  current: SimpleField<number>,
  extracted: ExtractResult['people'],
): SimpleField<number> {
  const autoKey = extracted?.raw ?? null
  if (current.resolvedBy) {
    if (autoKey === null || autoKey === current.lastAutoKey) return current
    return {
      value: extracted!.value,
      raw: extracted!.raw,
      needsCheck: true,
      reason: 'changed',
      note: `'${extracted!.raw}' · 고객이 말을 바꿈`,
      resolvedBy: null,
      previousDisplay:
        current.value !== null ? `${current.value}명` : current.raw,
      lastAutoKey: autoKey,
    }
  }
  if (!extracted)
    return current.lastAutoKey === null ? current : emptySimpleField<number>()

  if (extracted.value < 1) {
    return {
      value: extracted.value,
      raw: extracted.raw,
      needsCheck: true,
      reason: 'conflict',
      note: '인원이 0명 이하 · 다시 확인 필요',
      resolvedBy: null,
      previousDisplay: null,
      lastAutoKey: autoKey,
    }
  }
  return {
    value: extracted.value,
    raw: extracted.raw,
    needsCheck: false,
    reason: null,
    note: `근거 '${extracted.raw}'`,
    resolvedBy: 'auto',
    previousDisplay: null,
    lastAutoKey: autoKey,
  }
}

/** 출발=도착이면 두 칸 모두 다시 확인 필요로 되돌린다 (기능규칙 5.1·10장). */
function applyStopConflictCheck(fields: ReservationFields): ReservationFields {
  if (fields.from.id && fields.to.id && fields.from.id === fields.to.id) {
    const conflictNote = '출발과 도착이 같음 · 다시 확인'
    return {
      ...fields,
      from: {
        ...fields.from,
        needsCheck: true,
        reason: 'conflict',
        note: conflictNote,
        resolvedBy: null,
      },
      to: {
        ...fields.to,
        needsCheck: true,
        reason: 'conflict',
        note: conflictNote,
        resolvedBy: null,
      },
    }
  }
  return fields
}

export function applyExtraction(
  fields: ReservationFields,
  extraction: ExtractResult,
  receivedAt: Date,
): ReservationFields {
  const next: ReservationFields = {
    purpose: fields.purpose,
    from: resolveStopField(fields.from, extraction.from),
    to: resolveStopField(fields.to, extraction.to),
    date: resolveDateField(fields.date, extraction.date, receivedAt),
    time: resolveTimeField(fields.time, extraction.time),
    people: resolvePeopleField(fields.people, extraction.people),
  }
  return applyStopConflictCheck(next)
}

/** 상담원이 후보 목록 또는 "목록에 없음 찾기"에서 정류장 하나를 고른다. */
export function selectStop(
  fields: ReservationFields,
  key: 'from' | 'to',
  stopId: string,
): ReservationFields {
  const field = fields[key]
  const candidates = field.candidates.includes(stopId)
    ? field.candidates
    : [...field.candidates, stopId]
  const next: ReservationFields = {
    ...fields,
    [key]: {
      ...field,
      id: stopId,
      candidates,
      needsCheck: false,
      reason: null,
      note: '상담원이 고름',
      resolvedBy: 'agent',
      previousDisplay: null,
    },
  }
  return applyStopConflictCheck(next)
}

export function overrideDate(
  fields: ReservationFields,
  value: string,
  receivedAt: Date,
): ReservationFields {
  const past = isPastDate(value, receivedAt)
  return {
    ...fields,
    date: {
      value,
      raw: fields.date.raw,
      needsCheck: past,
      reason: past ? 'conflict' : null,
      note: past ? '과거 날짜 · 다시 확인 필요' : '상담원이 직접 입력',
      resolvedBy: past ? null : 'agent',
      previousDisplay: null,
      lastAutoKey: fields.date.lastAutoKey,
    },
  }
}

export function overrideTime(
  fields: ReservationFields,
  value: string,
): ReservationFields {
  return {
    ...fields,
    time: {
      value,
      raw: fields.time.raw,
      needsCheck: false,
      reason: null,
      note: '상담원이 직접 입력',
      resolvedBy: 'agent',
      previousDisplay: null,
      lastAutoKey: fields.time.lastAutoKey,
    },
  }
}

export function overridePeople(
  fields: ReservationFields,
  value: number,
): ReservationFields {
  const invalid = !Number.isInteger(value) || value < 1
  return {
    ...fields,
    people: {
      value,
      raw: fields.people.raw,
      needsCheck: invalid,
      reason: invalid ? 'conflict' : null,
      note: invalid ? '인원이 0명 이하 · 다시 확인 필요' : '상담원이 직접 입력',
      resolvedBy: invalid ? null : 'agent',
      previousDisplay: null,
      lastAutoKey: fields.people.lastAutoKey,
    },
  }
}

export interface ConfirmCheck {
  ready: boolean
  remaining: RequiredFieldKey[]
}

export function checkReadyToConfirm(fields: ReservationFields): ConfirmCheck {
  const remaining: RequiredFieldKey[] = []
  for (const key of REQUIRED_FIELD_KEYS) {
    const field = fields[key]
    const hasValue =
      key === 'from' || key === 'to'
        ? (field as StopField).id !== null
        : (field as SimpleField<unknown>).value !== null
    if (field.needsCheck || !hasValue) remaining.push(key)
  }
  return { ready: remaining.length === 0, remaining }
}

export function remainingFieldLabels(remaining: RequiredFieldKey[]): string {
  return remaining.map((k) => FIELD_LABEL[k]).join(', ')
}
