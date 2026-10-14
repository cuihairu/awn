// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDebouncedValue } from './use-debounced-value'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

function Harness({ value, delay }: { value: string; delay?: number }) {
  const debounced = useDebouncedValue(value, delay)
  return <span>{debounced}</span>
}

function setup(initial: string, delay = 300) {
  const node = document.createElement('div')
  document.body.appendChild(node)
  const root: Root = createRoot(node)
  const render = (v: string) => act(() => root.render(<Harness value={v} delay={delay} />))
  render(initial)
  return { node, set: render, unmount: () => act(() => root.unmount()) }
}

describe('useDebouncedValue', () => {
  beforeEach(() => vi.useFakeTimers())

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('passes the first value through immediately', () => {
    const h = setup('沈')
    expect(h.node.querySelector('span')?.textContent).toBe('沈')
    h.unmount()
  })

  it('holds the old value until the delay elapses', () => {
    const h = setup('沈')
    h.set('沈流')
    expect(h.node.querySelector('span')?.textContent).toBe('沈')
    act(() => vi.advanceTimersByTime(299))
    expect(h.node.querySelector('span')?.textContent).toBe('沈')
    act(() => vi.advanceTimersByTime(1))
    expect(h.node.querySelector('span')?.textContent).toBe('沈流')
    h.unmount()
  })

  it('resets the timer when the value changes again before the delay', () => {
    const h = setup('沈')
    h.set('沈流')
    act(() => vi.advanceTimersByTime(200))
    h.set('沈流舒')
    act(() => vi.advanceTimersByTime(200))
    expect(h.node.querySelector('span')?.textContent).toBe('沈')
    act(() => vi.advanceTimersByTime(100))
    expect(h.node.querySelector('span')?.textContent).toBe('沈流舒')
    h.unmount()
  })
})
