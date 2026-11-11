// 통화 보조 화면의 상태 저장소. docsui/D-1(통화 보조 화면 기본)·D-4(확정 버튼)·
// D-5(확정 결과 화면)의 동작을 그대로 구현한다. 지금은 프론트엔드 단독 데모라서
// 메모리 상태로만 있고 새로고침하면 사라진다 (강대운 예약 API 연동 전 자리표시).

import { create } from 'zustand'
import { STOPS } from '@/data/stops'
import { DEMO_SCRIPTS } from '@/data/demoScripts'
import { buildAliasIndex, extractFields } from '@/lib/extract'
import {
  applyExtraction,
  checkReadyToConfirm,
  createEmptyFields,
  overrideDate as overrideDateField,
  overridePeople as overridePeopleField,
  overrideTime as overrideTimeField,
  selectStop,
} from '@/lib/resolveFields'
import type {
  ReservationFields,
  ReservationStatus,
  Speaker,
  TranscriptLine,
} from '@/types/reservation'

const ALIAS_INDEX = buildAliasIndex(STOPS)
const LINE_STEP_SECONDS = 4

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function nowClock(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

interface CallState {
  reservationId: number
  agentName: string
  status: ReservationStatus
  receivedAt: string // ISO, "내일/모레" 변환 기준
  fields: ReservationFields
  transcript: TranscriptLine[]
  callSeconds: number
  confirmModalOpen: boolean
  confirmedAt: string | null
  confirmedBy: string | null

  setAgentName: (name: string) => void
  loadScript: (scriptId: number) => void
  addManualLine: (speaker: Speaker, text: string) => void
  selectStopCandidate: (key: 'from' | 'to', stopId: string) => void
  overrideDate: (value: string) => void
  overrideTime: (value: string) => void
  overridePeople: (value: number) => void
  openConfirmModal: () => void
  closeConfirmModal: () => void
  confirmReservation: () => void
  nextCall: () => void
}

function rerunExtraction(
  transcript: TranscriptLine[],
  fields: ReservationFields,
  receivedAtIso: string,
): ReservationFields {
  const customerText = transcript
    .filter((l) => l.speaker === '고객')
    .map((l) => l.text)
    .join(' ')
  const extraction = extractFields(
    customerText,
    new Date(receivedAtIso.replace(' ', 'T')),
    ALIAS_INDEX,
  )
  return applyExtraction(
    fields,
    extraction,
    new Date(receivedAtIso.replace(' ', 'T')),
  )
}

function freshState(reservationId: number, agentName: string) {
  return {
    reservationId,
    agentName,
    status: '확정 대기' as ReservationStatus,
    receivedAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
    fields: createEmptyFields(),
    transcript: [] as TranscriptLine[],
    callSeconds: 0,
    confirmModalOpen: false,
    confirmedAt: null,
    confirmedBy: null,
  }
}

export const useCallStore = create<CallState>((set, get) => ({
  ...freshState(1, ''),

  setAgentName: (name) => set({ agentName: name }),

  loadScript: (scriptId) => {
    const script = DEMO_SCRIPTS.find((s) => s.id === scriptId)
    if (!script) return
    const { reservationId, agentName } = get()
    set({
      ...freshState(reservationId, agentName),
      receivedAt: script.receivedAt,
    })
    let seconds = 0
    for (const line of script.lines) {
      seconds += LINE_STEP_SECONDS
      const transcriptLine: TranscriptLine = {
        id: crypto.randomUUID(),
        time: formatElapsed(seconds),
        speaker: line.speaker,
        text: line.text,
      }
      set((state) => {
        const transcript = [...state.transcript, transcriptLine]
        const fields =
          line.speaker === '고객'
            ? rerunExtraction(transcript, state.fields, state.receivedAt)
            : state.fields
        return { transcript, fields, callSeconds: seconds }
      })
    }
  },

  addManualLine: (speaker, text) => {
    const trimmed = text.trim()
    if (!trimmed) return
    set((state) => {
      const seconds = state.callSeconds + LINE_STEP_SECONDS
      const line: TranscriptLine = {
        id: crypto.randomUUID(),
        time: formatElapsed(seconds),
        speaker,
        text: trimmed,
      }
      const transcript = [...state.transcript, line]
      const fields =
        speaker === '고객'
          ? rerunExtraction(transcript, state.fields, state.receivedAt)
          : state.fields
      return { transcript, fields, callSeconds: seconds }
    })
  },

  selectStopCandidate: (key, stopId) =>
    set((state) => ({ fields: selectStop(state.fields, key, stopId) })),

  overrideDate: (value) =>
    set((state) => ({
      fields: overrideDateField(
        state.fields,
        value,
        new Date(state.receivedAt.replace(' ', 'T')),
      ),
    })),

  overrideTime: (value) =>
    set((state) => ({ fields: overrideTimeField(state.fields, value) })),

  overridePeople: (value) =>
    set((state) => ({ fields: overridePeopleField(state.fields, value) })),

  openConfirmModal: () => {
    const { ready } = checkReadyToConfirm(get().fields)
    if (ready) set({ confirmModalOpen: true })
  },

  closeConfirmModal: () => set({ confirmModalOpen: false }),

  confirmReservation: () => {
    const { ready } = checkReadyToConfirm(get().fields)
    if (!ready) return
    const agentName = get().agentName.trim() || '상담원'
    const seconds = get().callSeconds + 2
    const confirmLine: TranscriptLine = {
      id: crypto.randomUUID(),
      time: formatElapsed(seconds),
      speaker: '기록',
      text: `예약 #${get().reservationId} 확정 · 상담원 ${agentName}`,
    }
    set((state) => ({
      status: '확정',
      confirmModalOpen: false,
      confirmedAt: nowClock(),
      confirmedBy: agentName,
      transcript: [...state.transcript, confirmLine],
      callSeconds: seconds,
    }))
  },

  nextCall: () => {
    const { reservationId, agentName } = get()
    set(freshState(reservationId + 1, agentName))
  },
}))
