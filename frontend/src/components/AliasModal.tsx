// 별칭 추가 모달. 상담원이 고른 정류장이 고객이 한 말과 이름·별칭으로 안 맞을 때
// 서버가 aliasSuggestion을 주면 뜬다. 추가해도 바로 확정에 쓰이진 않고 승인 대기로 쌓인다.
import { useCallStore } from '@/store/callStore'

export function AliasModal() {
  const suggestion = useCallStore((s) => s.aliasSuggestion)
  const stopById = useCallStore((s) => s.stopById)
  const addAlias = useCallStore((s) => s.addAlias)
  const dismissAlias = useCallStore((s) => s.dismissAlias)

  if (!suggestion) return null
  const stopName = stopById[suggestion.stopId]?.name ?? suggestion.stopId

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl">
        <h3 className="font-bold text-slate-800">별칭으로 추가할까요?</h3>
        <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
          고객이 말한 <b>'{suggestion.said}'</b>를 <b>{stopName}</b>의 별칭으로
          기록합니다.
        </p>
        <p className="mt-2 text-xs text-slate-500">
          다음 통화부터 이 정류장이 후보 위쪽에 나옵니다. 승인되면 확인 완료로
          바로 채워집니다.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={dismissAlias}
            className="rounded border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-600"
          >
            안 함
          </button>
          <button
            type="button"
            onClick={addAlias}
            className="rounded bg-[#1C4E80] px-3 py-2 text-sm font-semibold text-white"
          >
            추가
          </button>
        </div>
      </div>
    </div>
  )
}
