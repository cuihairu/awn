import { Fragment, useState } from 'react'
import { ApiError, type DisputeResolution, type DisputeSellerResponseView } from '@awn/api-client'
import { adminApi } from '../lib/admin-client'
import {
  disputeBadgeClass,
  disputeReasonZh,
  disputeResolutionZh,
  disputeStatusZh,
  formatAdminTime,
  minorToYuan,
} from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

const RESOLUTION_OPTIONS: DisputeResolution[] = ['REJECTED', 'REFUND_APPROVED', 'REDELIVER_APPROVED']

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
            {(disputes ?? []).map((dispute) => (
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
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
        {(disputes ?? []).length === 0 && <p className="state">暂无争议。</p>}
      </div>
      <p className="hint">
        数据来自 GET /api/v1/admin/disputes（最近争议，新→旧）。受理 OPEN→UNDER_REVIEW；裁决仅受理后可用，
        裁决只记结论不动钱（退款走既有退款流）。卖家申辩来自 GET /api/v1/admin/disputes/&#123;disputeNo&#125;/seller-responses。
      </p>
    </>
  )
}
