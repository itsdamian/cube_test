import type { EventSourceLike } from '../live/liveStream'

/** Test double for the browser's EventSource: the test decides which events "arrive". */
export class FakeEventSource implements EventSourceLike {
  static instances: FakeEventSource[] = []

  readyState = 0
  onopen: EventSourceLike['onopen'] = null
  onerror: EventSourceLike['onerror'] = null
  closed = false
  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>()

  readonly url: string

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  static factory = (url: string) => new FakeEventSource(url)

  static latest(): FakeEventSource {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1]
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close(): void {
    this.closed = true
    this.readyState = 2
  }

  /** Simulate the server sending `event: <type>` with JSON data. */
  emit(type: string, data: unknown): void {
    const event = new MessageEvent(type, { data: JSON.stringify(data) })
    this.listeners.get(type)?.forEach((listener) => listener(event))
  }

  open(): void {
    this.readyState = 1
    this.onopen?.call(this as unknown as EventSource, new Event('open'))
  }

  fail(): void {
    this.readyState = 0
    this.onerror?.call(this as unknown as EventSource, new Event('error'))
  }
}
