// 확정 전 되읽기 창. docsui/D-4(확정 버튼이 눌리기까지) 4단계.
// 자동 확정은 없다 — 상담원이 고객에게 읽어 주고, 고객이 맞다고 해야 확정된다.
import { STOP_BY_ID } from '@/data/stops'
import { formatKoreanDate, formatKoreanTime } from '@/lib/dateFormat'
import { formatPeopleKorean } from '@/lib/koreanNumbers'
import { useCallStore } from '@/store/callStore'

export function ConfirmModal() {
  const open = useCallStore((s) => s.confirmModalOpen)
  const fields = useCallStore((s) => s.fields)
  const closeConfirmModal = useCallStore((s) => s.closeConfirmModal)
  const confirmReservation = useCallStore((s) => s.confirmReservation)

  if (!open) return null

  const fromName = fields.from.id
    ? (STOP_BY_ID[fields.from.id]?.name ?? fields.from.id)
    : ''
  const toName = fields.to.id
    ? (STOP_BY_ID[fields.to.id]?.name ?? fields.to.id)
    : ''
  const dateText = fields.date.value ? formatKoreanDate(fields.date.value) : ''
  const timeText = fields.time.value ? formatKoreanTime(fields.time.value) : ''
  const peopleText =
    fields.people.value !== null ? formatPeopleKorean(fields.people.value) : ''

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl">
        <h3 className="font-bold text-slate-800">
          고객에게 이렇게 읽어 주세요
        </h3>
        <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
          "{dateText} {timeText}, {fromName}에서 {toName}까지, {peopleText}{' '}
          예약하겠습니다. 맞으세요?"
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={closeConfirmModal}
            className="rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-600"
          >
            수정
          </button>
          <button
            type="button"
            onClick={confirmReservation}
            className="rounded bg-[#1C4E80] px-3 py-2 text-sm font-semibold text-white"
          >
            고객이 맞다고 함 · 확정
          </button>
        </div>
      </div>
    </div>
  )
}
