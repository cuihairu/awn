import { useState } from 'react'
import { ApiError } from '@awn/api-client'
import { adminApi, type AdminSessionRow, type AdminSeat } from '../lib/admin-client'
import { useAdminData } from '../lib/use-admin-data'
import { parseSeatPositions } from '../lib/seat-positions'

/** 座位状态中文文案（与逐座状态机五态一一对应，不发明状态） */
const SEAT_STATUS_LABEL_ZH: Record<AdminSeat['status'], string> = {
  IN_STOCK: '可选',
  RESERVED: '已预留',
  SOLD: '已售',
  DELIVERED: '已出票',
  INVALID: '不可用',
}

/** 状态徽章配色：可售 / 流程中 / 终止 */
function seatStatusBadge(status: AdminSeat['status']): string {
  if (status === 'IN_STOCK') return 'badge--success'
  if (status === 'SOLD' || status === 'DELIVERED') return 'badge--info'
  return 'badge--warning'
}

/**
 * 铺座运营视图（todo 2D 座位图余项）：选活动 → 场次 → 该场次座位运营清单 +
 * 铺座表单。铺座是批次操作：位置列表整体成功或整体失败（同场次同位置唯一、
 * 商品必须经目录校验），失败原因按后端错误码原样给出，不静默丢弃坏行。
 */
export function SeatsView({ token }: { token: string }) {
  const { data: events, loading: eventsLoading, failed: eventsFailed } = useAdminData(
    () => adminApi.events(token),
    [token],
  )
  const [eventCode, setEventCode] = useState('')
  const activeEvent = eventCode || events?.[0]?.eventCode || ''
  const { data: sessions, loading: sessionsLoading } = useAdminData(
    () => (activeEvent ? adminApi.eventSessions(token, activeEvent) : Promise.resolve([])),
    [token, activeEvent],
  )
  const [sessionCode, setSessionCode] = useState('')
  const activeSession = sessionCode || sessions?.[0]?.sessionCode || ''

  const { data: seats, loading: seatsLoading, failed: seatsFailed, reload } = useAdminData(
    () => (activeSession ? adminApi.sessionSeats(token, activeSession) : Promise.resolve([])),
    [token, activeSession],
  )

  const [productCode, setProductCode] = useState('')
  const [positionsText, setPositionsText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState('')

  if (eventsLoading) return <p className="state">加载中…</p>
  if (eventsFailed)
    return (
      <p className="state state--error">
        活动加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={() => window.location.reload()}>
          刷新
        </button>
      </p>
    )

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setDone('')
    if (!activeSession) return setError('请先选择场次。')
    if (!productCode.trim()) return setError('商品编码必填（铺座前先在目录建好票档）。')
    const { positions, error: parseError } = parseSeatPositions(positionsText)
    if (parseError) return setError(parseError)
    if (positions.length === 0) return setError('请按「分区,排,座」逐行粘贴座位。')
    setSubmitting(true)
    try {
      const created = await adminApi.createSeats(token, activeSession, {
        productCode: productCode.trim(),
        positions,
      })
      setDone(`已铺 ${created.length} 座（批次整体成功），下方清单已刷新。`)
      setPositionsText('')
      reload()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `铺座失败：${err.status} ${err.code}（${err.message}）`
          : '铺座失败：网络或服务异常。',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const seatRows = seats ?? []

  return (
    <div className="cols-2">
      <div className="col">
        <section>
          <h2>场次座位清单</h2>
          <div className="toolbar">
            <label>
              活动：
              <select value={activeEvent} onChange={(e) => setEventCode(e.target.value)}>
                {(events ?? []).map((event) => (
                  <option key={event.eventCode} value={event.eventCode}>
                    {event.name}（{event.eventCode}）
                  </option>
                ))}
              </select>
            </label>
            <label>
              场次：
              <select
                value={activeSession}
                onChange={(e) => {
                  setSessionCode(e.target.value)
                  setDone('')
                  setError('')
                }}
                disabled={sessionsLoading || (sessions ?? []).length === 0}
              >
                {(sessions ?? []).map((session: AdminSessionRow) => (
                  <option key={session.sessionCode} value={session.sessionCode}>
                    {session.name}（{session.sessionCode}）
                  </option>
                ))}
              </select>
            </label>
            <button
              className="btn btn-sm btn-ghost"
              type="button"
              onClick={reload}
              disabled={!activeSession}
            >
              刷新
            </button>
          </div>

          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">座位号</th>
                  <th scope="col">分区</th>
                  <th scope="col">排</th>
                  <th scope="col">座</th>
                  <th scope="col">票档</th>
                  <th scope="col">状态</th>
                  <th scope="col">占座订单</th>
                </tr>
              </thead>
              <tbody>
                {seatRows.map((seat) => (
                  <tr key={seat.seatNo}>
                    <td className="num">{seat.seatNo}</td>
                    <td className="num">{seat.section}</td>
                    <td className="num">{seat.row}</td>
                    <td className="num">{seat.seat}</td>
                    <td className="num">{seat.productCode}</td>
                    <td>
                      <span className={`badge num ${seatStatusBadge(seat.status)}`}>
                        {SEAT_STATUS_LABEL_ZH[seat.status]}
                      </span>
                    </td>
                    <td className="num">{seat.orderNo ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {sessionsLoading && <p className="state">场次加载中…</p>}
            {seatsLoading && <p className="state">座位清单加载中…</p>}
            {seatsFailed && (
              <p className="state state--error">
                座位清单加载失败（令牌或网络问题）。
                <button className="btn btn-sm" type="button" onClick={reload}>
                  重试
                </button>
              </p>
            )}
            {!seatsLoading && !seatsFailed && seatRows.length === 0 && (
              <p className="state">该场次暂未铺座。</p>
            )}
          </div>
          <p className="hint">运营视图含占座订单号（公开座位图剥去该字段）。</p>
        </section>
      </div>

      <section className="card form-card" aria-labelledby="seats-title">
        <h2 id="seats-title" className="card-title">
          铺座
        </h2>
        <p className="hint">对应 POST /api/v1/admin/sessions/{'{sessionCode}'}/seats</p>
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="seats-product">票档商品编码</label>
            <input
              id="seats-product"
              className="input num"
              type="text"
              placeholder="如 prod-seat-a（须为目录内同场次商品）"
              value={productCode}
              onChange={(e) => setProductCode(e.target.value)}
            />
            <span className="hint">一次铺一个商品在同一场次下的座位。</span>
          </div>
          <div className="field">
            <label htmlFor="seats-positions">座位位置（每行一座）</label>
            <textarea
              id="seats-positions"
              className="input num"
              rows={8}
              placeholder={'A,1,1\nA,1,2\nA,2,1'}
              value={positionsText}
              onChange={(e) => setPositionsText(e.target.value)}
            />
            <span className="hint">格式「分区,排,座」，兼容中文逗号与表格粘贴的制表符；批次整体成功或整体失败。</span>
          </div>
          {error && (
            <p className="state state--error" role="alert">
              {error}
            </p>
          )}
          {done && (
            <p className="state" role="status">
              {done}
            </p>
          )}
          <button className="btn btn-block" type="submit" disabled={submitting}>
            {submitting ? '提交中…' : '提交铺座'}
          </button>
        </form>
      </section>
    </div>
  )
}
