import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { Currency, CurrencyInput } from '../api/types'
import { notifyCurrenciesChanged } from './events'

type FieldErrors = Partial<Record<keyof CurrencyInput, string>> & { form?: string }

/** Turn an API error into messages next to the fields (400) or for the whole form (409, others). */
function toErrors(error: unknown): FieldErrors {
  if (error instanceof ApiError) {
    if (error.status === 409) return { code: '代碼已存在' }
    if (error.status === 400) {
      const fields = error.fieldErrors()
      return { code: fields.code, name: fields.name, form: fields.code || fields.name ? undefined : error.message }
    }
    if (error.status === 404) return { form: '此幣別已不存在，請重新整理' }
  }
  return { form: '操作失敗，請稍後再試' }
}

const EMPTY: CurrencyInput = { code: '', name: '' }

function CurrencyForm({ initial, submitLabel, onSubmit, onCancel }: {
  initial: CurrencyInput
  submitLabel: string
  onSubmit: (input: CurrencyInput) => Promise<void>
  onCancel?: () => void
}) {
  const [input, setInput] = useState(initial)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setErrors({})
    try {
      await onSubmit({ code: input.code.trim().toUpperCase(), name: input.name.trim() })
      if (!onCancel) setInput(EMPTY)
    } catch (error) {
      setErrors(toErrors(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="currency-form" onSubmit={submit} aria-label={submitLabel}>
      <label>
        代碼
        <input value={input.code} maxLength={3} required placeholder="TWD" aria-invalid={!!errors.code}
               onChange={(e) => setInput({ ...input, code: e.target.value })} />
      </label>
      {errors.code && <span className="field-error" role="alert">{errors.code}</span>}
      <label>
        中文名稱
        <input value={input.name} maxLength={50} required placeholder="新台幣" aria-invalid={!!errors.name}
               onChange={(e) => setInput({ ...input, name: e.target.value })} />
      </label>
      {errors.name && <span className="field-error" role="alert">{errors.name}</span>}
      <button type="submit" disabled={busy}>{submitLabel}</button>
      {onCancel && <button type="button" onClick={onCancel}>取消</button>}
      {errors.form && <span className="field-error" role="alert">{errors.form}</span>}
    </form>
  )
}

/** List, add, rename and delete currencies (code + Chinese name). */
export function CurrencyManager() {
  const [currencies, setCurrencies] = useState<Currency[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [editing, setEditing] = useState<number | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setCurrencies(await api.currencies())
      setLoadError(false)
    } catch {
      setLoadError(true)
    }
  }, [])

  // Initial load; ignore a late answer if the component is gone.
  useEffect(() => {
    let cancelled = false
    api.currencies()
      .then((list) => !cancelled && setCurrencies(list))
      .catch(() => !cancelled && setLoadError(true))
    return () => {
      cancelled = true
    }
  }, [])

  const changed = async () => {
    await reload()
    notifyCurrenciesChanged()
  }

  const remove = async (id: number) => {
    setDeleteError(null)
    try {
      await api.deleteCurrency(id)
      setConfirmDelete(null)
      await changed()
    } catch (error) {
      setDeleteError(toErrors(error).form ?? '刪除失敗')
    }
  }

  if (loadError) return <p>無法載入幣別清單</p>
  if (!currencies) return <p>載入中…</p>

  return (
    <div>
      <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">代碼</th>
            <th scope="col">中文名稱</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          {currencies.map((c) =>
            editing === c.id ? (
              <tr key={c.id}>
                <td colSpan={3}>
                  <CurrencyForm initial={{ code: c.code, name: c.name }} submitLabel="儲存"
                                onCancel={() => setEditing(null)}
                                onSubmit={async (input) => {
                                  await api.updateCurrency(c.id, input)
                                  setEditing(null)
                                  await changed()
                                }} />
                </td>
              </tr>
            ) : (
              <tr key={c.id} data-testid={`currency-${c.code}`}>
                <td>{c.code}</td>
                <td>{c.name}</td>
                <td className="actions">
                  <button type="button" onClick={() => setEditing(c.id)} aria-label={`編輯 ${c.code}`}>編輯</button>
                  {confirmDelete === c.id ? (
                    <>
                      <button type="button" className="danger" onClick={() => void remove(c.id)}>確定刪除？</button>
                      <button type="button" onClick={() => setConfirmDelete(null)}>取消</button>
                    </>
                  ) : (
                    <button type="button" onClick={() => setConfirmDelete(c.id)} aria-label={`刪除 ${c.code}`}>刪除</button>
                  )}
                </td>
              </tr>
            ),
          )}
        </tbody>
      </table>
      </div>
      {deleteError && <p className="field-error" role="alert">{deleteError}</p>}
      <h3>新增幣別</h3>
      <CurrencyForm initial={EMPTY} submitLabel="新增" onSubmit={async (input) => {
        await api.createCurrency(input)
        await changed()
      }} />
    </div>
  )
}
