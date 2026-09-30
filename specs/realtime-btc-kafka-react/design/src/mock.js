/* ==========================================================================
   Shared mockup runtime (NOT part of the design): fake chart, simulated ticks,
   state switching, toasts and a few interactions so every state can be seen.
   Chart data (DATA) is real data captured from the running app's API.
   ========================================================================== */
(function () {
  const params = new URLSearchParams(location.search)
  const cfg = window.MOCK_CFG || {}
  const root = document.documentElement
  const body = document.body

  const RATES = { EUR: 0.88152, GBP: 0.755961, JPY: 157.389062, TWD: 31.84071, USD: 1 }
  const nf2 = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  const fmt = (v) => nf2.format(v)
  const money = (v, code) => new Intl.NumberFormat('zh-TW', { style: 'currency', currency: code, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v)
  const hhmm = (s) => new Date(s * 1000).toLocaleTimeString('zh-TW', { hour12: false, hour: '2-digit', minute: '2-digit' })
  const hhmmss = (ms) => new Date(ms).toLocaleTimeString('zh-TW', { hour12: false })
  const signed = (v) => (v >= 0 ? '+' : '−') + fmt(Math.abs(v))
  window.MOCK = { fmt, money, signed, RATES }

  /* ---------------------------------------------------------------- state */
  const c1 = DATA.c1.map((c) => c.slice())
  const c5 = DATA.c5.map((c) => c.slice())
  const tr = DATA.tr.map((c) => c.slice())
  let price = c1[c1.length - 1][4]
  const openPrice = price - 42.37           // "price when the page was opened"
  let sessHi = Math.max(price, openPrice + 61.2), sessLo = Math.min(price, openPrice - 18.9)
  let lastTickAt = Date.now()
  let view = '1m'
  let flash = { dir: null, at: 0 }
  let feed = params.get('state') || 'live'
  const FLASH_EVERY_MS = 2000, FLASH_MIN_GAP_MS = 1000

  /* ---------------------------------------------------------------- theme */
  function setTheme(t) {
    if (t === 'auto') root.removeAttribute('data-theme'); else root.setAttribute('data-theme', t)
    requestAnimationFrame(drawChart)
    document.querySelectorAll('[data-mock-theme]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.mockTheme === t))
  }

  /* ---------------------------------------------------------------- feed state */
  const LABEL = { live: '即時', stale: '資料延遲', disconnected: '已斷線', connecting: '連線中' }
  const MSG = {
    stale: '資料延遲：暫時沒有收到新的價格，顯示的可能不是最新價格。',
    disconnected: '已斷線：目前顯示的是最後收到的價格，連線恢復後會自動更新。',
  }
  function setFeed(s) {
    feed = s
    body.dataset.feed = s
    document.querySelectorAll('[data-status-label]').forEach((e) => (e.textContent = LABEL[s]))
    document.querySelectorAll('[data-status-source]').forEach((e) => (e.textContent = s === 'stale' ? 'Kraken' : 'Coinbase'))
    document.querySelectorAll('[data-source-role]').forEach((e) => (e.textContent = s === 'stale' ? '備援' : '主要'))
    document.querySelectorAll('[data-status-msg]').forEach((e) => { e.textContent = MSG[s] || ''; e.hidden = !MSG[s] })
    document.querySelectorAll('[data-mock-state]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.mockState === s))
  }

  /* ---------------------------------------------------------------- price binding */
  function bindPrice(dir, doFlash) {
    document.querySelectorAll('[data-usd]').forEach((e) => (e.textContent = fmt(price)))
    document.querySelectorAll('[data-usd-int]').forEach((e) => (e.textContent = fmt(price).split('.')[0]))
    document.querySelectorAll('[data-usd-dec]').forEach((e) => (e.textContent = '.' + fmt(price).split('.')[1]))
    document.querySelectorAll('[data-usd-sym]').forEach((e) => (e.textContent = money(price, 'USD')))
    document.querySelectorAll('[data-conv]').forEach((e) => (e.textContent = money(price * RATES[e.dataset.conv], e.dataset.conv)))
    const chg = price - openPrice
    document.querySelectorAll('[data-change]').forEach((e) => {
      e.textContent = `${signed(chg)}（${chg >= 0 ? '+' : '−'}${Math.abs((chg / openPrice) * 100).toFixed(2)}%）`
      e.dataset.sign = chg >= 0 ? 'up' : 'down'
    })
    document.querySelectorAll('[data-sess-hi]').forEach((e) => (e.textContent = fmt(sessHi)))
    document.querySelectorAll('[data-sess-lo]').forEach((e) => (e.textContent = fmt(sessLo)))
    document.querySelectorAll('[data-time]').forEach((e) => (e.textContent = hhmmss(lastTickAt)))
    document.querySelectorAll('[data-dist]').forEach((e) => {
      const d = +e.dataset.dist - price
      e.textContent = `${d >= 0 ? '還差 +' : '還差 −'}${fmt(Math.abs(d))}`
    })
    if (dir) document.querySelectorAll('.js-dir').forEach((e) => {
      e.dataset.dir = dir
      const sr = e.querySelector('.sr'); if (sr) sr.textContent = dir === 'up' ? '較前一筆上漲' : '較前一筆下跌'
      const g = e.querySelector('.glyph'); if (g) g.textContent = dir === 'up' ? '▲' : '▼'
    })
    if (doFlash) document.querySelectorAll('.js-flash').forEach((e) => {
      e.classList.remove('flash-up', 'flash-down'); void e.offsetWidth; e.classList.add('flash-' + dir)
    })
  }

  function tick() {
    if (feed !== 'live' || params.get('tick') === '0') return
    const prev = price
    price = Math.round((price + (Math.random() - 0.48) * 9) * 100) / 100
    if (price === prev) return
    lastTickAt = Date.now()
    sessHi = Math.max(sessHi, price); sessLo = Math.min(sessLo, price)
    for (const set of [c1, c5]) { const l = set[set.length - 1]; l[4] = price; l[2] = Math.max(l[2], price); l[3] = Math.min(l[3], price) }
    tr[tr.length - 1][1] = price
    const dir = price > prev ? 'up' : 'down'
    const now = lastTickAt, since = now - flash.at
    const doFlash = since >= FLASH_MIN_GAP_MS && (dir !== flash.dir || since >= FLASH_EVERY_MS)
    if (doFlash) flash = { dir, at: now }
    bindPrice(dir, doFlash)
    drawChart()
  }

  /* ---------------------------------------------------------------- fake chart (SVG, mimics lightweight-charts) */
  function niceStep(range, n) {
    const raw = range / n, p = 10 ** Math.floor(Math.log10(raw)), f = raw / p
    return (f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10) * p
  }
  function drawChart() {
    const host = document.getElementById('chart')
    if (!host) return
    const W = host.clientWidth, H = host.clientHeight
    if (!W || !H) return
    const axisW = cfg.axisW || 64, axisH = 24, padT = 12
    const pw = W - axisW, ph = H - axisH - padT
    const isLine = view === 'trend'
    const src = view === '1m' ? c1 : view === '5m' ? c5 : tr
    const slot = view === '5m' ? Math.max(10, pw / src.length) : view === '1m' ? (cfg.slot1m || 8) : 0
    const data = isLine ? src : src.slice(-Math.max(8, Math.floor((pw - 8) / slot)))
    const lows = data.map((d) => (isLine ? d[1] : d[3])), highs = data.map((d) => (isLine ? d[1] : d[2]))
    let lo = Math.min(...lows), hi = Math.max(...highs)
    const pad = (hi - lo) * 0.1 || 10; lo -= pad; hi += pad
    const y = (v) => padT + ((hi - v) / (hi - lo)) * ph
    const n = data.length
    const x = isLine ? (i) => (i / (n - 1)) * (pw - 8) + 2 : (i) => (i + 0.5) * ((pw - 4) / n)
    const bw = isLine ? 0 : Math.max(2, ((pw - 4) / n) * 0.62)
    let g = ''
    // horizontal grid + price labels
    const step = niceStep(hi - lo, H < 300 ? 4 : 6)
    for (let v = Math.ceil(lo / step) * step; v < hi; v += step) {
      g += `<line class="ch-grid" x1="0" x2="${pw}" y1="${y(v).toFixed(1)}" y2="${y(v).toFixed(1)}"/>`
      g += `<text class="ch-axis" x="${pw + 8}" y="${(y(v) + 4).toFixed(1)}">${nf2.format(v).replace(/\.00$/, '')}</text>`
    }
    // vertical grid + time labels
    const every = view === '1m' ? 15 : view === '5m' ? 30 : 15
    let lastLx = -99
    data.forEach((d, i) => {
      const t = new Date(d[0] * 1000)
      const onMark = isLine ? t.getMinutes() % every === 0 && t.getSeconds() < 12 : t.getMinutes() % every === 0
      if (onMark && x(i) - lastLx > 56 && x(i) > 18 && x(i) < pw - 18) {
        lastLx = x(i)
        g += `<line class="ch-grid" x1="${x(i).toFixed(1)}" x2="${x(i).toFixed(1)}" y1="${padT}" y2="${padT + ph}"/>`
        g += `<text class="ch-axis" text-anchor="middle" x="${x(i).toFixed(1)}" y="${H - 7}">${hhmm(d[0])}</text>`
      }
    })
    g += `<line class="ch-border" x1="${pw}" x2="${pw}" y1="0" y2="${H - axisH}"/><line class="ch-border" x1="0" x2="${W}" y1="${H - axisH}" y2="${H - axisH}"/>`
    if (isLine) {
      const pts = data.map((d, i) => `${x(i).toFixed(1)},${y(d[1]).toFixed(1)}`).join(' ')
      if (cfg.area) g += `<defs><linearGradient id="ar" x1="0" x2="0" y1="0" y2="1"><stop offset="0" class="ch-area-top"/><stop offset="1" class="ch-area-bot"/></linearGradient></defs><polygon fill="url(#ar)" points="${x(0)},${padT + ph} ${pts} ${x(n - 1)},${padT + ph}"/>`
      g += `<polyline class="ch-line" points="${pts}"/>`
    } else {
      data.forEach((d, i) => {
        const [, o, h, l, c] = d, cls = c >= o ? 'ch-up' : 'ch-down', cx = x(i)
        g += `<line class="${cls}" x1="${cx.toFixed(1)}" x2="${cx.toFixed(1)}" y1="${y(h).toFixed(1)}" y2="${y(l).toFixed(1)}"/>`
        g += `<rect class="${cls}" x="${(cx - bw / 2).toFixed(1)}" y="${y(Math.max(o, c)).toFixed(1)}" width="${bw.toFixed(1)}" height="${Math.max(1, Math.abs(y(o) - y(c))).toFixed(1)}" rx="${cfg.candleRadius || 0}"/>`
      })
    }
    // last-price line + tag on the axis
    const last = isLine ? data[n - 1][1] : data[n - 1][4]
    const lastUp = isLine ? true : data[n - 1][4] >= data[n - 1][1]
    const ly = y(last)
    g += `<line class="ch-last ${isLine ? 'ch-last--line' : lastUp ? 'ch-last--up' : 'ch-last--down'}" x1="0" x2="${pw}" y1="${ly.toFixed(1)}" y2="${ly.toFixed(1)}"/>`
    g += `<rect class="ch-tag ${isLine ? 'ch-tag--line' : lastUp ? 'ch-tag--up' : 'ch-tag--down'}" x="${pw}" y="${(ly - 10).toFixed(1)}" width="${axisW}" height="20" rx="${cfg.tagRadius || 0}"/>`
    g += `<text class="ch-tag-text" x="${pw + 6}" y="${(ly + 4).toFixed(1)}">${fmt(last)}</text>`
    host.innerHTML = `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-label="BTC-USD 價格圖（示意）">${g}</svg>`
    // OHLC legend + visible-range high/low (optional derived data)
    const lc = data[n - 1]
    document.querySelectorAll('[data-ohlc]').forEach((e) => {
      const k = e.dataset.ohlc
      if (isLine) { e.textContent = k === 'c' ? fmt(lc[1]) : '—'; return }
      e.textContent = fmt(lc[{ o: 1, h: 2, l: 3, c: 4 }[k]])
    })
    document.querySelectorAll('[data-range-hi]').forEach((e) => (e.textContent = fmt(Math.max(...highs))))
    document.querySelectorAll('[data-range-lo]').forEach((e) => (e.textContent = fmt(Math.min(...lows))))
    document.querySelectorAll('[data-view-label]').forEach((e) => (e.textContent = { '1m': '1 分 K', '5m': '5 分 K', trend: '1 小時走勢' }[view]))
  }

  /* ---------------------------------------------------------------- segmented control (chart views) */
  document.querySelectorAll('[data-view]').forEach((b) => b.addEventListener('click', () => {
    view = b.dataset.view
    document.querySelectorAll('[data-view]').forEach((x) => x.setAttribute('aria-selected', x === b))
    body.dataset.view = view
    drawChart()
  }))
  document.querySelectorAll('[role=tablist]').forEach((list) => list.addEventListener('keydown', (e) => {
    if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return
    const tabs = [...list.querySelectorAll('[role=tab]')], i = tabs.indexOf(document.activeElement)
    const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length]
    next.focus(); next.click()
  }))

  /* ---------------------------------------------------------------- toasts */
  let toastN = 0
  function showToast() {
    const tpl = document.getElementById('toast-tpl'), box = document.querySelector('.toasts')
    if (!tpl || !box) return
    const el = tpl.content.firstElementChild.cloneNode(true)
    el.dataset.n = ++toastN
    el.querySelectorAll('[data-toast-now]').forEach((e) => (e.textContent = hhmmss(Date.now())))
    el.querySelector('[data-toast-close]')?.addEventListener('click', () => el.remove())
    box.appendChild(el)
    if (params.get('shot') !== '1') setTimeout(() => el.remove(), 10000)
  }

  /* ---------------------------------------------------------------- generic interactions */
  document.addEventListener('click', (e) => {
    const t = e.target.closest('button, a'); if (!t) return
    const a = t.dataset.act
    if (!a) return
    const row = t.closest('[data-row]')
    if (a === 'ask-delete') { row.classList.add('is-confirming'); row.querySelector('[data-act=confirm-delete]')?.focus() }
    if (a === 'cancel-delete') { row.classList.remove('is-confirming'); row.querySelector('[data-act=ask-delete]')?.focus() }
    if (a === 'confirm-delete') { const list = row.parentElement; row.remove(); syncEmpty(list) }
    if (a === 'edit') { row.classList.add('is-editing'); row.querySelector('input')?.focus() }
    if (a === 'cancel-edit' || a === 'save-edit') row.classList.remove('is-editing')
    if (a === 'read') { const list = row.parentElement; row.remove(); syncUnread(list) }
    if (a === 'read-all') { document.querySelectorAll('[data-unread-list] [data-row]').forEach((r) => r.remove()); syncUnread(document.querySelector('[data-unread-list]')) }
    if (a === 'open-currency') document.getElementById('currency-dialog')?.showModal()
    if (a === 'close-dialog') t.closest('dialog')?.close()
    if (a === 'fill-price') { const inp = document.querySelector('#alert-threshold'); if (inp) { inp.value = Math.round(price); inp.focus() } }
    if (a === 'toggle') { const tgt = document.getElementById(t.getAttribute('aria-controls')); const open = t.getAttribute('aria-expanded') !== 'true'; t.setAttribute('aria-expanded', open); tgt.hidden = !open; if (open) tgt.querySelector('input,button')?.focus() }
  })
  function syncEmpty(list) {
    if (!list) return
    const empty = document.querySelector(`[data-empty-for="${list.id}"]`)
    if (empty) empty.hidden = list.querySelectorAll('[data-row]').length > 0
  }
  function syncUnread(list) {
    const n = list ? list.querySelectorAll('[data-row]').length : 0
    document.querySelectorAll('[data-unread-count]').forEach((e) => (e.textContent = n))
    body.dataset.unread = n
  }
  // alert form
  document.getElementById('alert-form')?.addEventListener('submit', (e) => {
    e.preventDefault()
    const f = e.target, v = Number(f.threshold.value), err = f.querySelector('[data-err]')
    if (!f.threshold.value || !(v > 0)) { err.textContent = '請輸入大於 0 的價格'; err.hidden = false; f.threshold.setAttribute('aria-invalid', 'true'); f.threshold.focus(); return }
    err.hidden = true; f.threshold.removeAttribute('aria-invalid')
    const list = document.getElementById('alert-list')
    list.insertAdjacentHTML('afterbegin', window.renderAlertRow(f.direction.value, v))
    f.threshold.value = ''; syncEmpty(list); bindPrice()
  })
  // currency form
  document.getElementById('currency-form')?.addEventListener('submit', (e) => {
    e.preventDefault()
    const f = e.target, code = f.code.value.trim().toUpperCase(), name = f.cname.value.trim()
    const setErr = (field, msg) => { const el = f.querySelector(`[data-err=${field}]`); el.textContent = msg || ''; el.hidden = !msg; f[field].toggleAttribute('aria-invalid', !!msg); if (msg) f[field].setAttribute('aria-invalid', 'true') }
    const exists = [...document.querySelectorAll('#currency-list [data-code]')].some((r) => r.dataset.code === code)
    const codeErr = !/^[A-Z]{3}$/.test(code) ? '代碼須為 3 個英文字母（ISO 4217），例如 TWD' : exists ? '代碼已存在' : ''
    const nameErr = name ? '' : '請輸入中文名稱'
    setErr('code', codeErr); setErr('cname', nameErr)
    if (codeErr || nameErr) { f[codeErr ? 'code' : 'cname'].focus(); return }
    document.getElementById('currency-list').insertAdjacentHTML('beforeend', window.renderCurrencyRow(code, name))
    f.reset()
  })

  /* ---------------------------------------------------------------- mockup control bar (outside the design) */
  function controlBar() {
    if (params.get('bar') === '0') return
    const bar = document.createElement('aside')
    bar.className = 'mock-bar'
    bar.setAttribute('aria-label', 'Mockup 控制列（不屬於設計）')
    bar.innerHTML = `
      <strong>MOCKUP 控制列 <small>（不屬於設計）</small></strong>
      <div role="group" aria-label="主題">主題
        <button data-mock-theme="auto">自動</button><button data-mock-theme="dark">深色</button><button data-mock-theme="light">淺色</button></div>
      <div role="group" aria-label="連線狀態">狀態
        <button data-mock-state="live">即時</button><button data-mock-state="stale">延遲</button><button data-mock-state="disconnected">已斷線</button><button data-mock-state="connecting">載入中</button></div>
      <div role="group" aria-label="其他">
        <button data-mock-toast>觸發 toast</button><button data-mock-currency>幣別管理</button><button data-mock-hide title="隱藏控制列">隱藏</button></div>`
    document.body.appendChild(bar)
    bar.addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return
      if (b.dataset.mockTheme) setTheme(b.dataset.mockTheme)
      if (b.dataset.mockState) setFeed(b.dataset.mockState)
      if ('mockToast' in b.dataset) showToast()
      if ('mockCurrency' in b.dataset) document.getElementById('currency-dialog')?.showModal()
      if ('mockHide' in b.dataset) bar.remove()
    })
  }

  /* ---------------------------------------------------------------- boot */
  controlBar()
  setTheme(params.get('theme') || 'auto')
  setFeed(feed)
  body.dataset.view = view
  bindPrice('up')
  drawChart()
  document.querySelectorAll('[data-unread-list]').forEach(syncUnread)
  if (params.get('toast') === '1') showToast()
  if (params.get('dialog') === '1') document.getElementById('currency-dialog')?.showModal()
  if (params.get('confirm') === '1') document.querySelector('#alert-list [data-row]')?.classList.add('is-confirming')
  if (params.get('shot') !== '1') setInterval(tick, 650)
  let rz; addEventListener('resize', () => { clearTimeout(rz); rz = setTimeout(drawChart, 60) })
  matchMedia('(prefers-color-scheme: light)').addEventListener('change', drawChart)
})()
