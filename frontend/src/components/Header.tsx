// 위쪽 상태 바. docsui/D-0(상담원 화면 예시)의 느낌을 참고했다.
// 키 없이 동작하는 규칙 기반 데모 모드라는 것을 항상 보여 준다 (CLAUDE.md 확정 결정).
import { useCallStore } from '@/store/callStore'

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function Header() {
  const reservationId = useCallStore((s) => s.reservationId)
  const status = useCallStore((s) => s.status)
  const callSeconds = useCallStore((s) => s.callSeconds)
  const agentName = useCallStore((s) => s.agentName)
  const setAgentName = useCallStore((s) => s.setAgentName)

  return (
    <header className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-900 px-4 py-3 text-white">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-lg">🚌</span>
        <h1 className="font-bold">말로 부르는 버스 · 상담원 보조</h1>
        <span className="rounded-full bg-white/10 px-2 py-1 text-xs">
          예약 #{reservationId} · 상담원 통화
        </span>
        <span className="rounded-full bg-white/10 px-2 py-1 text-xs">
          상태: {status}
        </span>
        <span className="rounded-full bg-amber-400/20 px-2 py-1 text-xs text-amber-200">
          AI 모드 (키 없으면 규칙 기반 데모 모드)
        </span>
      </div>
      <div className="flex items-center gap-2 text-xs text-slate-300">
        <span>상담원</span>
        <input
          value={agentName}
          onChange={(e) => setAgentName(e.target.value)}
          placeholder="이름 입력"
          className="w-20 rounded border border-white/20 bg-white/10 px-2 py-1 text-white outline-none placeholder:text-slate-400"
        />
        <span className="h-2 w-2 rounded-full bg-emerald-400" />
        <span>{formatElapsed(callSeconds)}</span>
      </div>
    </header>
  )
}
