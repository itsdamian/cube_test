import type { Direction } from '../api/types'
import { formatUsd } from '../format/format'

export const DIRECTION_LABEL: Record<Direction, string> = { ABOVE: '高於', BELOW: '低於' }

export function describeAlert(direction: Direction, threshold: number): string {
  return `BTC-USD ${DIRECTION_LABEL[direction]} ${formatUsd(threshold)}`
}
