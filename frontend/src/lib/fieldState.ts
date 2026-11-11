import type { FieldState } from '@/types/reservation'

export function fieldState(
  hasValue: boolean,
  needsCheck: boolean,
  resolvedBy: 'auto' | 'agent' | null,
): FieldState {
  if (needsCheck) return 'needsCheck'
  if (!hasValue && !resolvedBy) return 'empty'
  if (resolvedBy === 'agent') return 'agentConfirmed'
  return 'confirmed'
}
