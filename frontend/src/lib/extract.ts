// 통화 내용(고객 발화)에서 예약 정보를 뽑아내는 규칙 기반 엔진.
// API 키가 없어도 동작하는 데모 모드이며, prototype/engine.js의 정규식 방식을 참고해
// docs/기능규칙.md 5장·7장 기준에 맞게 새로 작성했다.
//
// 설계 원칙 (기능규칙.md 7.3 "정정"):
//   같은 통화에서 여러 번 말이 나오면 가장 마지막(가장 오른쪽) 표현을 따른다.
//   "아홉 시... 아니다 열 시 반" 처럼 한 줄 안에서 바뀌어도, 통화 전체를 이어 붙인
//   텍스트에서 정규식으로 모두 찾은 뒤 마지막 것을 쓰면 같은 규칙으로 처리된다.

import type { StopInfo } from '@/data/stops'
import { NUMBER_WORD_PATTERN, wordToNumber } from '@/lib/koreanNumbers'
import { addDays, formatISODate } from '@/lib/dateFormat'

export interface StopAliasIndex {
  map: Map<string, Set<string>>
  sortedKeys: string[]
}

export function buildAliasIndex(stops: StopInfo[]): StopAliasIndex {
  const map = new Map<string, Set<string>>()
  const add = (alias: string, id: string) => {
    const a = alias.trim()
    if (!a) return
    if (!map.has(a)) map.set(a, new Set())
    map.get(a)!.add(id)
  }
  for (const s of stops) {
    add(s.name, s.id)
    for (const a of s.alias) add(a, s.id)
  }
  const sortedKeys = [...map.keys()].sort((a, b) => b.length - a.length)
  return { map, sortedKeys }
}

type StopRole = 'from' | 'to' | 'place'

interface StopMention {
  role: StopRole
  said: string
  candidates: string[]
  index: number
}

// "동아백화점 수성점 앞까지"처럼 정류장 이름과 조사 사이에 꾸밈말이 끼어드는 경우가 있어,
// 쉼표·마침표를 넘지 않는 선에서 조금 떨어진 조사도 같은 말로 본다.
const FROM_ROLE_RE = /^[^,.!?]{0,10}에서/
const TO_ROLE_RE = /^[^,.!?]{0,10}(까지|으로|로|에\s*가|가|갈)/

/**
 * 긴 별칭부터 찾고, 찾은 자리는 공백으로 지워서 짧은 별칭이 같은 자리에 겹쳐
 * 잡히지 않게 한다. 뒤 문맥으로 출발("~에서")·도착("~까지/으로/가")을 가른다.
 */
function findStopMentions(text: string, index: StopAliasIndex): StopMention[] {
  const mentions: StopMention[] = []
  let masked = text
  for (const alias of index.sortedKeys) {
    let i = masked.indexOf(alias)
    while (i !== -1) {
      const after = masked.slice(i + alias.length)
      const role: StopRole = FROM_ROLE_RE.test(after)
        ? 'from'
        : TO_ROLE_RE.test(after)
          ? 'to'
          : 'place'
      mentions.push({
        role,
        said: alias,
        candidates: [...index.map.get(alias)!],
        index: i,
      })
      masked =
        masked.slice(0, i) +
        ' '.repeat(alias.length) +
        masked.slice(i + alias.length)
      i = masked.indexOf(alias)
    }
  }
  return mentions.sort((a, b) => a.index - b.index)
}

export interface StopExtraction {
  said: string
  candidates: string[]
}

export interface DateExtraction {
  raw: string
  value: string // YYYY-MM-DD
}

export interface TimeExtraction {
  raw: string
  value: string // HH:MM
  /** 오전/오후를 말하지 않아 AI가 임의로 정하지 않고 둔 제안값. */
  ambiguous: boolean
}

export interface PeopleExtraction {
  raw: string
  value: number
}

export interface ExtractResult {
  from: StopExtraction | null
  to: StopExtraction | null
  date: DateExtraction | null
  time: TimeExtraction | null
  people: PeopleExtraction | null
}

