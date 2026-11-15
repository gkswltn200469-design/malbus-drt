import { Header } from '@/components/Header'
import { ReservationPanel } from '@/components/ReservationPanel'
import { TranscriptPanel } from '@/components/TranscriptPanel'

function App() {
  return (
    <main className="min-h-screen bg-slate-100 p-4">
      <div className="mx-auto flex h-[calc(100vh-2rem)] max-w-6xl flex-col gap-4">
        <Header />
        <div className="grid flex-1 grid-cols-1 gap-4 overflow-hidden md:grid-cols-2">
          <TranscriptPanel />
          <ReservationPanel />
        </div>
      </div>
    </main>
  )
}

export default App
