import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './msw/server'

// jsdom does not implement <dialog>'s modal API. This minimal stand-in follows the spec's
// observable behaviour that the app relies on: showModal() sets `open`, close() removes it and
// fires "close", and Esc fires "cancel" and then closes (unless cancel is prevented).
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || !this.open) return
      const cancel = new Event('cancel', { cancelable: true })
      if (this.dispatchEvent(cancel)) this.close()
    }
    this.addEventListener('keydown', onKey)
    this.addEventListener('close', () => this.removeEventListener('keydown', onKey), { once: true })
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.open) return
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}

// Any request without a mock handler fails the test: tests never reach a real backend or the internet.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())
