import { describe, expect, it } from 'vitest'
import type { ProductListItem } from '@awn/api-client'
import {
  FULFILLMENT_STATUS_LABEL_ZH,
  MAX_TICKETS_PER_LINE,
  ORDER_STATUS_LABEL_ZH,
  PAYMENT_STATUS_LABEL_ZH,
  addToCart,
  canPay,
  cartTotalQuantity,
  formatInstant,
  formatMinor,
  fulfillmentStatusLabel,
  isPaymentSettled,
  isTerminalOrder,
  maxQuantityFor,
  newestPayment,
  orderStatusBadge,
  orderStatusLabel,
  paymentChannelLabel,
  paymentInFlight,
  paymentStatusLabel,
  quoteTotals,
  setQuantity,
  toOrderLines,
  validateCart,
  type CartLine,
} from './checkout'

/**
 * 下单链路展示层契约（docs/design/ui-system.md §4.2 / docs/api/contracts.md）：
 * 金额以服务端报价为准、支付成功只来自渠道回调、结果未知不是失败。
 */

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

const cartOf = (lines: CartLine[]): CartLine[] => lines

describe('cart operations', () => {
  it('caps the purchasable quantity by available stock and the per-line limit', () => {
    expect(maxQuantityFor(product({ available: 2 }))).toBe(2)
    expect(maxQuantityFor(product({ available: 99 }))).toBe(MAX_TICKETS_PER_LINE)
    // 未建池 = 未开售，可购数量为 0
    expect(maxQuantityFor(product({ available: null }))).toBe(0)
  })

  it('adds a line and merges quantities for the same product', () => {
    let cart = addToCart([], product())
    expect(cartTotalQuantity(cart)).toBe(1)
    cart = addToCart(cart, product())
    expect(cartTotalQuantity(cart)).toBe(2)
  })

  it('never adds a tier that is not on sale or sold out', () => {
    expect(addToCart([], product({ available: null }))).toEqual([])
    expect(addToCart([], product({ available: 0 }))).toEqual([])
  })

  it('clamps quantity into range and drops the line at zero', () => {
    const cart = cartOf([{ product: product(), quantity: 1 }])
    expect(setQuantity(cart, 'p-1', 99)[0].quantity).toBe(MAX_TICKETS_PER_LINE)
    expect(setQuantity(cart, 'p-1', 0)).toEqual([])
    expect(setQuantity(cart, 'p-1', -3)).toEqual([])
  })
})

describe('toOrderLines', () => {
  it('carries pool code and snapshot fields but never a client total', () => {
    const lines = toOrderLines([{ product: product(), quantity: 2 }])
    expect(lines).toEqual([
      {
        poolCode: 'POOL-1',
        listingNo: null,
        productId: 'p-1',
        productNameSnapshot: '内场 A 区',
        sessionId: '9',
        ticketCategorySnapshot: null,
        quantity: 2,
        unitPrice: 168000,
        discountAmount: 0,
        feeAmount: 0,
      },
    ])
    expect(lines[0]).not.toHaveProperty('payAmount')
  })
})

describe('validateCart', () => {
  it('blocks an empty cart', () => {
    expect(validateCart([])).toBe('请先选择票档')
  })

  it('blocks a tier with no inventory pool as not on sale', () => {
    const cart = cartOf([{ product: product({ poolCode: null }), quantity: 1 }])
    expect(validateCart(cart)).toBe('「内场 A 区」未开售（无库存池）')
  })

  it('blocks a sold-out tier', () => {
    const cart = cartOf([{ product: product({ available: 0 }), quantity: 1 }])
    expect(validateCart(cart)).toBe('「内场 A 区」已售罄')
  })

  it('blocks a quantity above the display cap', () => {
    const cart = cartOf([{ product: product({ available: 2 }), quantity: 3 }])
    expect(validateCart(cart)).toBe('「内场 A 区」超出可购数量')
  })

  it('passes a valid cart', () => {
    expect(validateCart(cartOf([{ product: product(), quantity: 2 }]))).toBeNull()
  })
})

