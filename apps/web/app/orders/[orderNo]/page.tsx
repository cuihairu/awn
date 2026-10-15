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
  listOrderDisputes,
  listPayments,
  listPaymentChannels,
  openDispute,
  retryPayment,
  withdrawDispute,
  type DisputeReason,
  type DisputeRequestType,
  type DisputeView,
  type FulfillmentView,
  type OrderView,
  type PaymentView,
} from '@awn/api-client'
import {
  DEMO_USER_KEY,
  canPay,
  canRetryPayment,
  formatCountdown,
  formatMinor,
  formatOrderExpiry,
  fulfillmentStatusLabel,
  isPaymentSettled,
  isTerminalOrder,
  newestPayment,
  orderStatusBadge,
  orderStatusLabel,
  payCountdownSeconds,
  paymentChannelLabel,
  paymentInFlight,
  paymentStatusLabel,
} from '../../../lib/checkout'
import {
  DISPUTE_EVIDENCE_DOMAINS,
  canOpenDispute,
  canWithdrawDispute,
  disputeReasonLabel,
  disputeResolutionLabel,
  disputeStatusBadge,
  disputeStatusLabel,
  hasInFlightDispute,
  parseYuanToFen,
  validateDisputeForm,
} from '../../../lib/disputes'

/** 争议操作失败文案：409 冲突给出可读解释，其余按错误信封展示 */
function disputeErrorText(prefix: string) {
  return (err: unknown) => {
    if (err instanceof ApiError) {
      if (err.code === 'DISPUTE_CONFLICT') {
        return `${prefix}：该订单已有在途争议，或与订单状态不符（后端已拒绝）。`
      }
      return `${prefix}（${err.code}）：${err.message}`
    }
    return `${prefix}，请稍后重试。`
  }
}

/**
 * 订单页 /orders/{orderNo}（原型 web/order-*.html）：支付、支付历史、取消与履约单。
 *
 * 支付口径（docs/api/contracts.md + payment-state-machine）：
 * - 发起支付只提交渠道，金额与币种由服务端取订单应付快照；
 * - 支付成功只认渠道回调，页面不据前端参数判成功——沙箱渠道当前没有 HTTP 回调通道，
 *   所以支付单停在「支付中」，页面如实呈现并给出手动刷新；
 * - 结果未知（UNKNOWN）走对账，不能当失败展示，也不允许重复发起支付；
 * - 失败单重试 / 换渠道只认后端 retryable，渠道编码取 GET /payments/channels，不硬编码；
 * - 支付倒计时以 serverTime 为锚点本机推算，是否超时只认后端结论；
 * - 取消只对未支付订单开放，库存预留随之释放（服务端状态机裁决）。
 *
 * 争议口径（docs/api/contracts.md「争议端点」）：
 * - 买家面只有开单 / 列表 / 撤回；受理与裁决在 /api/v1/admin/disputes（X-Admin-Token），
 *   此页不放任何裁决入口；
 * - 开单守卫（订单状态、在途争议、非下单人）一律由后端 409 裁决，前端只做同口径自检提示；
 * - 裁决只记结论不动钱（退款走既有退款流），页面如实呈现结论文案。
 */
