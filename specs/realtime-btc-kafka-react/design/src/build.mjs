// Assemble self-contained mockups: src/direction-x.src.html + tokens + data + shared runtime.
import { readFileSync, writeFileSync } from 'node:fs'
import { PALETTES } from './tokens.mjs'
const here = new URL('.', import.meta.url).pathname
const kebab = (k) => k.replace(/([A-Z])/g, '-$1').replace(/(\d)/g, '-$1').toLowerCase()
const vars = (p) => Object.entries(p).map(([k, v]) => `--${kebab(k)}:${v};`).join('')
for (const dir of ['a', 'b'].filter((d) => process.argv.length < 3 || process.argv.includes(d))) {
  const { dark, light } = PALETTES[dir]
  const tokens = `:root{color-scheme:dark;${vars(dark)}}
@media (prefers-color-scheme: light){:root:not([data-theme="dark"]){color-scheme:light;${vars(light)}}}
:root[data-theme="light"]{color-scheme:light;${vars(light)}}`
  let html = readFileSync(`${here}direction-${dir}.src.html`, 'utf8')
  html = html.replace('/*@TOKENS*/', tokens).replace('/*@MOCKCSS*/', readFileSync(`${here}mock.css`, 'utf8'))
    .replace('/*@DATA*/', readFileSync(`${here}data.js`, 'utf8')).replace('/*@MOCKJS*/', readFileSync(`${here}mock.js`, 'utf8'))
  writeFileSync(`${here}../direction-${dir}.html`, html)
  console.log('built', `direction-${dir}.html`, html.length)
}
