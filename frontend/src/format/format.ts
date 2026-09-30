/** Display helpers (Traditional Chinese / Taiwan formatting). */

const usd = new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function formatUsd(value: number): string {
  return usd.format(value)
}

/**
 * zh-TW shows the New Taiwan dollar as a bare "$". Next to "US$" that is ambiguous, so the
 * symbol is spelled out (team lead decision, task 31).
 */
const SYMBOLS: Record<string, string> = { TWD: 'NT$' }

/** A price in any currency; JPY/TWD-style currencies still get 2 decimals for BTC prices. */
export function formatMoney(value: number, currency: string): string {
  try {
    const parts = new Intl.NumberFormat('zh-TW', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).formatToParts(value)
    const symbol = SYMBOLS[currency]
    return parts.map((p) => (p.type === 'currency' && symbol ? symbol : p.value)).join('')
  } catch {
    // unknown ISO code (user-defined currency): plain number + code
    return `${value.toLocaleString('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`
  }
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-TW', { hour12: false })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('zh-TW', { hour12: false })
}

const SOURCE_NAMES: Record<string, string> = { coinbase: 'Coinbase', kraken: 'Kraken' }

export function sourceName(source: string): string {
  return SOURCE_NAMES[source] ?? source
}

// application.yml fixes the primary / backup exchanges by name (app.feed.primary.name = coinbase,
// app.feed.backup.name = kraken); they are not environment-configurable.
const SOURCE_ROLES: Record<string, string> = { coinbase: '主要', kraken: '備援' }

/** 主要 / 備援, or null for an unknown source. */
export function sourceRole(source: string): string | null {
  return SOURCE_ROLES[source] ?? null
}
