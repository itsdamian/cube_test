import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Currency, CurrencyInput } from '../api/types'
import { samples } from '../test/msw/samples'
import { server } from '../test/msw/server'
import { CurrencyManager } from './CurrencyManager'
import { CURRENCIES_CHANGED } from './events'

/** A tiny in-memory backend so the list really reflects what was changed. */
function fakeBackend() {
  let store: Currency[] = structuredClone(samples.currencies)
  let nextId = 100
  const calls: { method: string; path: string; body?: CurrencyInput }[] = []
  server.use(
    http.get('/api/currencies', () => HttpResponse.json(store)),
    http.post('/api/currencies', async ({ request }) => {
      const body = (await request.json()) as CurrencyInput
      calls.push({ method: 'POST', path: '/api/currencies', body })
      if (store.some((c) => c.code === body.code)) {
        return HttpResponse.json({ status: 409, detail: `Currency code ${body.code} already exists` }, { status: 409 })
      }
      const created = { ...body, id: nextId++, createdAt: '2026-09-30T00:00:00Z', updatedAt: '2026-09-30T00:00:00Z' }
      store = [...store, created]
      return HttpResponse.json(created, { status: 201 })
    }),
    http.put('/api/currencies/:id', async ({ request, params }) => {
      const body = (await request.json()) as CurrencyInput
      calls.push({ method: 'PUT', path: `/api/currencies/${params.id}`, body })
      store = store.map((c) => (c.id === Number(params.id) ? { ...c, ...body } : c))
      return HttpResponse.json(store.find((c) => c.id === Number(params.id)))
    }),
    http.delete('/api/currencies/:id', ({ params }) => {
      calls.push({ method: 'DELETE', path: `/api/currencies/${params.id}` })
      store = store.filter((c) => c.id !== Number(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return calls
}

describe('CurrencyManager (AC11 frontend)', () => {
  let calls: ReturnType<typeof fakeBackend>
  let changes = 0
  const countChange = () => changes++

  beforeEach(() => {
    calls = fakeBackend()
    changes = 0
    window.addEventListener(CURRENCIES_CHANGED, countChange)
  })

  afterEach(() => {
    window.removeEventListener(CURRENCIES_CHANGED, countChange)
  })

  it('lists currencies with code and Chinese name', async () => {
    render(<CurrencyManager />)
    expect(within(await screen.findByTestId('currency-TWD')).getByText('新台幣')).toBeInTheDocument()
    expect(within(screen.getByTestId('currency-EUR')).getByText('歐元')).toBeInTheDocument()
  })

  it('adds a currency (code upper-cased) and shows it in the list', async () => {
    const user = userEvent.setup()
    render(<CurrencyManager />)
    const form = await screen.findByRole('form', { name: '新增' })

    await user.type(within(form).getByLabelText('代碼'), 'chf')
    await user.type(within(form).getByLabelText('中文名稱'), '瑞士法郎')
    await user.click(within(form).getByRole('button', { name: '新增' }))

    expect(within(await screen.findByTestId('currency-CHF')).getByText('瑞士法郎')).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'POST', path: '/api/currencies', body: { code: 'CHF', name: '瑞士法郎' } })
    expect(within(form).getByLabelText('代碼')).toHaveValue('')   // form cleared
    expect(changes).toBe(1)                                      // conversion table is told to reload
  })

  it('renames a currency', async () => {
    const user = userEvent.setup()
    render(<CurrencyManager />)
    await user.click(await screen.findByRole('button', { name: '編輯 TWD' }))

    const form = screen.getByRole('form', { name: '儲存' })
    const name = within(form).getByLabelText('中文名稱')
    await user.clear(name)
    await user.type(name, '台幣')
    await user.click(within(form).getByRole('button', { name: '儲存' }))

    expect(within(await screen.findByTestId('currency-TWD')).getByText('台幣')).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'PUT', path: '/api/currencies/4', body: { code: 'TWD', name: '台幣' } })
  })

  it('deletes a currency after an explicit confirmation', async () => {
    const user = userEvent.setup()
    render(<CurrencyManager />)
    await user.click(await screen.findByRole('button', { name: '刪除 EUR' }))
    expect(calls).toHaveLength(0)                                // first click only asks

    await user.click(screen.getByRole('button', { name: '確定刪除？' }))

    await waitFor(() => expect(screen.queryByTestId('currency-EUR')).not.toBeInTheDocument())
    expect(calls).toContainEqual({ method: 'DELETE', path: '/api/currencies/1' })
  })

  it('shows 代碼已存在 when the code is taken (409)', async () => {
    const user = userEvent.setup()
    render(<CurrencyManager />)
    const form = await screen.findByRole('form', { name: '新增' })
    await user.type(within(form).getByLabelText('代碼'), 'TWD')
    await user.type(within(form).getByLabelText('中文名稱'), '重複')
    await user.click(within(form).getByRole('button', { name: '新增' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('代碼已存在')
  })

  it('shows the backend validation message next to each field (400)', async () => {
    server.use(http.post('/api/currencies', () =>
      HttpResponse.json(samples.currencyValidationError, { status: 400 })))
    const user = userEvent.setup()
    render(<CurrencyManager />)
    const form = await screen.findByRole('form', { name: '新增' })
    await user.type(within(form).getByLabelText('代碼'), 'US1')
    await user.type(within(form).getByLabelText('中文名稱'), 'x')
    await user.click(within(form).getByRole('button', { name: '新增' }))

    const alerts = await within(form).findAllByRole('alert')
    expect(alerts.map((a) => a.textContent)).toEqual([
      'must be 3 uppercase letters (ISO 4217), e.g. TWD',
      'must not be blank',
    ])
  })
})
