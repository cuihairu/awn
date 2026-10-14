import { describe, expect, it } from 'vitest'
import { AVAILABILITY_LABEL_ZH, type AvailabilityLabel, type TicketTier } from '../src/index'

/**
 * 票档展示契约：展示层可用缓存值，但文案映射必须覆盖全部后端 availability 枚举，
 * 且各状态文案互不相同（避免前端把「余票紧张」渲染成「已售罄」）。
 */
const ALL_LABELS: AvailabilityLabel[] = [
  'Selling',
  'LowStock',
  'SoldOut',
  'NotOnSale',
]

describe('AVAILABILITY_LABEL_ZH', () => {
  it('covers every availability label', () => {
    expect(Object.keys(AVAILABILITY_LABEL_ZH).sort()).toEqual([...ALL_LABELS].sort())
  })

  it('maps each label to a distinct non-empty Chinese string', () => {
    const texts = ALL_LABELS.map((label) => AVAILABILITY_LABEL_ZH[label])
    for (const text of texts) {
      expect(text.length).toBeGreaterThan(0)
      expect(text).toMatch(/[一-鿿]/)
    }
    expect(new Set(texts).size).toBe(ALL_LABELS.length)
  })

  it('uses the exact copy for the two extreme states', () => {
    expect(AVAILABILITY_LABEL_ZH.Selling).toBe('售票中')
    expect(AVAILABILITY_LABEL_ZH.SoldOut).toBe('已售罄')
  })
})

describe('TicketTier contract', () => {
  it('expresses money as integer minor units, never floats', () => {
    const tier: TicketTier = {
      productId: 'prod-001',
      name: '内场 A 区',
      sessionId: 'sess-001',
      unitPriceMinor: 30000,
      currency: 'CNY',
      quantityLimit: 2,
      availability: 'LowStock',
    }
    expect(Number.isInteger(tier.unitPriceMinor)).toBe(true)
    expect(tier.unitPriceMinor).toBe(30000)
    expect(tier.currency).toBe('CNY')
  })
})
