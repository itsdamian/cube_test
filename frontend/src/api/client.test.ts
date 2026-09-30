import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/msw/server'
import { samples } from '../test/msw/samples'
import { api, ApiError } from './client'

describe('api client (against the backend contract samples)', () => {
  it('reads the latest price with its feed status', async () => {
    const latest = await api.latestPrice()
    expect(latest.pair).toBe('BTC-USD')
    expect(latest.price).toBe(84045.5)
    expect(latest.status?.state).toBe('LIVE')
  })

  it('reads converted prices, including a currency without a rate', async () => {
    const converted = await api.convertedPrices()
    expect(converted.items.find((i) => i.code === 'TWD')?.name).toBe('新台幣')
    expect(converted.items.find((i) => i.code === 'XAU')?.rate).toBeNull()
    expect(converted.rateSource).toContain('Exchange Rate API')
  })

  it('sends query parameters for candles', async () => {
    let url = ''
    server.use(http.get('/api/candles', ({ request }) => {
      url = request.url
      return HttpResponse.json(samples.candles)
    }))
    const candles = await api.candles('5m', '2026-09-29T08:00:00Z')
    expect(new URL(url).searchParams.get('interval')).toBe('5m')
    expect(new URL(url).searchParams.get('from')).toBe('2026-09-29T08:00:00Z')
    expect(new URL(url).searchParams.has('to')).toBe(false)
    expect(candles[0].open).toBe(84040.1)
  })

  it('turns a validation ProblemDetail into an ApiError with field messages', async () => {
    server.use(http.post('/api/currencies', () =>
      HttpResponse.json(samples.currencyValidationError, { status: 400 })))
    const error = await api.createCurrency({ code: 'usd', name: '' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).fieldErrors().code).toContain('3 uppercase letters')
    expect((error as ApiError).fieldErrors().name).toBeDefined()
  })

  it('returns undefined for 204 responses', async () => {
    server.use(http.delete('/api/alerts/3', () => new HttpResponse(null, { status: 204 })))
    await expect(api.deleteAlert(3)).resolves.toBeUndefined()
  })
})
