// Screenshot the running app via raw Chrome DevTools Protocol (no deps).
// usage: node scripts/screenshot.mjs <out.png> <width> <height> <dpr> <dark|light> [mobile]
// env:   SCREENSHOT_URL (default http://localhost:3001/), CHROME_PATH (default: macOS Google Chrome)
import { spawn } from 'node:child_process'
import { writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [out, w, h, dpr, scheme, mobile] = process.argv.slice(2)
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
  await send('Page.navigate', { url: URL })
  await sleep(7000) // real time: let SSE deliver prices and charts render
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
  console.log('saved', out)
  ws.close()
} finally {
  proc.kill()
}
