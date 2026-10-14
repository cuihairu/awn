// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SeatMap } from './SeatMap'
import { groupSeatSections, type SeatInput } from '../lib/seatmap'

/**
 * 2D 座位图渲染契约（原型 seatmap-2d.html）：
 * 可选座位可点击 / 键盘切换，已售置灰座位不可交互；
 * 图上只有三态外观（可选 / 置灰 / 选中），状态原文进 aria-label。
 */

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let container: HTMLDivElement | null = null
let root: Root | null = null

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  container = null
  root = null
})

function render(element: React.ReactNode) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(element))
  return container
}

const seats: SeatInput[] = [
  { seatNo: 'SEAT-A', section: 'A', row: '1', seat: '1', status: 'IN_STOCK' },
  { seatNo: 'SEAT-B', section: 'A', row: '1', seat: '2', status: 'SOLD' },
  { seatNo: 'SEAT-C', section: 'A', row: '1', seat: '3', status: 'IN_STOCK' },
]

function renderMap(selected: string[] = [], onToggleSeat?: (seatNo: string) => void) {
  return render(
    <SeatMap
      sections={groupSeatSections(seats)}
      selected={selected}
      onToggleSeat={onToggleSeat}
    />,
  )
}

describe('SeatMap', () => {
  it('renders sections, rows and seats with per-status aria labels', () => {
    const node = renderMap()
    const buttons = [...node.querySelectorAll('[role="button"]')]
    expect(buttons).toHaveLength(3)
    expect(buttons[0].getAttribute('aria-label')).toBe('A 1 排 1 座 可选')
    expect(buttons[1].getAttribute('aria-label')).toBe('A 1 排 2 座 已售')
  })

  it('marks only IN_STOCK seats interactive', () => {
    const node = renderMap()
    const buttons = [...node.querySelectorAll('[role="button"]')]
    expect(buttons[0].getAttribute('tabindex')).toBe('0')
    expect(buttons[1].getAttribute('tabindex')).toBe('-1')
    expect(buttons[1].getAttribute('aria-disabled')).toBeNull()
    expect(buttons[1].querySelector('rect')?.getAttribute('class')).toContain('seat--occupied')
  })

  it('toggles selection only for selectable seats', () => {
    const onToggleSeat = vi.fn()
    const node = renderMap(['SEAT-A'], onToggleSeat)
    const buttons = [...node.querySelectorAll('[role="button"]')]
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true')
    expect(buttons[0].querySelector('rect')?.getAttribute('class')).toContain('seat--selected')

    act(() => {
      buttons[0].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(onToggleSeat).toHaveBeenCalledWith('SEAT-A')

    act(() => {
      buttons[1].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(onToggleSeat).toHaveBeenCalledTimes(1)
  })

  it('supports keyboard toggling with Enter and Space', () => {
    const onToggleSeat = vi.fn()
    const node = renderMap([], onToggleSeat)
    const buttons = [...node.querySelectorAll('[role="button"]')]
    act(() => {
      buttons[0].dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }),
      )
    })
    act(() => {
      buttons[2].dispatchEvent(
        new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true }),
      )
    })
    expect(onToggleSeat).toHaveBeenCalledWith('SEAT-A')
    expect(onToggleSeat).toHaveBeenCalledWith('SEAT-C')
  })

  it('keeps the legend and the backend-recheck hint', () => {
    const node = renderMap()
    const legend = node.querySelector('.seat-legend')
    expect(legend?.textContent).toContain('可选')
    expect(legend?.textContent).toContain('已售 / 不可售')
    expect(legend?.textContent).toContain('本次选中')
    expect(node.querySelector('.hint')?.textContent).toContain('显示可选 ≠ 后台有库存')
  })
})
