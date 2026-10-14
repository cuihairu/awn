import { describe, expect, it } from 'vitest'
import type { ProductListItem } from '@awn/api-client'
import {
  EVENT_STATUS_LABEL_ZH,
  deriveAvailability,
  eventStatusLabel,
  formatEventDateTime,
  geoLine,
  productToTier,
} from './event-labels'

/**
 * 活动 / 票档展示层契约（docs/design/ui-system.md §5 §6.5）：
 * 状态文案只取后端已定义的取值；可售状态从 available 派生——
 * null 是「未开售」（未建库存池），不等于「已售罄」，两者文案必须区分。
 */

describe('formatEventDateTime', () => {
  it('formats ISO timestamp as month day time', () => {
    expect(formatEventDateTime('2026-11-06T11:30:00Z')).toBe('11 月 6 日 19:30')
  })

  it('returns an empty string for an unparseable value', () => {
    expect(formatEventDateTime('not-a-date')).toBe('')
  })
})

describe('eventStatusLabel', () => {
  it('covers every backend event status without inventing new ones', () => {
    expect(Object.keys(EVENT_STATUS_LABEL_ZH).sort()).toEqual([
      'CANCELLED',
      'COMPLETED',
      'DRAFT',
      'PUBLISHED',
    ])
  })

  it('falls back for an unknown code instead of rendering undefined', () => {
    expect(eventStatusLabel('SOMETHING_ELSE' as 'PUBLISHED')).toBe('未知状态')
  })
})

describe('deriveAvailability', () => {
  it('treats a null availability as not on sale, not sold out', () => {
    expect(deriveAvailability(null)).toBe('NotOnSale')
  })

  it('treats zero availability as sold out', () => {
    expect(deriveAvailability(0)).toBe('SoldOut')
  })

  it('flags low stock at or below the threshold', () => {
    expect(deriveAvailability(3, 20)).toBe('LowStock')
    expect(deriveAvailability(20, 20)).toBe('LowStock')
    expect(deriveAvailability(21, 20)).toBe('Selling')
  })
})

describe('productToTier', () => {
  const product = (overrides: Partial<ProductListItem> = {}): ProductListItem => ({
    productCode: 'p-1',
    name: '内场 A 区',
    status: 'PUBLISHED',
    unitPrice: 168000,
    categoryId: null,
    sessionId: '9',
    poolCode: 'POOL-1',
    available: 120,
    ...overrides,
  })

  it('maps the product view onto the tier card contract', () => {
    expect(productToTier(product())).toEqual({
      productId: 'p-1',
      name: '内场 A 区',
      sessionId: '9',
      poolCode: 'POOL-1',
      unitPriceMinor: 168000,
      currency: 'CNY',
      quantityLimit: null,
      availability: 'Selling',
    })
  })

  it('keeps the pool code null (not on sale) when the backend has no pool', () => {
    expect(productToTier(product({ poolCode: null })).poolCode).toBeNull()
  })

  it('leaves quantityLimit null when the backend has not shipped purchase rules', () => {
    expect(productToTier(product()).quantityLimit).toBeNull()
  })

  it('maps the three inventory cases to distinct availability labels', () => {
    expect(productToTier(product({ available: null })).availability).toBe('NotOnSale')
    expect(productToTier(product({ available: 0 })).availability).toBe('SoldOut')
    expect(productToTier(product({ available: 5 })).availability).toBe('LowStock')
  })
})

describe('geoLine', () => {
  const geo = (overrides: Partial<import('@awn/api-client').SessionVenueView> = {}) => ({
    venueCode: 'v-1',
    venueName: '北京工人体育场',
    city: '北京',
    address: '朝阳工人体育场北路',
    latitude: 39.9308,
    longitude: 116.4435,
    ...overrides,
  })

  it('joins venue name, city and address with a dot separator', () => {
    expect(geoLine(geo())).toBe('北京工人体育场 · 北京 · 朝阳工人体育场北路')
  })

  it('skips missing parts instead of rendering blanks', () => {
    expect(geoLine(geo({ address: null }))).toBe('北京工人体育场 · 北京')
    expect(geoLine(geo({ city: null, address: null }))).toBe('北京工人体育场')
  })

  it('returns null for missing geo or an all-empty venue (no fake location)', () => {
    expect(geoLine(null)).toBeNull()
    expect(geoLine(geo({ venueName: '', city: null, address: null }))).toBeNull()
  })
})