export default function OrderDetailPage() {
  const params = useParams<{ orderNo: string }>()
  const orderNo = params.orderNo

  const [order, setOrder] = useState<OrderView | null>(null)
  const [payments, setPayments] = useState<PaymentView[]>([])
  const [fulfillment, setFulfillment] = useState<FulfillmentView | null>(null)
  /** 可用渠道编码只认后端清单；加载完成前为空，不硬编码兜底 */
  const [channels, setChannels] = useState<string[]>([])
  const [channel, setChannel] = useState('')
  const [now, setNow] = useState(() => Date.now())
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

  // 渠道编码由后端已注册网关决定，前端不硬编码可用性
  useEffect(() => {
    let alive = true
    listPaymentChannels()
      .then((list) => {
        if (!alive) return
        setChannels(list)
        setChannel((prev) => (prev && list.includes(prev) ? prev : (list[0] ?? '')))
      })
      .catch(() => {
        if (!alive) return
        setChannels([])
        setChannel('')
      })
    return () => {
      alive = false
    }
  }, [])

  // 待支付才走动：每秒重算剩余秒数，订单状态推进即停
  const payable = order != null && canPay(order.status)
  useEffect(() => {
    if (!payable) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [payable])

  // 最新的支付单：重试只针对最新失败单，原单保持 FAILED、历史不改写
  const latest = newestPayment(payments)
  // 结果未定的支付单在途，避免重复发起导致重复扣款
  const payBusy = paymentInFlight(latest?.status ?? '')
  // 可重试只认后端 retryable（本单失败且订单仍可支付），前端不自行推断
  const retryable = canRetryPayment(latest)
  // 倒计时以 serverTime 为锚点本机推算；是否超时只认后端结论
  const countdownSeconds = payCountdownSeconds(order, now)

  const reportError = (prefix: string) => (err: unknown) => {
    setActionError(
      err instanceof ApiError ? `${prefix}（${err.code}）：${err.message}` : `${prefix}，请稍后重试。`,
    )
  }

  const pay = useCallback(() => {
    setBusy(true)
    setActionError('')
    // 失败单走重试端点（可换渠道）；金额与币种仍取订单应付快照，客户端只选渠道
    const request =
      retryable && latest ? retryPayment(latest.paymentNo, channel) : createPayment(orderNo, channel)
    request
      .then(() => load())
      .catch(reportError(retryable ? '重新支付失败' : '发起支付失败'))
      .finally(() => setBusy(false))
  }, [channel, latest, load, orderNo, retryable])

  const cancel = useCallback(() => {
    setBusy(true)
    setActionError('')
    cancelOrder(orderNo)
      .then(() => load())
      .catch(reportError('取消失败'))
      .finally(() => setBusy(false))
  }, [load, orderNo])

  // ── 争议（买家面只有开单 / 列表 / 撤回；受理与裁决在 /admin/disputes，此页不提供入口）──
  const [disputes, setDisputes] = useState<DisputeView[]>([])
  const [disputeSeq, setDisputeSeq] = useState(0)
  const [userId, setUserId] = useState(0)
  const [disputeForm, setDisputeForm] = useState<{
    reason: DisputeReason
    description: string
    evidenceUrl: string
    resolution: DisputeRequestType
    amountYuan: string
  }>({
    reason: 'NOT_DELIVERED',
    description: '',
    evidenceUrl: '',
    resolution: 'REFUND',
    amountYuan: '',
  })
  const [disputeError, setDisputeError] = useState('')
  const [disputeBusy, setDisputeBusy] = useState(false)

  // 演示身份与结算页同键（awn-demo-user）：无登录态，不伪造静默身份
  useEffect(() => {
    const saved = Number(window.localStorage.getItem(DEMO_USER_KEY) ?? '')
    setUserId(Number.isInteger(saved) && saved > 0 ? saved : 0)
  }, [])

  const demoUser = userId > 0
  const disputeEligible = canOpenDispute(order?.status ?? '')
  const canOpen = disputeEligible && demoUser
  const inFlight = hasInFlightDispute(disputes)

  useEffect(() => {
    if (!canOpen) return
    let alive = true
    listOrderDisputes(orderNo, userId)
      .then((list) => {
        if (alive) setDisputes(list)
      })
      .catch((err: unknown) => {
        // 缺 / 非法 userId 的 400 视为无争议可查，不当错误打扰；网络类照常展示
        if (!alive) return
        if (!(err instanceof ApiError && err.status === 400)) {
          setDisputeError(disputeErrorText('争议列表加载失败')(err))
        }
      })
    return () => {
      alive = false
    }
  }, [canOpen, userId, orderNo, disputeSeq])

  const submitDispute = (event: React.FormEvent) => {
    event.preventDefault()
    if (!order) return
    setDisputeError('')
    const invalid = validateDisputeForm(disputeForm, order.payAmount)
    if (invalid) {
      setDisputeError(invalid)
      return
    }
    setDisputeBusy(true)
    openDispute(orderNo, {
      userId,
      reason: disputeForm.reason,
      description: disputeForm.description.trim(),
      evidenceUrl: disputeForm.evidenceUrl.trim() || null,
      requestedResolution: disputeForm.resolution,
      requestedAmount:
        disputeForm.resolution === 'REFUND' ? parseYuanToFen(disputeForm.amountYuan) : null,
    })
      .then(() => setDisputeSeq((seq) => seq + 1))
      .catch((err: unknown) => setDisputeError(disputeErrorText('开争议失败')(err)))
      .finally(() => setDisputeBusy(false))
  }

  const withdraw = (disputeNo: string) => {
    setDisputeBusy(true)
    setDisputeError('')
    withdrawDispute(disputeNo, userId)
      .then(() => setDisputeSeq((seq) => seq + 1))
      .catch((err: unknown) => setDisputeError(disputeErrorText('撤回失败')(err)))
      .finally(() => setDisputeBusy(false))
  }

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
          {canPay(order.status) && countdownSeconds > 0 && (
            <span className="num"> · 剩余 {formatCountdown(countdownSeconds)}</span>
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
              <label htmlFor="channel">{retryable ? '重新支付渠道' : '支付渠道'}</label>
              <select
                id="channel"
                value={channel}
                onChange={(e) => setChannel(e.target.value)}
              >
                {channels.length === 0 && <option value="">加载渠道…</option>}
                {channels.map((code) => (
                  <option key={code} value={code}>
                    {paymentChannelLabel(code)}
                  </option>
                ))}
              </select>
              <span className="hint">
                {retryable
                  ? '上次支付失败，可换渠道重试；金额与币种仍取订单应付快照。'
                  : '金额与币种取订单应付快照，页面只提交渠道；支付结果以渠道回调为准。'}
              </span>
            </div>
            <button
              type="button"
              className="btn"
              disabled={busy || payBusy || !channel}
              onClick={pay}
            >
              {busy ? '处理中…' : retryable ? '重新支付' : '发起支付'}
            </button>
            {payBusy && (
              <p className="hint">已有在途支付单，完成或核实前不重复发起，避免重复扣款。</p>
            )}
            {retryable && (
              <p className="hint">最近一次支付失败且订单仍可支付，可重试或换渠道重试。</p>
            )}
            {channels.length === 0 && (
              <p className="hint">渠道清单不可用，暂不可发起支付。</p>
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

      {disputeEligible && (
        <section className="detail-section">
          <h2>争议</h2>

          {disputes.length === 0 && !inFlight && (
            <p className="hint">该订单暂无争议记录。</p>
          )}

          {disputes.map((dispute) => (
            <div key={dispute.disputeNo} className="group">
              <p className="row-between">
                <span className="num">{dispute.disputeNo}</span>
                <span className={`badge ${disputeStatusBadge(dispute.status)}`}>
                  {disputeStatusLabel(dispute.status)}
                </span>
              </p>
              <p className="hint" style={{ margin: 0 }}>
                {disputeReasonLabel(dispute.reason)} · 诉求：
                {dispute.requestedResolution === 'REFUND'
                  ? ` 退款 ${formatMinor(dispute.requestedAmount ?? 0)}`
                  : ' 重新交付'}
              </p>
              <p className="hint" style={{ margin: 0 }}>{dispute.description}</p>
              {dispute.resolution && (
                <p className="hint" style={{ margin: 0 }}>
                  {disputeResolutionLabel(dispute.resolution)}
                  {dispute.resolutionNote ? ` · ${dispute.resolutionNote}` : ''}
                  （裁决只记结论不动钱，退款执行由运营另行发起）
                </p>
              )}
              {canWithdrawDispute(dispute.status) && demoUser && (
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={disputeBusy}
                  onClick={() => withdraw(dispute.disputeNo)}
                >
                  撤回争议
                </button>
              )}
            </div>
          ))}

          {disputeError && (
            <p className="state state--error" role="alert">
              {disputeError}
            </p>
          )}

          {disputeEligible && !demoUser && (
            <p className="hint">
              演示环境未记录下单身份 userId（在结算页输入过会自动带入本机），暂不能开或查争议。
            </p>
          )}

          {canOpen && !inFlight && (
            <form onSubmit={submitDispute}>
              <div className="field">
                <label htmlFor="dispute-reason">争议原因</label>
                <select
                  id="dispute-reason"
                  value={disputeForm.reason}
                  onChange={(e) =>
                    setDisputeForm((form) => ({ ...form, reason: e.target.value as DisputeReason }))
                  }
                >
                  <option value="NOT_DELIVERED">未交付</option>
                  <option value="TICKET_INVALID">票券无效</option>
                  <option value="DESCRIPTION_MISMATCH">与描述不符</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="dispute-resolution">诉求</label>
                <select
                  id="dispute-resolution"
                  value={disputeForm.resolution}
                  onChange={(e) =>
                    setDisputeForm((form) => ({
                      ...form,
                      resolution: e.target.value as DisputeRequestType,
                    }))
                  }
                >
                  <option value="REFUND">退款</option>
                  <option value="REDELIVER">重新交付</option>
                </select>
                {disputeForm.resolution === 'REFUND' && (
                  <input
                    className="num"
                    type="text"
                    inputMode="decimal"
                    placeholder={`退款金额（元，不超过 ${formatMinor(order?.payAmount ?? 0)}）`}
                    value={disputeForm.amountYuan}
                    onChange={(e) =>
                      setDisputeForm((form) => ({ ...form, amountYuan: e.target.value }))
                    }
                  />
                )}
              </div>
              <div className="field">
                <label htmlFor="dispute-description">问题描述</label>
                <textarea
                  id="dispute-description"
                  rows={3}
                  placeholder="发生了什么、期望如何解决（必填）"
                  value={disputeForm.description}
                  onChange={(e) =>
                    setDisputeForm((form) => ({ ...form, description: e.target.value }))
                  }
                />
              </div>
              <div className="field">
                <label htmlFor="dispute-evidence">凭证链接（可选）</label>
                <input
                  id="dispute-evidence"
                  type="text"
                  placeholder="https://assets.awn.site/...（平台存储域）"
                  value={disputeForm.evidenceUrl}
                  onChange={(e) =>
                    setDisputeForm((form) => ({ ...form, evidenceUrl: e.target.value }))
                  }
                />
                <span className="hint">
                  只接受平台存储域（{DISPUTE_EVIDENCE_DOMAINS.join(' / ')}）或 / 起的相对路径，与后端白名单同口径。
                </span>
              </div>
              <button type="submit" className="btn btn--ghost" disabled={disputeBusy}>
                {disputeBusy ? '提交中…' : '发起争议'}
              </button>
              <p className="hint">
                开单后运营受理与裁决（页面不展示裁决入口）；裁决前可撤回。
              </p>
            </form>
          )}

          {canOpen && inFlight && (
            <p className="hint">该订单已有在途争议，裁决或撤回前不能重复开单。</p>
          )}
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