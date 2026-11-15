// 오른쪽 패널 · 예약 접수 및 확정. docsui/D-1(양식)·D-4(확정 버튼)의 규칙을 따른다:
// 확인 필요·빈칸이 하나라도 남으면 확정 버튼이 눌리지 않는다.
import { ConfirmedCard } from '@/components/ConfirmedCard'
import { ConfirmModal } from '@/components/ConfirmModal'
import { DateFieldCard } from '@/components/DateFieldCard'
import { FieldShell } from '@/components/FieldShell'
import { PeopleFieldCard } from '@/components/PeopleFieldCard'
import { StopFieldCard } from '@/components/StopFieldCard'
import { TimeFieldCard } from '@/components/TimeFieldCard'
import { checkReadyToConfirm, remainingFieldLabels } from '@/lib/resolveFields'
import { useCallStore } from '@/store/callStore'

export function ReservationPanel() {
  const status = useCallStore((s) => s.status)
  const fields = useCallStore((s) => s.fields)
  const openConfirmModal = useCallStore((s) => s.openConfirmModal)

  if (status === '확정') return <ConfirmedCard />

  const { ready, remaining } = checkReadyToConfirm(fields)

  return (
    <section className="flex h-full flex-col rounded-xl border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h2 className="font-bold text-slate-800">예약 접수 및 확정</h2>
        <span className="text-xs text-slate-400">
          통화 내용으로 자동 채움 · 칸마다 고칠 수 있음
        </span>
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        <FieldShell
          label="목적"
          state="confirmed"
          value={fields.purpose}
          fixed
        />
        <StopFieldCard label="출발 정류장" fieldKey="from" />
        <StopFieldCard label="도착 정류장" fieldKey="to" />
        <DateFieldCard />
        <TimeFieldCard />
        <PeopleFieldCard />
      </div>

      <div className="flex items-center justify-between border-t border-slate-200 p-3">
        {ready ? (
          <span className="text-sm font-medium text-[#3E9467]">
            모든 칸이 확인되었습니다
          </span>
        ) : (
          <span className="text-sm font-medium text-[#E39A2D]">
            ! 확인 필요 {remaining.length}칸 남음 ·{' '}
            {remainingFieldLabels(remaining)}
          </span>
        )}
        <button
          type="button"
          disabled={!ready}
          onClick={openConfirmModal}
          className="rounded bg-[#1C4E80] px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-300"
        >
          확정
        </button>
      </div>

      <ConfirmModal />
    </section>
  )
}
