import type {
  FieldState,
  ReservationFields,
  RequiredFieldKey,
  SimpleField,
  StopField,
} from '@/types/reservation'
import { FIELD_LABEL, REQUIRED_FIELD_KEYS } from '@/types/reservation'

export function fieldState(
  hasValue: boolean,
  needsCheck: boolean,
  resolvedBy: 'auto' | 'agent' | null,
): FieldState {
  if (needsCheck) return 'needsCheck'
  if (!hasValue && !resolvedBy) return 'empty'
  if (resolvedBy === 'agent') return 'agentConfirmed'
  return 'confirmed'
}

const EMPTY = {
  needsCheck: false,
  reason: null,
  note: null,
  resolvedBy: null,
  previousDisplay: null,
  lastAutoKey: null,
}

// 통화 시작 전 빈 양식
export function createEmptyFields(): ReservationFields {
  const stop: StopField = { ...EMPTY, id: null, said: null, candidates: [] }
  const simple: SimpleField<string> = { ...EMPTY, value: null, raw: null }
  return {
    purpose: '이동수단 예약',
    from: stop,
    to: { ...stop },
    date: simple,
    time: { ...simple },
  }
}

// 확정 버튼 활성화용. 최종 판정은 서버 confirm이 한다
export function checkReadyToConfirm(fields: ReservationFields) {
  const remaining: RequiredFieldKey[] = REQUIRED_FIELD_KEYS.filter((key) => {
    const field = fields[key]
    const hasValue =
      key === 'from' || key === 'to'
        ? (field as StopField).id !== null
        : (field as SimpleField<string>).value !== null
    return field.needsCheck || !hasValue
  })
  return { ready: remaining.length === 0, remaining }
}

export function remainingFieldLabels(remaining: RequiredFieldKey[]): string {
  return remaining.map((k) => FIELD_LABEL[k]).join(', ')
}
