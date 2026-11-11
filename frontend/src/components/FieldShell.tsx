// 예약 양식 칸 하나의 틀. 색과 테두리는 docsui/D-2(양식 칸의 4가지 상태)의 값을 그대로 썼다.
//   빈칸         : 회색 점선 테두리
//   확인 완료     : 옅은 파랑 배경 + 파랑 글자
//   확인 필요     : 옅은 주황 배경 + 굵은 주황 테두리 + 어두운 뱃지
//   상담원이 고름 : 흰 배경 + 초록 테두리 (결과적으로 확인 완료)
import type { ReactNode } from 'react'
import type { FieldState } from '@/types/reservation'

const STATE_STYLE: Record<
  FieldState,
  { wrap: string; badge: string | null; badgeText: string }
> = {
  empty: {
    wrap: 'border border-dashed border-gray-400 bg-white',
    badge: null,
    badgeText: '',
  },
  confirmed: {
    wrap: 'border border-[#B9CBE3] bg-[#E7EEF6]',
    badge: 'bg-[#1C4E80]/10 text-[#1C4E80]',
    badgeText: '✓ 확인 완료',
  },
  needsCheck: {
    wrap: 'border-[2.5px] border-[#E39A2D] bg-[#FFF3DF]',
    badge: 'bg-[#E39A2D] text-white',
    badgeText: '! 확인 필요',
  },
  agentConfirmed: {
    wrap: 'border-[1.5px] border-[#3E9467] bg-white',
    badge: 'bg-[#3E9467]/10 text-[#3E9467]',
    badgeText: '✓ 상담원 확인',
  },
}

export interface FieldShellProps {
  label: string
  state: FieldState
  /** 칸 안에 보여줄 값 (크게 표시). */
  value: ReactNode
  /** 근거·확인 필요 사유 한 줄. */
  note?: string | null
  /** 고객이 말을 바꿔 지워진 이전 값 (취소선). */
  previousDisplay?: string | null
  children?: ReactNode
  fixed?: boolean
}

export function FieldShell({
  label,
  state,
  value,
  note,
  previousDisplay,
  children,
  fixed,
}: FieldShellProps) {
  const style = STATE_STYLE[state]
  return (
    <div className={`rounded-lg p-3 transition-colors ${style.wrap}`}>
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-medium text-slate-600">{label}</span>
        {fixed ? (
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
            고정값
          </span>
        ) : (
          style.badge && (
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-semibold ${style.badge}`}
            >
              {style.badgeText}
            </span>
          )
        )}
      </div>
      <div className="mt-1 flex items-baseline gap-2">
        {previousDisplay && (
          <span className="text-base text-slate-400 line-through">
            {previousDisplay}
          </span>
        )}
        <span
          className={`text-lg font-bold ${state === 'empty' ? 'text-slate-400 font-normal' : 'text-slate-900'}`}
        >
          {value}
        </span>
      </div>
      {note && <p className="mt-0.5 text-xs text-slate-500">{note}</p>}
      {children}
    </div>
  )
}
