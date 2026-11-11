import { useState } from 'react'
import { FieldShell } from '@/components/FieldShell'
import { fieldState } from '@/lib/fieldState'
import { useCallStore } from '@/store/callStore'

export function PeopleFieldCard() {
  const field = useCallStore((s) => s.fields.people)
  const overridePeople = useCallStore((s) => s.overridePeople)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(String(field.value ?? 1))

  const state = fieldState(
    field.value !== null,
    field.needsCheck,
    field.resolvedBy,
  )
  const display = field.value !== null ? `${field.value}명` : '아직 못 들음'

  return (
    <FieldShell
      label="인원"
      state={state}
      value={display}
      note={field.note}
      previousDisplay={field.previousDisplay}
    >
      {!editing ? (
        <button
          type="button"
          onClick={() => {
            setDraft(String(field.value ?? 1))
            setEditing(true)
          }}
          className="mt-2 text-xs font-medium text-[#1C4E80] hover:underline"
        >
          직접 입력
        </button>
      ) : (
        <div className="mt-2 flex items-center gap-2">
          <input
            type="number"
            min={1}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="w-16 rounded border border-slate-300 px-2 py-1 text-sm outline-none focus:border-[#1C4E80]"
          />
          <button
            type="button"
            onClick={() => {
              const n = Number(draft)
              if (Number.isFinite(n)) overridePeople(n)
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
