// 확정 결과 화면. docsui/D-5(확정 결과 화면): 양식 자리가 예약 카드로 바뀌고,
// 저장된 JSON을 그대로 보여 준다. 배차 시스템 전달은 확장 과제라 지금은 화면·파일로만 남는다.
import { STOP_BY_ID } from '@/data/stops'
import { buildReservationJson } from '@/lib/buildReservationJson'
import { formatKoreanDate, formatKoreanTime } from '@/lib/dateFormat'
import { useCallStore } from '@/store/callStore'
import { FIELD_LABEL, type RequiredFieldKey } from '@/types/reservation'

export function ConfirmedCard() {
  const reservationId = useCallStore((s) => s.reservationId)
  const fields = useCallStore((s) => s.fields)
  const transcript = useCallStore((s) => s.transcript)
  const status = useCallStore((s) => s.status)
  const confirmedAt = useCallStore((s) => s.confirmedAt)
  const confirmedBy = useCallStore((s) => s.confirmedBy)
  const nextCall = useCallStore((s) => s.nextCall)

  const agentPicked: RequiredFieldKey[] = (
    ['from', 'to', 'date', 'time', 'people'] as const
  ).filter((k) => fields[k].resolvedBy === 'agent')

  const json = buildReservationJson({
    id: reservationId,
    status,
    fields,
    transcript,
  })

  return (
    <div className="flex h-full flex-col rounded-xl border border-slate-200 bg-white">
      <div className="rounded-t-xl bg-[#1C6B47] px-4 py-3 text-white">
        <p className="text-xs opacity-80">상담원 확인 · {confirmedAt}</p>
        <p className="text-lg font-bold">✓ 예약 #{reservationId} 확정됨</p>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        <div>
          <p className="text-xs text-slate-500">날짜·시간</p>
          <p className="text-2xl font-bold text-slate-900">
            {fields.date.value && formatKoreanDate(fields.date.value)}{' '}
            {fields.time.value && formatKoreanTime(fields.time.value)}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <p className="text-xs text-slate-500">출발</p>
            <p className="font-semibold text-slate-900">
              {fields.from.id &&
                (STOP_BY_ID[fields.from.id]?.name ?? fields.from.id)}
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">도착</p>
            <p className="font-semibold text-slate-900">
              {fields.to.id && (STOP_BY_ID[fields.to.id]?.name ?? fields.to.id)}
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">인원</p>
            <p className="font-semibold text-slate-900">
              {fields.people.value}명
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">목적</p>
            <p className="font-semibold text-slate-900">{fields.purpose}</p>
          </div>
        </div>

        {agentPicked.length > 0 && (
          <p className="text-xs text-slate-500">
            🔒 잠김 · 상담원이 고른 칸:{' '}
            {agentPicked.map((k) => FIELD_LABEL[k]).join(', ')}
          </p>
        )}

        <div>
          <p className="text-xs text-slate-500">
            ✓ 저장됨 · reservations/{reservationId}.json · {confirmedAt}
          </p>
          <pre className="mt-1 max-h-64 overflow-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">
            {JSON.stringify(json, null, 2)}
          </pre>
        </div>
      </div>

      <div className="flex items-center justify-between border-t border-slate-200 p-3">
        <div>
          <button
            type="button"
            className="rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-600"
            title="배차 시스템 전달은 확장 과제 · 지금은 화면과 파일로 저장"
          >
            수정
          </button>
          <span className="ml-2 text-xs text-slate-400">
            상담원: {confirmedBy}
          </span>
        </div>
        <button
          type="button"
          onClick={nextCall}
          className="rounded bg-[#1C4E80] px-4 py-2 text-sm font-semibold text-white"
        >
          다음 통화
        </button>
      </div>
    </div>
  )
}
