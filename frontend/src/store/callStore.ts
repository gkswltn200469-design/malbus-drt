// 통화 보조 화면의 상태 저장소. docsui/D-1(통화 보조 화면 기본)·D-4(확정 버튼)·
// D-5(확정 결과 화면)의 동작을 따른다. 추출·판정·저장은 백엔드가 하고(docs/api.md),
// 여기서는 응답을 받아 화면 상태로 바꿔 둔다.

import { create } from 'zustand'
import { DEMO_SCRIPTS } from '@/data/demoScripts'
import {
  api,
  ApiError,
  toFields,
  toServerTime,
  toTranscript,
  type AliasSuggestion,
  type ApiReservation,
  type Stop,
} from '@/lib/api'
import { checkReadyToConfirm, createEmptyFields } from '@/lib/fieldState'
import type {
  ReservationFields,
  ReservationStatus,
  Speaker,
  TranscriptLine,
} from '@/types/reservation'

const LINE_STEP_SECONDS = 4

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function clockOf(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

interface CallState {
  stops: Stop[]
  stopById: Record<string, Stop>
  reservationId: number | null
  agentName: string
  status: ReservationStatus
  fields: ReservationFields
  transcript: TranscriptLine[]
  callSeconds: number
  confirmModalOpen: boolean
  confirmedAt: string | null
  confirmedBy: string | null
  /** 확정 후 서버가 돌려준 예약 그대로 (D-5 JSON 화면) */
  saved: ApiReservation | null
  aliasSuggestion: AliasSuggestion | null
  error: string | null

  loadStops: () => Promise<void>
  setAgentName: (name: string) => void
  loadScript: (scriptId: number) => Promise<void>
  addManualLine: (speaker: Speaker, text: string) => Promise<void>
  selectStopCandidate: (key: 'from' | 'to', stopId: string) => Promise<void>
  overrideDate: (value: string) => Promise<void>
  overrideTime: (value: string) => Promise<void>
  openConfirmModal: () => void
  closeConfirmModal: () => void
  confirmReservation: () => Promise<void>
  addAlias: () => Promise<void>
  dismissAlias: () => void
  clearError: () => void
  nextCall: () => void
}

function freshCall() {
  return {
    reservationId: null,
    status: '확정 대기' as ReservationStatus,
    fields: createEmptyFields(),
    transcript: [] as TranscriptLine[],
    callSeconds: 0,
    confirmModalOpen: false,
    confirmedAt: null,
    confirmedBy: null,
    saved: null,
    aliasSuggestion: null,
    error: null,
  }
}

// 대본 재생 중에 다른 대본이나 다음 통화를 누르면 이전 재생을 멈추는 용도
let callToken = 0

export const useCallStore = create<CallState>((set, get) => {
  function apply(r: ApiReservation) {
    const { stopById } = get()
    const confirmed = r.status === 'CONFIRMED'
    set({
      reservationId: r.id,
      status: confirmed ? '확정' : '확정 대기',
      fields: toFields(r, (id) => stopById[id]?.name ?? id),
      transcript: toTranscript(r, (n) => formatElapsed(n * LINE_STEP_SECONDS)),
      callSeconds: r.transcript.length * LINE_STEP_SECONDS,
      confirmedAt: r.confirmedAt ? clockOf(r.confirmedAt) : null,
      confirmedBy: r.confirmedBy,
      saved: confirmed ? r : null,
    })
  }

  async function guard(fn: () => Promise<void>) {
    try {
      set({ error: null })
      await fn()
    } catch (e) {
      set({ error: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function ensureReservation(): Promise<number> {
    const id = get().reservationId
    if (id !== null) return id
    const r = await api.create()
    apply(r)
    return r.id
  }

  async function patch(name: string, value: string) {
    const id = get().reservationId
    if (id === null) return
    await guard(async () => {
      const res = await api.patchField(id, name, value)
      apply(res.reservation)
      set({ aliasSuggestion: res.aliasSuggestion })
    })
  }

  return {
    stops: [],
    stopById: {},
    agentName: '',
    ...freshCall(),

    loadStops: () =>
      guard(async () => {
        const stops = await api.stops()
        set({
          stops,
          stopById: Object.fromEntries(stops.map((s) => [s.id, s])),
        })
      }),

    setAgentName: (name) => set({ agentName: name }),

    loadScript: async (scriptId) => {
      const script = DEMO_SCRIPTS.find((s) => s.id === scriptId)
      if (!script) return
      const token = ++callToken
      set(freshCall())
      await guard(async () => {
        let r = await api.create(toServerTime(script.receivedAt))
        for (const line of script.lines) {
          if (token !== callToken) return
          apply(r)
          r = await api.addLine(r.id, line.speaker, line.text)
        }
        if (token === callToken) apply(r)
      })
    },

    addManualLine: async (speaker, text) => {
      const trimmed = text.trim()
      if (!trimmed) return
      await guard(async () => {
        const id = await ensureReservation()
        apply(await api.addLine(id, speaker, trimmed))
      })
    },

    selectStopCandidate: (key, stopId) => patch(key, stopId),
    overrideDate: (value) => patch('date', value),
    overrideTime: (value) => patch('time', value),

    openConfirmModal: () => {
      if (checkReadyToConfirm(get().fields).ready)
        set({ confirmModalOpen: true })
    },

    closeConfirmModal: () => set({ confirmModalOpen: false }),

    confirmReservation: async () => {
      const id = get().reservationId
      if (id === null) return
      const agentName = get().agentName.trim() || '상담원'
      set({ confirmModalOpen: false })
      await guard(async () => apply(await api.confirm(id, agentName)))
    },

    addAlias: async () => {
      const { aliasSuggestion, reservationId } = get()
      if (!aliasSuggestion || reservationId === null) return
      set({ aliasSuggestion: null })
      await guard(async () => {
        await api.addAlias(
          aliasSuggestion.said,
          aliasSuggestion.stopId,
          reservationId,
        )
      })
    },

    dismissAlias: () => set({ aliasSuggestion: null }),
    clearError: () => set({ error: null }),

    nextCall: () => {
      callToken++
      set(freshCall())
    },
  }
})
