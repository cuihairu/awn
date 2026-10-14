import { describe, expect, it } from 'vitest'
import {
  availableRatio,
  eventStatusZh,
  filterOrdersByStatus,
  formatAdminTime,
  isLowStock,
  minorToYuan,
  orderBadgeClass,
  orderStatusZh,
} from './admin-format'
import type { AdminOrder } from './admin-client'

describe('admin-format 展示口径', () => {
  it('maps all order statuses to zh labels with fallback', () => {
    expect(orderStatusZh('PENDING_PAYMENT')).toBe('待支付')
    expect(orderStatusZh('REFUND_PENDING')).toBe('退款中')
    expect(orderStatusZh('EXPIRED')).toBe('已超时')
    expect(orderStatusZh('SOMETHING_NEW')).toBe('未知状态')
  })

  it('maps statuses to badge modifier classes', () => {
    expect(orderBadgeClass('PAID')).toBe('badge--success')
    expect(orderBadgeClass('PENDING_PAYMENT')).toBe('badge--warning')
    expect(orderBadgeClass('REFUNDED')).toBe('')
  })

  it('maps event statuses including fallback', () => {
    expect(eventStatusZh('DRAFT')).toBe('草稿')
    expect(eventStatusZh('PUBLISHED')).toBe('已发布')
    expect(eventStatusZh('NOPE')).toBe('未知状态')
  })

  it('formats minor units as yuan with grouping', () => {
    expect(minorToYuan(388000)).toBe('3,880.00')
    expect(minorToYuan(380)).toBe('3.80')
    expect(minorToYuan(0)).toBe('0.00')
    expect(minorToYuan(Number.NaN)).toBe('')
  })

  it('formats iso time as MM-dd HH:mm and tolerates bad input', () => {
    const formatted = formatAdminTime('2026-10-13T09:47:00+08:00')
    expect(formatted).toMatch(/^\d{2}-\d{2} \d{2}:\d{2}$/)
    expect(formatAdminTime('not-a-date')).toBe('')
    expect(formatAdminTime(null)).toBe('')
  })

  it('derives availability ratio with clamping', () => {
    expect(availableRatio({ totalQuantity: 100, available: 20 })).toBe(0.2)
    expect(availableRatio({ totalQuantity: 0, available: 0 })).toBe(0)
    expect(availableRatio({ totalQuantity: 100, available: -5 })).toBe(0)
    expect(availableRatio({ totalQuantity: 100, available: 150 })).toBe(1)
  })

  it('flags low stock strictly below the 20 percent line', () => {
    expect(isLowStock(0.199)).toBe(true)
    expect(isLowStock(0.2)).toBe(false)
    expect(isLowStock(0)).toBe(true)
  })

  it('filters orders by status tab', () => {
    const orders: AdminOrder[] = [
      { ...baseOrder, orderNo: 'A', status: 'PENDING_PAYMENT' },
      { ...baseOrder, orderNo: 'B', status: 'PAID' },
      { ...baseOrder, orderNo: 'C', status: 'PAID' },
    ]
    expect(filterOrdersByStatus(orders, 'ALL')).toHaveLength(3)
    expect(filterOrdersByStatus(orders, 'PAID').map((o) => o.orderNo)).toEqual(['B', 'C'])
    expect(filterOrdersByStatus(orders, 'REFUNDED')).toHaveLength(0)
  })
})

const baseOrder: AdminOrder = {
  orderNo: 'AWN-X',
  status: 'PENDING_PAYMENT',
  currency: 'CNY',
  totalAmount: 0,
  discountAmount: 0,
  feeAmount: 0,
  payAmount: 0,
  expiresAt: null,
  items: [],
}
