import { describe, expect, it } from 'vitest'
import type { OrderView, PaymentView, ProductListItem } from '@awn/api-client'
import {
  FULFILLMENT_STATUS_LABEL_ZH,
  MAX_TICKETS_PER_LINE,
  ORDER_STATUS_LABEL_ZH,
  PAYMENT_STATUS_LABEL_ZH,
  addToCart,
  attendeeRowKey,
  attendeeRowsFor,
  attendeesLabel,
  canPay,
  canRetryPayment,
  cartTotalQuantity,
  formatCountdown,
  formatMinor,
  fulfillmentStatusLabel,
  isPaymentSettled,
  isTerminalOrder,
  maxQuantityFor,
  newestPayment,
  orderStatusBadge,
  orderStatusLabel,
  payCountdownSeconds,
  paymentChannelLabel,
  paymentInFlight,
  paymentStatusLabel,
  quoteTotals,
  setAttendeeRow,
  setQuantity,
  toOrderLines,
  toSeatOrderLines,
  validateAttendees,
  validateCart,
  validateCheckout,
  validateSeatCart,
  seatCartQuantity,
  type AttendeeInput,
  type CartLine,
  type SeatCartLine,
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
        seatNo: null,
        productId: 'p-1',
        productNameSnapshot: '内场 A 区',
        sessionId: '9',
        ticketCategorySnapshot: null,
        quantity: 2,
        unitPrice: 168000,
        discountAmount: 0,
        feeAmount: 0,
        // 未填实名 → null（后端把空数组判为数量不符 400，绝不发空数组）
        attendees: null,
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

describe('seat cart', () => {
  const seatProduct = (overrides: Partial<ProductListItem> = {}): ProductListItem =>
    product({ productCode: 'p-seat', name: '池畔 A 区', poolCode: null, available: null, ...overrides })

  it('turns each picked seat into its own quantity-1 order line', () => {
    const lines = toSeatOrderLines([{ product: seatProduct(), seatNos: ['SEAT-1', 'SEAT-2'] }])
    expect(lines).toHaveLength(2)
    expect(lines[0]).toMatchObject({
      seatNo: 'SEAT-1',
      poolCode: null,
      listingNo: null,
      quantity: 1,
      productId: 'p-seat',
      unitPrice: 168000,
    })
    expect(lines[1].seatNo).toBe('SEAT-2')
  })

  it('does not require a pool code for seat lines', () => {
    // 座位型不碰库存池：无池商品走座位行，不受「未开售（无库存池）」约束
    expect(validateSeatCart([{ product: seatProduct(), seatNos: ['SEAT-1'] }])).toBeNull()
  })

  it('blocks checkout when both carts are empty', () => {
    expect(validateCheckout([], [])).toBe('请先选择票档或座位')
  })

  it('passes when only the seat cart has seats', () => {
    expect(validateCheckout([], [{ product: seatProduct(), seatNos: ['SEAT-1'] }])).toBeNull()
  })

  it('still enforces quantity-line rules together with seat lines', () => {
    const cart = cartOf([{ product: product({ available: 0 }), quantity: 1 }])
    const seatCart: SeatCartLine[] = [{ product: seatProduct(), seatNos: ['SEAT-1'] }]
    expect(validateCheckout(cart, seatCart)).toBe('「内场 A 区」已售罄')
  })

  it('counts seat lines into the total ticket count', () => {
    expect(seatCartQuantity([{ product: seatProduct(), seatNos: ['S1', 'S2'] }])).toBe(2)
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
    const first = { paymentNo: 'P-1', orderNo: 'O-1', channel: 'sandbox', channelTxnNo: null, status: 'FAILED', currency: 'CNY', amount: 1, paidAt: null, retryable: true }
    const second = { ...first, paymentNo: 'P-2', status: 'SUCCEEDED', retryable: false }
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
      retryable: false,
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

describe('payment retry', () => {
  const payment = (overrides: Partial<PaymentView> = {}): PaymentView => ({
    paymentNo: 'PAY-1',
    orderNo: 'AWN-1',
    channel: 'sandbox',
    channelTxnNo: null,
    status: 'FAILED',
    currency: 'CNY',
    amount: 336000,
    paidAt: null,
    retryable: false,
    ...overrides,
  })

  it('offers a retry only when the backend marks the failed payment retryable', () => {
    expect(canRetryPayment(payment({ status: 'FAILED', retryable: true }))).toBe(true)
    // 失败但订单已过截止期：后端置 false，前端不自行推断可重试
    expect(canRetryPayment(payment({ status: 'FAILED', retryable: false }))).toBe(false)
    expect(canRetryPayment(payment({ status: 'SUCCEEDED', retryable: true }))).toBe(false)
    expect(canRetryPayment(payment({ status: 'PENDING', retryable: true }))).toBe(false)
    expect(canRetryPayment(null)).toBe(false)
  })
})

describe('pay countdown', () => {
  const order = (overrides: Partial<OrderView> = {}): OrderView => ({
    orderNo: 'AWN-1',
    status: 'PENDING_PAYMENT',
    currency: 'CNY',
    totalAmount: 336000,
    discountAmount: 0,
    feeAmount: 0,
    payAmount: 336000,
    expiresAt: '2026-10-15T12:00:00Z',
    items: [],
    serverTime: '2026-10-15T11:50:00Z',
    payExpiresInSeconds: 600,
    ...overrides,
  })

  const at = (iso: string) => Date.parse(iso)

  it('counts down from the server-time anchor, not the local clock', () => {
    const o = order({ serverTime: '2026-10-15T11:50:00Z', payExpiresInSeconds: 600 })
    expect(payCountdownSeconds(o, at('2026-10-15T11:50:00Z'))).toBe(600)
    expect(payCountdownSeconds(o, at('2026-10-15T11:55:00Z'))).toBe(300)
    expect(payCountdownSeconds(o, at('2026-10-15T11:59:30Z'))).toBe(30)
  })

  it('floors at zero and never counts down a non-payable order', () => {
    const o = order({ serverTime: '2026-10-15T11:50:00Z', payExpiresInSeconds: 60 })
    expect(payCountdownSeconds(o, at('2026-10-15T12:30:00Z'))).toBe(0)
    // 非待支付状态恒为 0，不看前端算出的剩余时间
    expect(payCountdownSeconds(order({ status: 'PAID' }), at('2026-10-15T11:50:00Z'))).toBe(0)
    expect(
      payCountdownSeconds(order({ status: 'EXPIRED', payExpiresInSeconds: 0 }), at('2026-10-15T11:50:00Z')),
    ).toBe(0)
  })

  it('drops an unparseable server time instead of fabricating a countdown', () => {
    expect(payCountdownSeconds(order({ serverTime: 'not-a-date' }), Date.now())).toBe(0)
  })

  it('formats mm:ss and adds an hour field past one hour', () => {
    expect(formatCountdown(0)).toBe('00:00')
    expect(formatCountdown(-5)).toBe('00:00')
    expect(formatCountdown(59)).toBe('00:59')
    expect(formatCountdown(600)).toBe('10:00')
    expect(formatCountdown(3661)).toBe('01:01:01')
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

})

/**
 * 观演人实名（docs/api/contracts.md「观演人实名（一票一证）」）：
 * 一票一证（提供时数量必须等于行数量）、同场次同证件限 1 张、姓名/证件号长度上限、
 * 不填实名则整行不带 attendees（空数组会被后端判数量不符 400）。
 */
describe('attendee rows', () => {
  const attendee = (overrides: Partial<AttendeeInput> = {}): AttendeeInput => ({
    name: '张三',
    idNumber: '11010119900307861X',
    ...overrides,
  })

  it('keys a quantity line by product and a seat line by product plus seat', () => {
    expect(attendeeRowKey('p-1')).toBe('p-1')
    expect(attendeeRowKey('p-1', 'SEAT-A-1-05')).toBe('p-1:SEAT-A-1-05')
    // 同票档两个座位互不覆盖
    expect(attendeeRowKey('p-1', 'SEAT-A-1-06')).not.toBe(attendeeRowKey('p-1', 'SEAT-A-1-05'))
  })

  it('pads rows to the ticket count and keeps what the buyer already typed', () => {
    const one = { 'p-1': [attendee()] }
    expect(attendeeRowsFor(one, 'p-1', 2)).toEqual([attendee(), { name: '', idNumber: '' }])
    expect(attendeeRowsFor(one, 'p-1', 1)).toEqual([attendee()])
    expect(attendeeRowsFor(one, 'p-2', 2)).toEqual([
      { name: '', idNumber: '' },
      { name: '', idNumber: '' },
    ])
  })

  it('updates a single field without mutating the record', () => {
    const rows = { 'p-1': [attendee(), { name: '', idNumber: '' }] }
    const next = setAttendeeRow(rows, 'p-1', 1, { name: '李四' })
    expect(next['p-1'][1]).toEqual({ name: '李四', idNumber: '' })
    expect(rows['p-1'][1]).toEqual({ name: '', idNumber: '' })
  })

  it('accepts an all-empty form as no real name (backend treats attendees as optional)', () => {
    expect(validateAttendees({}, cartOf([{ product: product(), quantity: 2 }]), [])).toBeNull()
    // 有行但全空 = 没填，同样视为不提交实名
    const blank = { 'p-1': [{ name: '', idNumber: '' }, { name: '', idNumber: '' }] }
    expect(validateAttendees(blank, cartOf([{ product: product(), quantity: 2 }]), [])).toBeNull()
  })

  it('requires every row once the buyer starts filling the form', () => {
    const cart = cartOf([{ product: product(), quantity: 2 }])
    const partial = { 'p-1': [attendee(), { name: '', idNumber: '' }] }
    expect(validateAttendees(partial, cart, [])).toContain('观演人 2')
    // 只填了姓名没填证件号同样阻塞
    const half = { 'p-1': [attendee(), { name: '李四', idNumber: '' }] }
    expect(validateAttendees(half, cart, [])).toContain('证件号')
  })

  it('checks seat rows the same way, one attendee per seat', () => {
    const seatCart: SeatCartLine[] = [{ product: product(), seatNos: ['SEAT-A-1-05'] }]
    const filled = { 'p-1:SEAT-A-1-05': [attendee()] }
    expect(validateAttendees(filled, [], seatCart)).toBeNull()
    const partial = { 'p-1:SEAT-A-1-05': [{ name: '', idNumber: '11010119900307861X' }] }
    expect(validateAttendees(partial, [], seatCart)).toContain('观演人')
  })

  it('rejects duplicate ids inside one session but allows them without a session', () => {
    const cart = cartOf([{ product: product(), quantity: 1 }])
    const dup = {
      'p-1': [attendee()],
      'p-2': [attendee()],
    }
    const two = cartOf([
      { product: product(), quantity: 1 },
      { product: product({ productCode: 'p-2' }), quantity: 1 },
    ])
    expect(validateAttendees(dup, two, [])).toContain('同一证件限 1 张')
    // 无场次（sessionId 空）不与后端同场次唯一口径冲突，不前端误判
    const noSession = cartOf([
      { product: product({ sessionId: '' }), quantity: 1 },
      { product: product({ productCode: 'p-2', sessionId: '' }), quantity: 1 },
    ])
    expect(validateAttendees(dup, noSession, [])).toBeNull()
  })

  it('enforces the backend field length caps', () => {
    const cart = cartOf([{ product: product(), quantity: 1 }])
    const longName = { 'p-1': [attendee({ name: '张'.repeat(65) })] }
    expect(validateAttendees(longName, cart, [])).toContain('64')
    const longId = { 'p-1': [attendee({ idNumber: '1'.repeat(33) })] }
    expect(validateAttendees(longId, cart, [])).toContain('32')
  })

  it('attaches trimmed attendees to quantity lines and null when left empty', () => {
    const rows = { 'p-1': [attendee({ name: ' 张三 ' })] }
    const lines = toOrderLines(cartOf([{ product: product(), quantity: 1 }]), rows)
    expect(lines[0].attendees).toEqual([{ name: '张三', idNumber: '11010119900307861X' }])
    // 不填实名整行不带 attendees：空数组会被后端按数量不符拒绝
    expect(toOrderLines(cartOf([{ product: product(), quantity: 1 }]), {})[0].attendees).toBeNull()
    // 填了一半（前端已阻塞）也不发非法载荷
    const half = { 'p-1': [{ name: '张三', idNumber: '' }] }
    expect(toOrderLines(cartOf([{ product: product(), quantity: 1 }]), half)[0].attendees).toBeNull()
  })

  it('binds one attendee to each seat line', () => {
    const seatCart: SeatCartLine[] = [
      { product: product(), seatNos: ['SEAT-A-1-05', 'SEAT-A-1-06'] },
    ]
    const rows = {
      'p-1:SEAT-A-1-05': [attendee()],
      'p-1:SEAT-A-1-06': [attendee({ name: '李四', idNumber: '310101199502140029' })],
    }
    const lines = toSeatOrderLines(seatCart, rows)
    expect(lines.map((l) => [l.seatNo, l.attendees?.[0].name])).toEqual([
      ['SEAT-A-1-05', '张三'],
      ['SEAT-A-1-06', '李四'],
    ])
  })

  it('renders masked attendees as returned by the server, never re-deriving them', () => {
    expect(
      attendeesLabel([
        { name: '张三', idNumber: '110***********861X' },
        { name: '李四', idNumber: '310***********0029' },
      ]),
    ).toBe('张三 110***********861X、李四 310***********0029')
    expect(attendeesLabel([])).toBe('')
    expect(attendeesLabel(null)).toBe('')
  })
})