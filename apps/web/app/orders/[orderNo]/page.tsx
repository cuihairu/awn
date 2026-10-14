'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  cancelOrder,
  createPayment,
  getOrder,
  getFulfillment,
  listPayments,
  type FulfillmentView,
  type OrderView,
  type PaymentView,
} from '@awn/api-client'
import {
  PAYMENT_CHANNELS,
  canPay,
  formatOrderExpiry,
  formatMinor,
  fulfillmentStatusLabel,
  isPaymentSettled,
  isTerminalOrder,
  newestPayment,
  orderStatusBadge,
  orderStatusLabel,
  paymentChannelLabel,
  paymentInFlight,
  paymentStatusLabel,
} from '../../../lib/checkout'

/**
 * 订单页 /orders/{orderNo}（原型 web/order-*.html）：支付、支付历史、取消与履约单。
 *
 * 支付口径（docs/api/contracts.md + payment-state-machine）：
 * - 发起支付只提交渠道，金额与币种由服务端取订单应付快照；
 * - 支付成功只认渠道回调，页面不据前端参数判成功——沙箱渠道当前没有 HTTP 回调通道，
 *   所以支付单停在「支付中」，页面如实呈现并给出手动刷新；
 * - 结果未知（UNKNOWN）走对账，不能当失败展示，也不允许重复发起支付；
 * - 取消只对未支付订单开放，库存预留随之释放（服务端状态机裁决）。
 */
