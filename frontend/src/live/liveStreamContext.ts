import { createContext, useContext } from 'react'
import type { PriceEvent } from '../api/types'
import type { AlertListener, LiveState } from './liveStream'

export interface LiveStreamContextValue extends LiveState {
  /** Receive every "alert" event; returns an unsubscribe function. */
  subscribeAlerts: (listener: AlertListener) => () => void
  /** Receive every "price" event (e.g. to extend a chart); returns an unsubscribe function. */
  subscribePrices: (listener: (price: PriceEvent) => void) => () => void
}

export const LiveStreamContext = createContext<LiveStreamContextValue | null>(null)

/** The live stream state; must be used inside `<LiveStreamProvider>`. */
export function useLiveStream(): LiveStreamContextValue {
  const value = useContext(LiveStreamContext)
  if (!value) {
    throw new Error('useLiveStream must be used inside <LiveStreamProvider>')
  }
  return value
}
