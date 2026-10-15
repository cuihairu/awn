import { Fragment, useState } from 'react'
import {
  ApiError,
  type DisputeResolution,
  type DisputeSellerResponseView,
  type DisputeView,
  type PaymentView,
} from '@awn/api-client'
import { adminApi, newIdempotencyKey } from '../lib/admin-client'
import {
  disputeBadgeClass,
  disputeReasonZh,
  disputeResolutionZh,
  disputeStatusZh,
  formatAdminTime,
  minorToYuan,
  parseYuanToFen,
  succeededPayments,
  validateRefundYuan,
} from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

const RESOLUTION_OPTIONS: DisputeResolution[] = ['REJECTED', 'REFUND_APPROVED', 'REDELIVER_APPROVED']

/** 退款执行表单状态：键在内容变化后换新（同键不同内容后端判冲突） */
interface RefundForm {
  paymentNo: string
  amountYuan: string
  reason: string
  key: string
}

/** 争议视图：最近争议列表 + 受理（OPEN→UNDER_REVIEW）+ 裁决（UNDER_REVIEW→RESOLVED）+ 卖家申辩查阅 */
export function DisputesView({ token }: { token: string }) {
  const { data: disputes, loading, failed, reload } = useAdminData(
    () => adminApi.disputes(token),
    [token],
  )
  const [expanded, setExpanded] = useState<string | null>(null)
  const [responses, setResponses] = useState<Record<string, DisputeSellerResponseView[]>>({})
  const [responsesLoading, setResponsesLoading] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const [actor, setActor] = useState('')
  const [resolution, setResolution] = useState<DisputeResolution>('REJECTED')
  const [note, setNote] = useState('')
  /** 退款执行：支付单按订单懒加载，裁决 REFUND_APPROVED 的详情展开时取 */
  const [payments, setPayments] = useState<Record<string, PaymentView[]>>({})
  const [paymentsLoading, setPaymentsLoading] = useState(false)
  const [refund, setRefund] = useState<RefundForm>({
    paymentNo: '',
    amountYuan: '',
    reason: '',
    key: '',
  })
  /** 已执行退款（争议号 → 退款单号）：后端无退款列表端点，成功标记存本页 */
  const [refunded, setRefunded] = useState<Record<string, string>>({})

  if (loading) return <p className="state">加载中…</p>
  if (failed)
    return (
      <p className="state state--error">
        争议加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={reload}>
          重试
        </button>
      </p>
    )

  const toggleExpand = async (disputeNo: string) => {
    if (expanded === disputeNo) {
      setExpanded(null)
      return
    }
    setExpanded(disputeNo)
    setNotice('')
    const target = (disputes ?? []).find((d) => d.disputeNo === disputeNo)
    if (target && target.status === 'RESOLVED' && target.resolution === 'REFUND_APPROVED') {
      // 退款执行：裁决退款结论的详情展开时按订单取支付单并预填表单
      const prefill = (list: PaymentView[]) => {
        const succeeded = succeededPayments(list)
        if (succeeded.length === 0) return
        setRefund({
          paymentNo: succeeded[0].paymentNo,
          amountYuan: target.requestedAmount != null ? minorToYuan(target.requestedAmount) : '',
          reason: `争议 ${target.disputeNo} 裁决退款`,
          key: newIdempotencyKey(),
        })
      }
      if (payments[target.orderNo]) {
        prefill(payments[target.orderNo])
      } else {
        setPaymentsLoading(true)
        try {
          const list = await adminApi.orderPayments(target.orderNo)
          setPayments((prev) => ({ ...prev, [target.orderNo]: list }))
          prefill(list)
        } catch (err) {
          setNotice(
            err instanceof ApiError
              ? `支付单加载失败：${err.status} ${err.code}`
              : '支付单加载失败：网络或服务异常。',
          )
        } finally {
          setPaymentsLoading(false)
        }
      }
    }
    if (responses[disputeNo]) return
    setResponsesLoading(true)
    try {
      const list = await adminApi.disputeSellerResponses(token, disputeNo)
      setResponses((prev) => ({ ...prev, [disputeNo]: list }))
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `申辩加载失败：${err.status} ${err.code}`
          : '申辩加载失败：网络或服务异常。',
      )
    } finally {
      setResponsesLoading(false)
    }
  }

  const accept = async (disputeNo: string) => {
    setBusy(disputeNo)
    setNotice('')
    try {
      await adminApi.acceptDispute(token, disputeNo)
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError ? `受理失败：${err.status} ${err.code}` : '受理失败：网络或服务异常。',
      )
    } finally {
      setBusy(null)
    }
  }

  const adjudicate = async (disputeNo: string) => {
    if (!actor.trim()) return setNotice('操作人必填。')
    if (!note.trim()) return setNotice('裁决说明必填。')
    setBusy(disputeNo)
    setNotice('')
    try {
      await adminApi.adjudicateDispute(token, disputeNo, {
        actor: actor.trim(),
        resolution,
        note: note.trim(),
      })
      setExpanded(null)
      setNote('')
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError ? `裁决失败：${err.status} ${err.code}` : '裁决失败：网络或服务异常。',
      )
    } finally {
      setBusy(null)
    }
  }

  /** 退款执行：金额以最小货币单位提交，可退余额由服务端守卫（超额 409） */
  const executeRefund = async (dispute: DisputeView) => {
    const invalid = validateRefundYuan(refund.amountYuan, dispute.requestedAmount)
    if (invalid) return setNotice(invalid)
    if (!refund.paymentNo) return setNotice('请选择收款支付单。')
    if (!refund.reason.trim()) return setNotice('退款原因必填。')
    setBusy(dispute.disputeNo)
    setNotice('')
    try {
      const view = await adminApi.createOrderRefund(dispute.orderNo, refund.key, {
        paymentNo: refund.paymentNo,
        amount: parseYuanToFen(refund.amountYuan) ?? 0,
        reason: refund.reason.trim(),
      })
      setRefunded((prev) => ({ ...prev, [dispute.disputeNo]: view.refundNo }))
      // 已成功键不重用：再次执行（如分笔退款）生成新键
      setRefund((prev) => ({ ...prev, key: newIdempotencyKey() }))
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? err.code === 'REFUND_CONFLICT'
            ? '退款被拒（REFUND_CONFLICT）：支付未成功或金额超出可退余额（可能已全额退款）。'
            : `退款失败：${err.status} ${err.code}`
          : '退款失败：网络或服务异常。',
      )
    } finally {
      setBusy(null)
    }
  }

  const patchRefund = (patch: Partial<RefundForm>) =>
    setRefund((prev) => ({ ...prev, ...patch, key: newIdempotencyKey() }))

  const requestLabel = (dispute: { requestedResolution: string; requestedAmount: number | null }) =>
    dispute.requestedResolution === 'REFUND'
      ? dispute.requestedAmount != null
        ? `退款 ¥${minorToYuan(dispute.requestedAmount)}`
        : '退款'
      : '重新交付'

  return (
    <>
      {notice && (
        <p className="state state--error" role="status">
          {notice}
        </p>
      )}
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">争议号</th>
              <th scope="col">订单号</th>
              <th scope="col">卖家</th>
              <th scope="col">原因</th>
              <th scope="col">诉求</th>
              <th scope="col">状态</th>
              <th scope="col">开单时间</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {(disputes ?? []).map((dispute) => {
              const refundSources = succeededPayments(payments[dispute.orderNo] ?? [])
              return (
              <Fragment key={dispute.disputeNo}>
                <tr>
                  <td className="num">{dispute.disputeNo}</td>
                  <td className="num">{dispute.orderNo}</td>
                  <td className="num">{dispute.sellerId ?? '—'}</td>
                  <td>{disputeReasonZh(dispute.reason)}</td>
                  <td>{requestLabel(dispute)}</td>
                  <td>
                    <span className={`badge ${disputeBadgeClass(dispute.status)}`}>
                      {disputeStatusZh(dispute.status)}
                    </span>
                    {dispute.status === 'RESOLVED' && dispute.resolution && (
                      <span className="muted"> {disputeResolutionZh(dispute.resolution)}</span>
                    )}
                  </td>
                  <td className="num">{formatAdminTime(dispute.createdAt)}</td>
                  <td>
                    {dispute.status === 'OPEN' && (
                      <button
                        className="btn btn-sm"
                        type="button"
                        disabled={busy === dispute.disputeNo}
                        onClick={() => accept(dispute.disputeNo)}
                      >
                        {busy === dispute.disputeNo ? '受理中…' : '受理'}
                      </button>
                    )}{' '}
                    <button className="btn btn-sm btn-ghost" type="button" onClick={() => toggleExpand(dispute.disputeNo)}>
                      {expanded === dispute.disputeNo ? '收起' : '详情'}
                    </button>
                  </td>
                </tr>
                {expanded === dispute.disputeNo && (
                  <tr>
                    <td colSpan={8}>
                      <div className="detail">
                        <p>
                          <span className="muted">问题描述：</span>
                          {dispute.description}
                        </p>
                        <p>
                          <span className="muted">凭证：</span>
                          {dispute.evidenceUrl ? (
                            <a href={dispute.evidenceUrl} target="_blank" rel="noreferrer">
                              {dispute.evidenceUrl}
                            </a>
                          ) : (
                            '无'
                          )}
                        </p>
                        <p className="muted">卖家申辩（裁决依据，提交时间正序）：</p>
                        {responsesLoading ? (
                          <p className="muted">申辩加载中…</p>
                        ) : (responses[dispute.disputeNo] ?? []).length === 0 ? (
                          <p className="muted">卖家未提交申辩。</p>
                        ) : (
                          <ul>
                            {(responses[dispute.disputeNo] ?? []).map((response, i) => (
                              <li key={`${response.sellerNo}-${i}`}>
                                <span className="num">{formatAdminTime(response.createdAt)}</span>{' '}
                                <span className="num">{response.sellerNo}</span>：{response.content}
                              </li>
                            ))}
                          </ul>
                        )}
                        {dispute.status === 'UNDER_REVIEW' && (
                          <form
                            className="detail-form"
                            onSubmit={(e) => {
                              e.preventDefault()
                              adjudicate(dispute.disputeNo)
                            }}
                          >
                            <label>
                              操作人
                              <input
                                className="input"
                                value={actor}
                                onChange={(e) => setActor(e.target.value)}
                                autoComplete="off"
                              />
                            </label>
                            <label>
                              裁决
                              <select
                                className="input"
                                value={resolution}
                                onChange={(e) => setResolution(e.target.value as DisputeResolution)}
                              >
                                {RESOLUTION_OPTIONS.map((option) => (
                                  <option key={option} value={option}>
                                    {disputeResolutionZh(option)}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label>
                              裁决说明
                              <textarea
                                className="input"
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                                rows={2}
                              />
                            </label>
                            <button className="btn btn-sm" type="submit" disabled={busy === dispute.disputeNo}>
                              {busy === dispute.disputeNo ? '裁决中…' : '提交裁决'}
                            </button>
                          </form>
                        )}
                        {dispute.status === 'RESOLVED' && dispute.resolution === 'REFUND_APPROVED' && (
                          <div className="detail-form">
                            <p className="muted">退款执行（裁决不动钱，此处走既有退款端点执行）：</p>
                            {refunded[dispute.disputeNo] ? (
                              <p className="muted">
                                已发起退款 <span className="num">{refunded[dispute.disputeNo]}</span>
                                （金额与结果以支付模块为准）。
                              </p>
                            ) : paymentsLoading && !payments[dispute.orderNo] ? (
                              <p className="muted">支付单加载中…</p>
                            ) : refundSources.length === 0 ? (
                              <p className="muted">
                                无成功支付单，退款暂不可执行（渠道未确认收款）。
                              </p>
                            ) : (
                              <>
                                <label>
                                  收款支付单
                                  <select
                                    className="input"
                                    value={refund.paymentNo}
                                    onChange={(e) => patchRefund({ paymentNo: e.target.value })}
                                  >
                                    {refundSources.map((payment) => (
                                      <option key={payment.paymentNo} value={payment.paymentNo}>
                                        {payment.paymentNo}（¥{minorToYuan(payment.amount)}）
                                      </option>
                                    ))}
                                  </select>
                                </label>
                                <label>
                                  退款金额（元，≤ 裁决 ¥{minorToYuan(dispute.requestedAmount ?? 0)}）
                                  <input
                                    className="input num"
                                    value={refund.amountYuan}
                                    onChange={(e) => patchRefund({ amountYuan: e.target.value })}
                                    autoComplete="off"
                                  />
                                </label>
                                <label>
                                  退款原因
                                  <input
                                    className="input"
                                    value={refund.reason}
                                    onChange={(e) => patchRefund({ reason: e.target.value })}
                                    autoComplete="off"
                                  />
                                </label>
                                <button
                                  className="btn btn-sm"
                                  type="button"
                                  disabled={busy === dispute.disputeNo}
                                  onClick={() => executeRefund(dispute)}
                                >
                                  {busy === dispute.disputeNo ? '退款中…' : '执行退款'}
                                </button>
                                <p className="muted">
                                  可退余额由服务端守卫（超额 409 REFUND_CONFLICT）；幂等键随内容变化换新。
                                </p>
                              </>
                            )}
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
              )
            })}
          </tbody>
        </table>
        {(disputes ?? []).length === 0 && <p className="state">暂无争议。</p>}
      </div>
      <p className="hint">
        数据来自 GET /api/v1/admin/disputes（最近争议，新→旧）。受理 OPEN→UNDER_REVIEW；裁决仅受理后可用，
        裁决只记结论不动钱；裁决退款的执行走既有退款端点
        POST /api/v1/orders/&#123;orderNo&#125;/refunds（金额 ≤ 裁决金额，服务端按可退余额守卫）。
        卖家申辩来自 GET /api/v1/admin/disputes/&#123;disputeNo&#125;/seller-responses。
      </p>
    </>
  )
}
