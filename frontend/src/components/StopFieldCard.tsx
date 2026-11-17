// 출발·도착 정류장 칸. docsui/D-3(정류장 고르기)의 규칙을 따른다:
// 정류장은 목록 안에서만 고르고, 고르는 사람은 언제나 상담원이다. 타이핑으로 넣지 않는다.
import { useState } from 'react'
import { FieldShell } from '@/components/FieldShell'
import { fieldState } from '@/lib/fieldState'
import { useCallStore } from '@/store/callStore'
import type { Stop } from '@/lib/api'
import type { StopField } from '@/types/reservation'

const QUESTION_BY_REASON: Record<string, string> = {
  ambiguous: '물어볼 말: "정확히 어느 정류장에서 타시나요?"',
  notFound: '물어볼 말: "정류장 이름으로 다시 한 번 말씀해 주시겠어요?"',
  conflict: '물어볼 말: "출발지와 도착지를 다시 확인해 주세요."',
  changed: '물어볼 말: "바뀐 내용을 다시 확인해 주세요."',
  missing: '물어볼 말: "어디서 타실 건가요?"',
}

export function StopFieldCard({
  label,
  fieldKey,
}: {
  label: string
  fieldKey: 'from' | 'to'
}) {
  const field = useCallStore((s) => s.fields[fieldKey])
  const selectStopCandidate = useCallStore((s) => s.selectStopCandidate)
  const stops = useCallStore((s) => s.stops)
  const stopById = useCallStore((s) => s.stopById)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')

  const state = fieldState(
    field.id !== null,
    field.needsCheck,
    field.resolvedBy,
  )
  const display = renderValue(field, stopById)

  const searchResults = query.trim()
    ? stops
        .filter(
          (s) =>
            s.name.includes(query.trim()) ||
            s.aliases.some((a) => a.includes(query.trim())),
        )
        .slice(0, 8)
    : []

  return (
    <FieldShell
      label={label}
      state={state}
      value={display}
      note={field.note}
      previousDisplay={field.previousDisplay}
    >
      {field.needsCheck && (
        <div className="mt-2 space-y-1 border-t border-[#E39A2D]/40 pt-2">
          {field.candidates.map((id) => {
            const stop = stopById[id]
            return (
              <label
                key={id}
                className="flex cursor-pointer items-center justify-between rounded border border-transparent px-2 py-1.5 text-sm hover:border-[#E39A2D]/60 hover:bg-white"
              >
                <span className="flex items-center gap-2">
                  <input
                    type="radio"
                    name={`${fieldKey}-candidate`}
                    checked={field.id === id}
                    onChange={() => selectStopCandidate(fieldKey, id)}
                  />
                  <span>
                    <span className="font-medium text-slate-800">
                      {stop?.name ?? id}
                    </span>
                    <span className="ml-1 text-xs text-slate-500">
                      말씀하신 '{field.said}'와 일치
                    </span>
                  </span>
                </span>
                <span className="text-xs text-slate-400">{id}</span>
              </label>
            )
          })}

          {!searchOpen ? (
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              className="mt-1 text-sm font-medium text-[#1C4E80] hover:underline"
            >
              + 목록에 없음 · 정류장 이름으로 찾기
            </button>
          ) : (
            <div className="mt-1 rounded border border-slate-200 bg-white p-2">
              <input
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="정류장 이름이나 별칭 입력"
                className="w-full rounded border border-slate-300 px-2 py-1 text-sm outline-none focus:border-[#1C4E80]"
              />
              <ul className="mt-1 max-h-40 overflow-auto">
                {searchResults.map((s) => (
                  <li key={s.id}>
                    <button
                      type="button"
                      onClick={() => {
                        selectStopCandidate(fieldKey, s.id)
                        setSearchOpen(false)
                        setQuery('')
                      }}
                      className="flex w-full items-center justify-between rounded px-2 py-1 text-left text-sm hover:bg-slate-100"
                    >
                      <span>{s.name}</span>
                      <span className="text-xs text-slate-400">{s.id}</span>
                    </button>
                  </li>
                ))}
                {query.trim() && searchResults.length === 0 && (
                  <li className="px-2 py-1 text-sm text-slate-400">
                    정류장 목록에 없습니다.
                  </li>
                )}
              </ul>
            </div>
          )}

          <p className="pt-1 text-xs text-[#9a6a1c]">
            {QUESTION_BY_REASON[field.reason ?? 'ambiguous']}
          </p>
        </div>
      )}
    </FieldShell>
  )
}

function renderValue(field: StopField, stopById: Record<string, Stop>) {
  if (field.id) return stopById[field.id]?.name ?? field.id
  if (field.said) return `${field.said} ?`
  return '아직 못 들음'
}
