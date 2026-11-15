// 확정된 예약을 저장용 JSON으로 바꾼다. 형식은 docs/기능규칙.md 12장(예약 저장 기본안)과
// 15장(needsCheck·purpose 제안)을 따른다. 15장은 아직 "팀 논의 필요"로 남아 있는 항목이라,
// 개발자 회의에서 필드명이 달라지면 이 함수만 고치면 되도록 한곳에 모아 두었다.
import type {
  ReservationFields,
  ReservationStatus,
  TranscriptLine,
} from '@/types/reservation'

export interface ReservationJsonInput {
  id: number
  status: ReservationStatus
  fields: ReservationFields
  transcript: TranscriptLine[]
}

export function buildReservationJson({
  id,
  status,
  fields,
  transcript,
}: ReservationJsonInput) {
  return {
    id,
    channel: '상담원 통화',
    status,
    purpose: fields.purpose,
    from: {
      id: fields.from.id,
      said: fields.from.said,
      candidates: fields.from.candidates,
      needsCheck: fields.from.needsCheck,
    },
    to: {
      id: fields.to.id,
      said: fields.to.said,
      candidates: fields.to.candidates,
      needsCheck: fields.to.needsCheck,
    },
    date: fields.date.value,
    time: fields.time.value,
    people: fields.people.value,
    assist: [] as string[],
    transcript: transcript.map((l) => [l.speaker, l.text]),
  }
}
