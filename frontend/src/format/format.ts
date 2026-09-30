/** Display helpers (Traditional Chinese / Taiwan formatting). */

const usd = new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function formatUsd(value: number): string {
  return usd.format(value)
}

/** A price in any currency; JPY/TWD-style currencies still get 2 decimals for BTC prices. */
export function formatMoney(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('zh-TW', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
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