export default function OrderDetailPage() {
  const params = useParams<{ orderNo: string }>()
  const orderNo = params.orderNo

  const [order, setOrder] = useState<OrderView | null>(null)
  const [payments, setPayments] = useState<PaymentView[]>([])
  const [fulfillment, setFulfillment] = useState<FulfillmentView | null>(null)
  const [channel, setChannel] = useState(PAYMENT_CHANNELS[0].key)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [failed, setFailed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const load = useCallback(async () => {
    const orderData = await getOrder(orderNo)
    const paymentList = await listPayments(orderNo)
    setOrder(orderData)
    setPayments(paymentList)
    // 履约单只在支付后才存在：404 表示尚未进入履约，不是错误
    try {
      setFulfillment(await getFulfillment(orderNo))
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 404)) throw err
      setFulfillment(null)
    }
  }, [orderNo])

  useEffect(() => {
    let alive = true
    setLoading(true)
    load()
      .catch((err: unknown) => {
        if (!alive) return
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
        } else {
          setFailed(true)
        }
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [load])

  const reportError = (prefix: string) => (err: unknown) => {
    setActionError(
      err instanceof ApiError ? `${prefix}（${err.code}）：${err.message}` : `${prefix}，请稍后重试。`,
    )
  }

  const pay = useCallback(() => {
    setBusy(true)
    setActionError('')
    createPayment(orderNo, channel)
      .then(() => load())
      .catch(reportError('发起支付失败'))
      .finally(() => setBusy(false))
  }, [channel, load, orderNo])

  const cancel = useCallback(() => {
    setBusy(true)
    setActionError('')
    cancelOrder(orderNo)
      .then(() => load())
      .catch(reportError('取消失败'))
      .finally(() => setBusy(false))
  }, [load, orderNo])

  if (loading) {
    return (
      <main className="page">
        <p className="state">加载中…</p>
      </main>
    )
  }

  if (notFound) {
    return (
      <main className="page">
        <p className="state state--error">
          订单不存在。<Link href="/">返回发现列表</Link>
        </p>
      </main>
    )
  }

  if (failed || !order) {
    return (
      <main className="page">
        <p className="state state--error">订单加载失败，请稍后重试。</p>
      </main>
    )
  }

  const latest = newestPayment(payments)
  // 结果未定的支付单在途，避免重复发起导致重复扣款
  const payBusy = paymentInFlight(latest?.status ?? '')

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">发现</Link>
        <span aria-hidden>/</span>
        <span>订单 {order.orderNo}</span>
      </nav>

      <header className="page-head">
        <h1>订单 {order.orderNo}</h1>
        <p className="muted">
          <span className={`badge ${orderStatusBadge(order.status)}`}>
            {orderStatusLabel(order.status)}
          </span>
          {order.expiresAt && canPay(order.status) && (
            <span className="num"> · 支付截止 {formatOrderExpiry(order.expiresAt)}</span>
          )}
        </p>
      </header>

      <section className="detail-section">
        <h2>订单信息</h2>
        <table className="table">
          <thead>
            <tr>
              <th>票档</th>
              <th className="num">单价</th>
              <th className="num">数量</th>
              <th className="num">小计</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.productId}>
                <td>{item.productNameSnapshot}</td>
                <td className="num">{formatMinor(item.unitPrice)}</td>
                <td className="num">{item.quantity}</td>
                <td className="num">{formatMinor(item.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="row-between">
          <span className="muted">应付金额</span>
          <span className="price price-yuan num">{formatMinor(order.payAmount)}</span>
        </div>
        {order.discountAmount > 0 && (
          <p className="hint">优惠 {formatMinor(order.discountAmount)}</p>
        )}
      </section>

      <section className="detail-section">
        <h2>支付</h2>
        {canPay(order.status) ? (
          <>
            <div className="field">
              <label htmlFor="channel">支付渠道</label>
              <select
                id="channel"
                value={channel}
                onChange={(e) => setChannel(e.target.value)}
              >
                {PAYMENT_CHANNELS.map((c) => (
                  <option key={c.key} value={c.key}>
                    {c.label}
                  </option>
                ))}
              </select>
              <span className="hint">
                金额与币种取订单应付快照，页面只提交渠道；支付结果以渠道回调为准。
              </span>
            </div>
            <button type="button" className="btn" disabled={busy || payBusy} onClick={pay}>
              {busy ? '处理中…' : '发起支付'}
            </button>
            {payBusy && (
              <p className="hint">已有在途支付单，完成或核实前不重复发起，避免重复扣款。</p>
            )}
          </>
        ) : (
          <p className="hint">订单当前状态无需支付。</p>
        )}

        {payments.length === 0 ? (
          <p className="hint">暂无支付记录。</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>支付单</th>
                <th>渠道</th>
                <th className="num">金额</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.paymentNo}>
                  <td className="num">{p.paymentNo}</td>
                  <td>{paymentChannelLabel(p.channel)}</td>
                  <td className="num">{formatMinor(p.amount)}</td>
                  <td>
                    <span
                      className={`badge ${
                        p.status === 'SUCCEEDED'
                          ? 'badge--success'
                          : p.status === 'FAILED'
                            ? 'badge--danger'
                            : ''
                      }`}
                    >
                      {paymentStatusLabel(p.status)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {isPaymentSettled(payments) && (
          <p className="hint">渠道已确认收款；订单状态以刷新结果为准，页面不据前端参数判定成功。</p>
        )}
        <button
          type="button"
          className="btn btn--ghost"
          disabled={busy}
          onClick={() => {
            setBusy(true)
            load()
              .catch(reportError('刷新失败'))
              .finally(() => setBusy(false))
          }}
        >
          刷新状态
        </button>
      </section>

      {fulfillment && (
        <section className="detail-section">
          <h2>履约单</h2>
          <p className="row-between">
            <span className="num">{fulfillment.fulfillmentNo}</span>
            <span className="badge">{fulfillmentStatusLabel(fulfillment.status)}</span>
          </p>
        </section>
      )}

      {actionError && (
        <p className="state state--error" role="alert">
          {actionError}
        </p>
      )}

      {!isTerminalOrder(order.status) && (
        <section className="detail-section">
          <h2>取消订单</h2>
          <p className="hint">取消后库存预留立即释放，不可恢复。</p>
          <button type="button" className="btn btn--ghost" disabled={busy} onClick={cancel}>
            {busy ? '处理中…' : '取消订单'}
          </button>
        </section>
      )}
    </main>
  )
}