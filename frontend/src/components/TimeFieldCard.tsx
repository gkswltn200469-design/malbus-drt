// 시간 칸. "열 시"처럼 오전/오후를 안 말하면 AI는 정하지 않고 제안값 + "?"만 보여준다
// (docs/기능규칙.md 5.4, docsui/D-2). 상담원은 오전/오후 버튼 하나로 바로 확인할 수 있다.
import { useState } from 'react'
import { FieldShell } from '@/components/FieldShell'
import { formatKoreanTime } from '@/lib/dateFormat'
import { fieldState } from '@/lib/fieldState'
import { useCallStore } from '@/store/callStore'

function toAmPm(hhmm: string, part: '오전' | '오후'): string {
  const [h, m] = hhmm.split(':').map(Number)
  let hour = h % 12
  if (part === '오후') hour += 12
  return `${String(hour).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export function TimeFieldCard() {
  const field = useCallStore((s) => s.fields.time)
  const overrideTime = useCallStore((s) => s.overrideTime)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(field.value ?? '')

  const state = fieldState(
    field.value !== null,
    field.needsCheck,
    field.resolvedBy,
  )
  const display = field.value
    ? formatKoreanTime(field.value) + (field.reason === 'ambiguous' ? ' ?' : '')
    : '아직 못 들음'

  return (
    <FieldShell
      label="시간"
      state={state}
      value={display}
      note={field.note}
      previousDisplay={field.previousDisplay}
    >
      {field.value && field.reason === 'ambiguous' && (
        <div className="mt-2 flex gap-2">
          <button
            type="button"
            onClick={() => overrideTime(toAmPm(field.value!, '오전'))}
            className="rounded bg-white px-2 py-1 text-xs font-semibold text-[#9a6a1c] ring-1 ring-[#E39A2D]"
          >
            오전이었어요
          </button>
          <button
            type="button"
            onClick={() => overrideTime(toAmPm(field.value!, '오후'))}
            className="rounded bg-white px-2 py-1 text-xs font-semibold text-[#9a6a1c] ring-1 ring-[#E39A2D]"
          >
            오후였어요
          </button>
        </div>
      )}

      {!editing ? (
        <button
          type="button"
          onClick={() => {
            setDraft(field.value ?? '')
            setEditing(true)
          }}
          className="mt-2 block text-xs font-medium text-[#1C4E80] hover:underline"
        >
          직접 입력
        </button>
      ) : (
        <div className="mt-2 flex items-center gap-2">
          <input
            type="time"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="rounded border border-slate-300 px-2 py-1 text-sm outline-none focus:border-[#1C4E80]"
          />
          <button
            type="button"
            onClick={() => {
              if (draft) overrideTime(draft)
              setEditing(false)
            }}
            className="rounded bg-[#1C4E80] px-2 py-1 text-xs font-semibold text-white"
          >
            확인
          </button>
          <button
            type="button"
            onClick={() => setEditing(false)}
            className="text-xs text-slate-500 hover:underline"
          >
            취소
          </button>
        </div>
      )}
    </FieldShell>
  )
}