const DATE_WORD_OFFSET: Record<string, number> = { 오늘: 0, 내일: 1, 모레: 2 }

export function extractFields(
  customerText: string,
  receivedAt: Date,
  aliasIndex: StopAliasIndex,
): ExtractResult {
  const text = customerText.trim()

  const mentions = findStopMentions(text, aliasIndex)
  const fromMentions = mentions.filter((m) => m.role === 'from')
  const toMentions = mentions.filter((m) => m.role === 'to')
  const from = fromMentions.length
    ? {
        said: fromMentions.at(-1)!.said,
        candidates: fromMentions.at(-1)!.candidates,
      }
    : null
  const to = toMentions.length
    ? {
        said: toMentions.at(-1)!.said,
        candidates: toMentions.at(-1)!.candidates,
      }
    : null

  // 날짜: "오늘/내일/모레"만 규칙으로 실제 날짜로 바꾼다 (기능규칙 5.3).
  let date: DateExtraction | null = null
  const dateMatches = [...text.matchAll(/오늘|내일|모레/g)]
  if (dateMatches.length) {
    const raw = dateMatches.at(-1)![0]
    date = {
      raw,
      value: formatISODate(addDays(receivedAt, DATE_WORD_OFFSET[raw])),
    }
  }

  // 시간: 24시간제로 바꾸되, 오전/오후를 안 말했으면 AI가 정하지 않고 "모호"로 남긴다.
  let time: TimeExtraction | null = null
  const timeRe = new RegExp(
    `(오전|오후|아침|저녁|낮|밤)?\\s*${NUMBER_WORD_PATTERN}\\s*시\\s*(반|${NUMBER_WORD_PATTERN}\\s*분)?`,
    'g',
  )
  const timeMatches = [...text.matchAll(timeRe)]
  if (timeMatches.length) {
    const last = timeMatches.at(-1)!
    const hourWord = last[2]
    const hour0 = hourWord ? wordToNumber(hourWord) : null
    if (hour0 !== null) {
      const minuteWord = last[4]
      const minutes =
        last[3] === '반' ? 30 : minuteWord ? (wordToNumber(minuteWord) ?? 0) : 0
      const part = last[1]
      let hour = hour0
      let ambiguous = false
      if (part === '오후' || part === '저녁' || part === '밤') {
        if (hour < 12) hour += 12
      } else if (part === '오전' || part === '아침') {
        if (hour === 12) hour = 0
      } else {
        ambiguous = true
      }
      if (hour >= 0 && hour <= 23 && minutes >= 0 && minutes <= 59) {
        time = {
          raw: last[0].trim(),
          value: `${String(hour).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`,
          ambiguous,
        }
      }
    }
  }

  // 인원: 숫자(명·사람), "둘이/셋이/넷이", "혼자"를 모두 보고 마지막 것을 쓴다.
  // 나이 같은 관계없는 숫자는 "명/사람/이"가 바로 뒤에 없으면 잡히지 않는다 (기능규칙 7.2).
  const peopleCandidates: Array<{ index: number; raw: string; value: number }> =
    []
  for (const m of text.matchAll(
    new RegExp(`${NUMBER_WORD_PATTERN}\\s*(명|사람)`, 'g'),
  )) {
    const n = wordToNumber(m[1])
    if (n !== null)
      peopleCandidates.push({ index: m.index, raw: m[0], value: n })
  }
  for (const m of text.matchAll(/(둘|셋|넷)이/g)) {
    const map: Record<string, number> = { 둘: 2, 셋: 3, 넷: 4 }
    peopleCandidates.push({ index: m.index, raw: m[0], value: map[m[1]] })
  }
  for (const m of text.matchAll(/저\s*혼자|혼자/g)) {
    peopleCandidates.push({ index: m.index, raw: m[0], value: 1 })
  }
  let people: PeopleExtraction | null = null
  if (peopleCandidates.length) {
    peopleCandidates.sort((a, b) => a.index - b.index)
    const last = peopleCandidates.at(-1)!
    people = { raw: last.raw, value: last.value }
  }

  return { from, to, date, time, people }
}
