import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'
import { FakeEventSource } from './test/fakeEventSource'

describe('App layout', () => {
  it('shows the page title and every section in Traditional Chinese', () => {
    render(<App eventSourceFactory={FakeEventSource.factory} />)
    expect(screen.getByRole('heading', { level: 1, name: '比特幣即時價格' })).toBeInTheDocument()
    for (const name of ['即時價格', '多幣別換算', '價格圖表', '幣別管理', '價格警示']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
  })
})
