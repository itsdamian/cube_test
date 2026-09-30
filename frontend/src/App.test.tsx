import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App'
import { fakeChartFactory } from './test/fakeChart'
import { FakeEventSource } from './test/fakeEventSource'

function renderApp() {
  return render(<App eventSourceFactory={FakeEventSource.factory} chartFactory={fakeChartFactory().factory} />)
}

describe('App layout', () => {
  it('shows the page title and every section in Traditional Chinese', () => {
    renderApp()
    expect(screen.getByRole('heading', { level: 1, name: '比特幣即時價格' })).toBeInTheDocument()
    // Every area is a labelled region (landmark) for screen readers.
    for (const name of ['即時價格', '價格圖表', '價格警示', '多幣別換算']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
      expect(screen.getByRole('region', { name })).toBeInTheDocument()
    }
    expect(screen.getByTestId('header-feed-state')).toBeInTheDocument()
    // Charts are credited in the footer (the in-chart TradingView logo is switched off).
    expect(screen.getByRole('link', { name: 'TradingView Lightweight Charts™' })).toHaveAttribute('href', 'https://www.tradingview.com/')
  })

  it('manages currencies in a modal dialog that closes with Esc and returns focus to its button (task 31)', async () => {
    const user = userEvent.setup()
    renderApp()
    // Closed: not in the accessibility tree.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const manage = await screen.findByRole('button', { name: '管理幣別' })
    await user.click(manage)
    const dialog = screen.getByRole('dialog', { name: '管理幣別' })
    expect(await within(dialog).findByTestId('currency-TWD')).toBeInTheDocument()
    expect(within(dialog).getByRole('form', { name: '新增' })).toBeInTheDocument()

    within(dialog).getByRole('button', { name: '編輯 TWD' }).focus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(manage).toHaveFocus()

    // The × button closes it the same way.
    await user.click(manage)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '關閉' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(manage).toHaveFocus()
  })
})
