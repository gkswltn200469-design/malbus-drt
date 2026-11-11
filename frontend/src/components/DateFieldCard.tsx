import { useState } from 'react'
import { FieldShell } from '@/components/FieldShell'
import { formatKoreanDate } from '@/lib/dateFormat'
import { fieldState } from '@/lib/fieldState'
import { useCallStore } from '@/store/callStore'

export function DateFieldCard() {
  const field = useCallStore((s) => s.fields.date)
  const overrideDate = useCallStore((s) => s.overrideDate)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(field.value ?? '')

  const state = fieldState(
    field.value !== null,
    field.needsCheck,
    field.resolvedBy,
  )
  const display = field.value ? formatKoreanDate(field.value) : '아직 못 들음'

  return (
    <FieldShell
      label="날짜"
      state={state}
      value={display}
      note={field.note}
      previousDisplay={field.previousDisplay}
    >
      {!editing ? (
        <button
          type="button"
          onClick={() => {
            setDraft(field.value ?? '')
            setEditing(true)
          }}
          className="mt-2 text-xs font-medium text-[#1C4E80] hover:underline"
        >
          직접 입력
        </button>
      ) : (
        <div className="mt-2 flex items-center gap-2">
          <input
            type="date"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="rounded border border-slate-300 px-2 py-1 text-sm outline-none focus:border-[#1C4E80]"
          />
          <button
            type="button"
            onClick={() => {
              if (draft) overrideDate(draft)
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
