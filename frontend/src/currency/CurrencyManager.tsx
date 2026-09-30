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
    <form className={onCancel ? 'cform cform--inline' : 'cform'} onSubmit={submit} aria-label={submitLabel}>
      <label className="field">
        代碼
        <input value={input.code} maxLength={3} required placeholder="CHF" autoComplete="off" aria-invalid={!!errors.code}
               onChange={(e) => setInput({ ...input, code: e.target.value })} />
      </label>
      <label className="field">
        中文名稱
        <input value={input.name} maxLength={50} required placeholder="瑞士法郎" autoComplete="off" aria-invalid={!!errors.name}
               onChange={(e) => setInput({ ...input, name: e.target.value })} />
      </label>
      <button className={onCancel ? 'btn btn--primary btn--sm' : 'btn btn--primary'} type="submit" disabled={busy}>{submitLabel}</button>
      {onCancel && <button className="btn btn--sm" type="button" onClick={onCancel}>取消</button>}
      {errors.code && <span className="field-error" role="alert">{errors.code}</span>}
      {errors.name && <span className="field-error" role="alert">{errors.name}</span>}
      {errors.form && <span className="field-error" role="alert">{errors.form}</span>}
    </form>
  )
}

/**
 * List, add, rename and delete currencies (code + Chinese name). Rendered inside the currency
 * dialog: the list scrolls in the body, the add form sits in the footer.
 */
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

  return (
    <>
      <div className="modal__body">
        {loadError && <p className="muted">無法載入幣別清單</p>}
        {!loadError && !currencies && <p className="muted">載入中…</p>}
        {!loadError && currencies && (
          <ul className="clist" aria-label="幣別清單">
            {currencies.map((c) =>
              editing === c.id ? (
                <li key={c.id}>
                  <div className="edit">
                    <CurrencyForm initial={{ code: c.code, name: c.name }} submitLabel="儲存"
                                  onCancel={() => setEditing(null)}
                                  onSubmit={async (input) => {
                                    await api.updateCurrency(c.id, input)
                                    setEditing(null)
                                    await changed()
                                  }} />
                  </div>
                </li>
              ) : (
                <li key={c.id} data-testid={`currency-${c.code}`}>
                  <span className="view"><span className="tile__code">{c.code}</span><span className="nowrap">{c.name}</span></span>
                  <span className="acts">
                    <button className="btn btn--sm" type="button" onClick={() => setEditing(c.id)} aria-label={`編輯 ${c.code}`}>編輯</button>
                    {confirmDelete === c.id ? (
                      <>
                        <button type="button" className="btn btn--danger btn--sm" onClick={() => void remove(c.id)}>確定刪除？</button>
                        <button type="button" className="btn btn--sm" onClick={() => setConfirmDelete(null)}>取消</button>
                      </>
                    ) : (
                      <button type="button" className="btn btn--quiet btn--sm" onClick={() => setConfirmDelete(c.id)} aria-label={`刪除 ${c.code}`}>刪除</button>
                    )}
                  </span>
                </li>
              ),
            )}
          </ul>
        )}
        {deleteError && <p className="field-error" role="alert">{deleteError}</p>}
      </div>
      <footer className="modal__foot">
        <h3>新增幣別</h3>
        <CurrencyForm initial={EMPTY} submitLabel="新增" onSubmit={async (input) => {
          await api.createCurrency(input)
          await changed()
        }} />
      </footer>
    </>
  )
}
