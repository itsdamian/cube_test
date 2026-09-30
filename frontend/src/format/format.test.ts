import { describe, expect, it } from 'vitest'
import { formatMoney, formatUsd } from './format'

describe('money formatting (task 31)', () => {
  it('spells out NT$ for TWD so it cannot be confused with US$', () => {
    expect(formatMoney(2676068.39, 'TWD')).toBe('NT$2,676,068.39')
    expect(formatMoney(-1234.5, 'TWD')).toBe('-NT$1,234.50')
  })

  it('keeps US$ for USD and the locale symbol for other currencies, always with 2 decimals', () => {
    expect(formatUsd(84045.5)).toBe('US$84,045.50')
    expect(formatMoney(84045.5, 'USD')).toBe('US$84,045.50')
    expect(formatMoney(13096534.289, 'JPY')).toBe('¥13,096,534.29')
    expect(formatMoney(73352.35, 'EUR')).toBe('€73,352.35')
  })

  it('falls back to "number CODE" for a code Intl does not know', () => {
    expect(formatMoney(12.5, 'XX1')).toBe('12.50 XX1')
  })
})
