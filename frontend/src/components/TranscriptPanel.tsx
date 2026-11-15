// 왼쪽 패널 · 통화 내용. docsui/D-1(통화 보조 화면 기본)의 동작 규칙을 따른다:
// 한 줄이 맨 아래로 쌓이고, 실제 전화망 대신 대본 불러오기·한 줄 입력으로 통화를 재현한다.
// 음성 받아쓰기는 선택 기능이라 이번 범위에서는 자리만 둔다.
import { useEffect, useRef, useState } from 'react'
import { DEMO_SCRIPTS } from '@/data/demoScripts'
import { useCallStore } from '@/store/callStore'
import type { Speaker } from '@/types/reservation'

const SPEAKER_BADGE: Record<Speaker, string> = {
  상담원: 'bg-slate-100 text-slate-600',
  고객: 'bg-indigo-100 text-indigo-700',
  기록: 'bg-[#3E9467]/10 text-[#3E9467]',
}

export function TranscriptPanel() {
  const transcript = useCallStore((s) => s.transcript)
  const loadScript = useCallStore((s) => s.loadScript)
  const addManualLine = useCallStore((s) => s.addManualLine)
  const status = useCallStore((s) => s.status)

  const [scriptPickerOpen, setScriptPickerOpen] = useState(false)
  const [speaker, setSpeaker] = useState<Speaker>('고객')
  const [text, setText] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [transcript.length])

  function submitLine() {
    if (!text.trim()) return
    addManualLine(speaker, text)
    setText('')
  }

  return (
    <section className="flex h-full flex-col rounded-xl border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h2 className="font-bold text-slate-800">통화 내용</h2>
        <span className="text-xs text-slate-400">한 줄씩 아래로 쌓임</span>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-slate-200 px-4 py-2">
        <div className="relative">
          <button
            type="button"
            onClick={() => setScriptPickerOpen((v) => !v)}
            className="rounded-full bg-slate-800 px-3 py-1 text-xs font-semibold text-white"
          >
            대본 불러오기
          </button>
          {scriptPickerOpen && (
            <div className="absolute left-0 z-10 mt-1 max-h-72 w-72 overflow-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg">
              {DEMO_SCRIPTS.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => {
                    loadScript(s.id)
                    setScriptPickerOpen(false)
                  }}
                  className="flex w-full items-center justify-between rounded px-2 py-1.5 text-left text-sm hover:bg-slate-100"
                >
                  <span>
                    {s.id}. {s.type}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
        <span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">
          한 줄 입력
        </span>
        <span
          className="rounded-full bg-slate-50 px-3 py-1 text-xs text-slate-400"
          title="선택 기능 · 이번 범위에서는 구현하지 않음"
        >
          음성 받아쓰기 · 선택 기능(미구현)
        </span>
      </div>

      <div ref={listRef} className="flex-1 space-y-2 overflow-y-auto px-4 py-3">
        {transcript.length === 0 && (
          <p className="pt-8 text-center text-sm text-slate-400">
            대본을 불러오거나, 아래에서 한 줄씩 입력해 통화를 시작하세요.
          </p>
        )}
        {transcript.map((line) => (
          <div key={line.id} className="flex items-start gap-2 text-sm">
            <span className="w-10 shrink-0 pt-0.5 text-xs text-slate-400">
              {line.time}
            </span>
            <span
              className={`shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold ${SPEAKER_BADGE[line.speaker]}`}
            >
              {line.speaker}
            </span>
            <span className="text-slate-800">{line.text}</span>
          </div>
        ))}
      </div>

      <div className="flex items-center gap-2 border-t border-slate-200 p-3">
        <select
          value={speaker}
          onChange={(e) => setSpeaker(e.target.value as Speaker)}
          className="rounded border border-slate-300 px-2 py-2 text-sm"
          disabled={status === '확정'}
        >
          <option value="고객">고객 ▾</option>
          <option value="상담원">상담원 ▾</option>
        </select>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submitLine()}
          placeholder="말한 내용을 입력하고 Enter"
          disabled={status === '확정'}
          className="flex-1 rounded border border-slate-300 px-3 py-2 text-sm outline-none focus:border-[#1C4E80] disabled:bg-slate-50"
        />
        <button
          type="button"
          onClick={submitLine}
          disabled={status === '확정'}
          className="rounded bg-slate-800 px-3 py-2 text-sm font-semibold text-white disabled:opacity-40"
        >
          추가
        </button>
      </div>
    </section>
  )
}
