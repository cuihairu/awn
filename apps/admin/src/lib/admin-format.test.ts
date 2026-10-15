import { describe, expect, it } from 'vitest'
import {
  availableRatio,
  disputeBadgeClass,
  disputeReasonZh,
  disputeResolutionZh,
  disputeStatusZh,
  eventStatusZh,
  filterOrdersByStatus,
  formatAdminTime,
  isLowStock,
  minorToYuan,
  orderBadgeClass,
  orderStatusZh,
  parseBounded,
  parseYuanToFen,
  succeededPayments,
  validateRefundYuan,
  validateVenueForm,
  type VenueFormInput,
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

  it('maps dispute statuses to zh labels with fallback', () => {
    expect(disputeStatusZh('OPEN')).toBe('待受理')
    expect(disputeStatusZh('UNDER_REVIEW')).toBe('受理中')
    expect(disputeStatusZh('RESOLVED')).toBe('已裁决')
    expect(disputeStatusZh('WITHDRAWN')).toBe('已撤回')
    expect(disputeStatusZh('SOMETHING_NEW')).toBe('未知状态')
  })

  it('maps dispute statuses to badge modifier classes', () => {
    expect(disputeBadgeClass('OPEN')).toBe('badge--warning')
    expect(disputeBadgeClass('UNDER_REVIEW')).toBe('badge--info')
    expect(disputeBadgeClass('RESOLVED')).toBe('badge--success')
    expect(disputeBadgeClass('WITHDRAWN')).toBe('')
  })

  it('maps dispute reasons to zh labels with fallback', () => {
    expect(disputeReasonZh('NOT_DELIVERED')).toBe('未交付')
    expect(disputeReasonZh('TICKET_INVALID')).toBe('票券无效')
    expect(disputeReasonZh('DESCRIPTION_MISMATCH')).toBe('与描述不符')
    expect(disputeReasonZh('OTHER')).toBe('其他')
    expect(disputeReasonZh('SOMETHING_NEW')).toBe('未知原因')
  })

  it('maps dispute resolutions to zh labels with fallback', () => {
    expect(disputeResolutionZh('REJECTED')).toBe('裁决：不支持')
    expect(disputeResolutionZh('REFUND_APPROVED')).toBe('裁决：退款')
    expect(disputeResolutionZh('REDELIVER_APPROVED')).toBe('裁决：重新交付')
    expect(disputeResolutionZh('SOMETHING_NEW')).toBe('未知结论')
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

describe('refund execution helpers', () => {
  it('parses yuan input to minor units, rejecting malformed text', () => {
    expect(parseYuanToFen('388')).toBe(38800)
    expect(parseYuanToFen(' 388.5 ')).toBe(38850)
    expect(parseYuanToFen('0.05')).toBe(5)
    // 非法输入（负数 / 三位小数 / 空串 / 文字）不猜值，返回 null
    expect(parseYuanToFen('-1')).toBeNull()
    expect(parseYuanToFen('1.234')).toBeNull()
    expect(parseYuanToFen('')).toBeNull()
    expect(parseYuanToFen('abc')).toBeNull()
  })

  it('keeps only succeeded payments as refund sources', () => {
    const payment = (status: string) => ({
      paymentNo: `P-${status}`,
      orderNo: 'ORD-1',
      channel: 'sandbox',
      channelTxnNo: null,
      status,
      currency: 'CNY',
      amount: 10000,
      paidAt: null,
      retryable: false,
    })
    const sources = succeededPayments([
      payment('SUCCEEDED'),
      payment('FAILED'),
      payment('PENDING'),
      payment('SUCCEEDED'),
    ])
    expect(sources.map((p) => p.paymentNo)).toEqual(['P-SUCCEEDED', 'P-SUCCEEDED'])
  })

  it('validates the refund amount against the adjudicated cap', () => {
    // 裁决上限 ¥100.00
    expect(validateRefundYuan('100', 10000)).toBeNull()
    expect(validateRefundYuan('0.01', 10000)).toBeNull()
    expect(validateRefundYuan('100.01', 10000)).toContain('不超过')
    expect(validateRefundYuan('0', 10000)).toContain('大于 0')
    expect(validateRefundYuan('abc', 10000)).toContain('大于 0')
    // 无裁决金额（不该出现）时只做非零校验
    expect(validateRefundYuan('120', null)).toBeNull()
  })
})

describe('venue config helpers', () => {
  const form = (overrides: Partial<VenueFormInput> = {}): VenueFormInput => ({
    name: '北京工人体育场',
    city: '北京',
    address: '朝阳工人体育场北路',
    latitude: '39.9308',
    longitude: '116.4435',
    mapImageUrl: 'https://assets.awn.site/venues/gongti.png',
    mapMarkerX: '62',
    mapMarkerY: '40',
    ...overrides,
  })

  it('parses bounded numbers, treating blank as absent and out-of-range as invalid', () => {
    expect(parseBounded(' 39.93 ', -90, 90)).toBe(39.93)
    expect(parseBounded('', -90, 90)).toBeNull()
    expect(parseBounded('abc', -90, 90)).toBeNull()
    expect(parseBounded('-91', -90, 90)).toBeNull()
    expect(parseBounded('101', 0, 100)).toBeNull()
    // 边界值合法
    expect(parseBounded('-90', -90, 90)).toBe(-90)
    expect(parseBounded('0', 0, 100)).toBe(0)
    expect(parseBounded('100', 0, 100)).toBe(100)
  })

  it('accepts a fully filled venue form', () => {
    expect(validateVenueForm(form())).toBeNull()
  })

  it('requires the venue name', () => {
    expect(validateVenueForm(form({ name: '  ' }))).toContain('场馆名称必填')
  })

  it('rejects half coordinates and out-of-range values', () => {
    expect(validateVenueForm(form({ longitude: '' }))).toContain('成对')
    expect(validateVenueForm(form({ latitude: '' }))).toContain('成对')
    expect(validateVenueForm(form({ latitude: '91' }))).toContain('-90 到 90')
    expect(validateVenueForm(form({ longitude: '-181' }))).toContain('-180 到 180')
    expect(validateVenueForm(form({ longitude: 'abc' }))).toContain('-180 到 180')
  })

  it('rejects half map markers and out-of-range percentages', () => {
    expect(validateVenueForm(form({ mapMarkerY: '' }))).toContain('成对')
    expect(validateVenueForm(form({ mapMarkerX: '' }))).toContain('成对')
    expect(validateVenueForm(form({ mapMarkerX: '-1' }))).toContain('0 到 100')
    expect(validateVenueForm(form({ mapMarkerY: '101' }))).toContain('0 到 100')
  })

  it('accepts optional fields left blank (full-coverage PUT sends null)', () => {
    expect(
      validateVenueForm(
        form({
          city: '',
          address: '',
          latitude: '',
          longitude: '',
          mapImageUrl: '',
          mapMarkerX: '',
          mapMarkerY: '',
        }),
      ),
    ).toBeNull()
  })
})
