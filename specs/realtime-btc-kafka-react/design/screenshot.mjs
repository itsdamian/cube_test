// Screenshot the running app via raw Chrome DevTools Protocol (no deps).
// usage: node screenshot.mjs <out.png> <w> <h> <dpr> <dark|light> [mobile|desktop] [url] [waitMs]
// Copy of scripts/screenshot.mjs with a URL argument, for the file:// mockups.
// env:   SCREENSHOT_URL (default http://localhost:3001/), CHROME_PATH (default: macOS Google Chrome)
import { spawn } from 'node:child_process'
import { writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [out, w, h, dpr, scheme, mobile, url = 'http://localhost:3001/', wait = '1500'] = process.argv.slice(2)
const CHROME = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const URL = process.env.SCREENSHOT_URL || 'http://localhost:3001/'
const port = 9300 + Math.floor(Math.random() * 500)
const proc = spawn(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', `--remote-debugging-port=${port}`,
  `--user-data-dir=${mkdtempSync(join(tmpdir(), 'shot-'))}`, 'about:blank'], { stdio: 'ignore' })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

try {
  let target
  for (let i = 0; i < 50 && !target; i++) {
    await sleep(200)
    try { target = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((t) => t.type === 'page') } catch {}
  }
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((r) => (ws.onopen = r))
  let id = 0
  const pending = new Map()
  ws.onmessage = (m) => { const d = JSON.parse(m.data); pending.get(d.id)?.(d); pending.delete(d.id) }
  const send = (method, params = {}) => new Promise((r) => { pending.set(++id, r); ws.send(JSON.stringify({ id, method, params })) })

  await send('Emulation.setDeviceMetricsOverride', { width: +w, height: +h, deviceScaleFactor: +dpr, mobile: mobile === 'mobile' })
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: scheme }] })
  await send('Page.enable')
  await send('Page.navigate', { url })
  await sleep(+wait)
  // Layout check: horizontal page overflow + elements sticking out of the viewport.
  const check = await send('Runtime.evaluate', { returnByValue: true, expression: `(() => {
    const vw = document.documentElement.clientWidth, sw = document.documentElement.scrollWidth
    const bad = [...document.querySelectorAll('body *')].filter((e) => { const r = e.getBoundingClientRect(); return r.width && (r.right > vw + 0.5 || r.left < -0.5) && !e.closest('.mock-bar, dialog:not([open])') })
      .slice(0, 8).map((e) => e.tagName.toLowerCase() + '.' + [...e.classList].join('.') + ' ' + Math.round(e.getBoundingClientRect().right))
    return { vw, sw, bad }
  })()` })
  console.log('layout', JSON.stringify(check.result.result.value))
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
  console.log('saved', out)
  ws.close()
} finally {
  proc.kill()
}
