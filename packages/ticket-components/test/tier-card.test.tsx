// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TierCard, type TierCardProps } from '../src/tier-card'
import type { TicketTier } from '../src/index'

/**
 * 票档卡契约（docs/design/ui-system.md §3 tier-row + ticket-components README）：
 * 展示缓存可售状态与限购；售罄档不可点击（不触发 onSelect）；价格以元展示。
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

function render(props: TierCardProps) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(<TierCard {...props} />))
  return container
}

function tier(overrides: Partial<TicketTier>): TicketTier {
  return {
    productId: 'p-1',
    name: '内场 A 区',
    sessionId: '9',
    unitPriceMinor: 168000,
    currency: 'CNY',
    quantityLimit: 4,
    availability: 'Selling',
    ...overrides,
  }
}

describe('TierCard', () => {
  it('renders tier name, price in yuan, and availability label', () => {
    const node = render({ tier: tier({}), tierLevel: 1 })
    expect(node.textContent).toContain('内场 A 区')
    expect(node.textContent).toContain('1680.00')
    expect(node.textContent).toContain('售票中')
    expect(node.textContent).toContain('限购 4 张')
  })

  it('maps tier level to the tier-dot style class', () => {
    const node = render({ tier: tier({}), tierLevel: 3 })
    expect(node.querySelector('.tier-dot-3')).not.toBeNull()
    expect(node.querySelector('.tier-dot-1')).toBeNull()
  })

  it('disables sold-out tiers and never calls onSelect', () => {
    const onSelect = vi.fn()
    const node = render({
      tier: tier({ availability: 'SoldOut' }),
      tierLevel: 1,
      onSelect,
    })
    const button = node.querySelector('button')
    expect(button?.getAttribute('aria-disabled')).toBe('true')
    act(() => button?.dispatchEvent(new MouseEvent('click', { bubbles: true })))
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('calls onSelect with the tier when selling', () => {
    const onSelect = vi.fn()
    const t = tier({})
    const node = render({ tier: t, tierLevel: 1, onSelect })
    const button = node.querySelector('button')
    act(() => button?.dispatchEvent(new MouseEvent('click', { bubbles: true })))
    expect(onSelect).toHaveBeenCalledWith(t)
  })
})
