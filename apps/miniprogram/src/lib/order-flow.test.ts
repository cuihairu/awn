import { describe, expect, it } from 'vitest'
import {
  buildDraft,
  deriveAvailability,
  formatMpTime,
  minorToYuan,
  newIdempotencyKey,
  toOrderLine,
  validateDraft,
} from './order-flow'
import type { ProductListItem } from './mp-api'

function product(overrides: Partial<ProductListItem> = {}): ProductListItem {
  return {
    productCode: 'p-1',
    name: 'VIP票',
    status: 'PUBLISHED',
    unitPrice: 199900,
    categoryId: null,
    sessionId: '9',
    poolCode: 'POOL-1',
    available: 100,
    ...overrides,
  }
}

describe('可售状态口径（与 web 端一致）', () => {
  it('derives NotOnSale / SoldOut / LowStock / Selling', () => {
    expect(deriveAvailability(null)).toBe('NotOnSale')
    expect(deriveAvailability(0)).toBe('SoldOut')
    expect(deriveAvailability(20)).toBe('LowStock')
    expect(deriveAvailability(21)).toBe('Selling')
  })
})

describe('下单行与草稿', () => {
  it('maps a product to an order line carrying the pool code', () => {
    const line = toOrderLine(product(), 2)
    expect(line).toEqual({
      poolCode: 'POOL-1',
      productId: 'p-1',
      productNameSnapshot: 'VIP票',
      sessionId: '9',
      quantity: 2,
      unitPrice: 199900,
      discountAmount: 0,
      feeAmount: 0,
    })
  })

  it('builds a draft with display total in minor units', () => {
    const draft = buildDraft([
      { product: product(), quantity: 2 },
      { product: product({ productCode: 'p-2', unitPrice: 59900 }), quantity: 1 },
    ])
    expect(draft.totalMinor).toBe(199900 * 2 + 59900)
    expect(draft.lines).toHaveLength(2)
  })

  it('flags non-positive quantities, missing pools and sold-out tiers', () => {
    const issues = validateDraft([
      { product: product(), quantity: 0 },
      { product: product({ productCode: 'p-2', poolCode: null }), quantity: 1 },
      { product: product({ productCode: 'p-3', available: 0 }), quantity: 1 },
      { product: product({ productCode: 'p-4', available: null }), quantity: 1 },
    ])
    expect(issues.map((i) => i.productCode)).toEqual(['p-1', 'p-2', 'p-3'])
    // 未建池（null available）按未开售放行到后端重校验（展示层不为 null 造售罄语义）
    expect(issues.find((i) => i.productCode === 'p-4')).toBeUndefined()
  })

  it('passes a clean draft', () => {
    expect(validateDraft([{ product: product({ available: 3 }), quantity: 1 }])).toEqual([])
  })
})

describe('幂等键与展示格式', () => {
  it('generates unique non-empty idempotency keys', () => {
    const a = newIdempotencyKey()
    const b = newIdempotencyKey()
    expect(a).not.toBe('')
    expect(a).not.toBe(b)
  })

  it('formats minor units and times like the web app', () => {
    expect(minorToYuan(388000)).toBe('3,880.00')
    expect(minorToYuan(0)).toBe('0.00')
    expect(formatMpTime('2026-11-02T19:30:00+08:00')).toMatch(/^\d{2}-\d{2} \d{2}:\d{2}$/)
    expect(formatMpTime('bad')).toBe('')
    expect(formatMpTime(null)).toBe('')
  })
})
