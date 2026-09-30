/** Fired after currencies are added, renamed or deleted, so other sections can reload at once. */
export const CURRENCIES_CHANGED = 'currencies-changed'

export function notifyCurrenciesChanged(): void {
  window.dispatchEvent(new Event(CURRENCIES_CHANGED))
}