describe('order status labels', () => {
  it('covers every backend order status without inventing new ones', () => {
    expect(Object.keys(ORDER_STATUS_LABEL_ZH).sort()).toEqual([
      'CANCELLED',
      'EXPIRED',
      'FULFILLED',
      'FULFILLING',
      'PAID',
      'PENDING_PAYMENT',
      'REFUNDED',
      'REFUND_PENDING',
    ])
  })

  it('falls back for an unknown code instead of rendering undefined', () => {
    expect(orderStatusLabel('SOMETHING_ELSE')).toBe('未知状态')
    expect(orderStatusBadge('SOMETHING_ELSE')).toBe('')
  })

  it('marks only terminal states as terminal', () => {
    expect(isTerminalOrder('CANCELLED')).toBe(true)
    expect(isTerminalOrder('EXPIRED')).toBe(true)
    expect(isTerminalOrder('REFUNDED')).toBe(true)
    expect(isTerminalOrder('PENDING_PAYMENT')).toBe(false)
  })

  it('only allows paying while the order is pending payment', () => {
    expect(canPay('PENDING_PAYMENT')).toBe(true)
    expect(canPay('PAID')).toBe(false)
    expect(canPay('EXPIRED')).toBe(false)
  })
})

describe('payment status labels', () => {
  it('shows UNKNOWN as pending reconciliation, not as a failure', () => {
    expect(paymentStatusLabel('UNKNOWN')).toBe('结果待核实（对账中）')
    expect(paymentInFlight('UNKNOWN')).toBe(true)
    expect(paymentStatusLabel('FAILED')).toBe('支付失败')
    expect(paymentInFlight('FAILED')).toBe(false)
  })

  it('covers every payment status the state machine defines', () => {
    expect(Object.keys(PAYMENT_STATUS_LABEL_ZH).sort()).toEqual([
      'CREATED',
      'FAILED',
      'PARTIALLY_REFUNDED',
      'PENDING',
      'REFUNDED',
      'REFUNDING',
      'SUCCEEDED',
      'UNKNOWN',
    ])
  })

  it('returns the newest payment and null when there is none', () => {
    const first = { paymentNo: 'P-1', orderNo: 'O-1', channel: 'sandbox', channelTxnNo: null, status: 'FAILED', currency: 'CNY', amount: 1, paidAt: null }
    const second = { ...first, paymentNo: 'P-2', status: 'SUCCEEDED' }
    expect(newestPayment([first, second])?.paymentNo).toBe('P-2')
    expect(newestPayment([])).toBeNull()
  })

  it('labels the sandbox channel and passes unknown channels through', () => {
    expect(paymentChannelLabel('sandbox')).toBe('沙箱支付')
    expect(paymentChannelLabel('wechat')).toBe('wechat')
  })

  it('counts an order as paid only when a channel-confirmed payment exists', () => {
    const base = {
      paymentNo: 'P-1',
      orderNo: 'O-1',
      channel: 'sandbox',
      channelTxnNo: 'TXN-1',
      status: 'PENDING',
      currency: 'CNY',
      amount: 336000,
      paidAt: null,
    }
    expect(isPaymentSettled([])).toBe(false)
    expect(isPaymentSettled([base])).toBe(false)
    expect(isPaymentSettled([base, { ...base, paymentNo: 'P-2', status: 'SUCCEEDED' }])).toBe(
      true,
    )
    // 结果待核实不是成功，也不当失败展示
    expect(isPaymentSettled([{ ...base, status: 'UNKNOWN' }])).toBe(false)
  })
})

describe('fulfillment status labels', () => {
  it('distinguishes seller delivery from platform verification', () => {
    expect(fulfillmentStatusLabel('PENDING_SELLER')).toBe('待卖家交付')
    expect(fulfillmentStatusLabel('PENDING_VERIFICATION')).toBe('待平台核验')
    expect(Object.keys(FULFILLMENT_STATUS_LABEL_ZH)).toHaveLength(7)
    expect(fulfillmentStatusLabel('WHAT')).toBe('未知状态')
  })
})

describe('amount formatting', () => {
  it('renders minor units as yuan with two decimals', () => {
    expect(formatMinor(168000)).toBe('1,680.00')
    expect(formatMinor(0)).toBe('0.00')
  })

  it('shows a placeholder instead of a fake zero for a missing quote', () => {
    expect(formatMinor(null)).toBe('—')
    expect(quoteTotals(null).pay).toBe('—')
  })

  it('prefers the server quote values for the totals block', () => {
    expect(
      quoteTotals({
        currency: 'CNY',
        totalAmount: 336000,
        discountAmount: 2000,
        feeAmount: 4800,
        payAmount: 338800,
      }),
    ).toEqual({ total: '3,360.00', discount: '20.00', fee: '48.00', pay: '3,388.00' })
  })

  it('formats an instant and stays silent for missing or broken values', () => {
    expect(formatInstant('2026-11-06T12:30:00Z')).not.toBe('')
    expect(formatInstant(null)).toBe('')
    expect(formatInstant('not-a-date')).toBe('')
  })
})