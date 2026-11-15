import { useEffect } from 'react'
import { Header } from '@/components/Header'
import { ReservationPanel } from '@/components/ReservationPanel'
import { TranscriptPanel } from '@/components/TranscriptPanel'
import { useCallStore } from '@/store/callStore'

function App() {
  const loadStops = useCallStore((s) => s.loadStops)
  const error = useCallStore((s) => s.error)
  const clearError = useCallStore((s) => s.clearError)

  useEffect(() => {
    loadStops()
  }, [loadStops])

  return (
    <main className="min-h-screen bg-slate-100 p-4">
      <div className="mx-auto flex h-[calc(100vh-2rem)] max-w-6xl flex-col gap-4">
        <Header />
        {error && (
          <div
            role="alert"
            className="flex items-center justify-between rounded-lg border border-red-300 bg-red-50 px-4 py-2 text-sm text-red-700"
          >
            <span>{error}</span>
            <button
              type="button"
              onClick={clearError}
              className="text-xs font-semibold hover:underline"
            >
              닫기
            </button>
          </div>
        )}
        <div className="grid min-h-0 flex-1 auto-rows-[minmax(0,1fr)] grid-cols-1 gap-4 overflow-hidden md:grid-cols-2">
          <TranscriptPanel />
          <ReservationPanel />
        </div>
      </div>
    </main>
  )
}

export default App
