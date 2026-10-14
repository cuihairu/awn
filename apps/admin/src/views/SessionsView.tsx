import { useState } from 'react'
import { ApiError } from '@awn/api-client'
import { adminApi, type AdminSessionRow } from '../lib/admin-client'
import { formatAdminTime } from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

/** 解析后的 geo 行：场馆名 / 城市（无任何场馆显示「—」，不造假地点） */
function geoText(session: AdminSessionRow): string {
  if (!session.geo) return '—'
  return [session.geo.venueName, session.geo.city].filter(Boolean).join(' · ')
}

/**
 * 场次配置视图（地理配置面）：选活动 → 场次列表（含解析 geo）→ 挂 / 摘场馆。
 * 挂载走 POST /admin/sessions/{code}/venue，摘除走 DELETE（仅清除场次覆盖，
 * 父活动场馆仍继承，无任何场馆时公开面 geo 为 null 是正常态）；
 * 未知场次 / 场馆 → 404（SESSION_NOT_FOUND / VENUE_NOT_FOUND）。
 */
export function SessionsView({ token }: { token: string }) {
  const { data: events, loading: eventsLoading, failed: eventsFailed } = useAdminData(
    () => adminApi.events(token),
    [token],
  )
  const { data: venues } = useAdminData(() => adminApi.venues(token), [token])
  const [eventCode, setEventCode] = useState('')
  const activeEvent = eventCode || events?.[0]?.eventCode || ''
  const { data: sessions, loading, failed, reload } = useAdminData(
    () => (activeEvent ? adminApi.eventSessions(token, activeEvent) : Promise.resolve([])),
    [token, activeEvent],
  )
  const [choices, setChoices] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState('')

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

  const run = async (sessionCode: string, action: 'attach' | 'detach') => {
    setBusy(sessionCode)
    setNotice('')
    try {
      if (action === 'attach') {
        const venueCode = choices[sessionCode]
        if (!venueCode) return
        const geo = await adminApi.attachSessionVenue(token, sessionCode, venueCode)
        setNotice(`已挂场馆：${geo ? [geo.venueName, geo.city].filter(Boolean).join(' · ') : '（空）'}`)
      } else {
        await adminApi.detachSessionVenue(token, sessionCode)
        setNotice('已摘除场次场馆覆盖，回退父活动场馆继承。')
      }
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `操作失败：${err.status} ${err.code}`
          : '操作失败：网络或服务异常。',
      )
    } finally {
      setBusy('')
    }
  }

  return (
    <>
      {notice && <p className="state" role="status">{notice}</p>}
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
      </div>
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">场次</th>
              <th scope="col">开始时间</th>
              <th scope="col">当前地点（geo）</th>
              <th scope="col">挂场馆</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {(sessions ?? []).map((session) => (
              <tr key={session.sessionCode}>
                <td>{session.name}</td>
                <td className="num">{formatAdminTime(session.startTime)}</td>
                <td>{geoText(session)}</td>
                <td>
                  <select
                    aria-label={`为 ${session.name} 选择场馆`}
                    value={choices[session.sessionCode] ?? ''}
                    onChange={(e) =>
                      setChoices((prev) => ({ ...prev, [session.sessionCode]: e.target.value }))
                    }
                  >
                    <option value="">选择场馆…</option>
                    {(venues ?? []).map((venue) => (
                      <option key={venue.venueCode} value={venue.venueCode}>
                        {venue.name}（{venue.city ?? '城市未录'}）
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <button
                    className="btn btn-sm"
                    type="button"
                    disabled={busy === session.sessionCode || !choices[session.sessionCode]}
                    onClick={() => run(session.sessionCode, 'attach')}
                  >
                    挂场馆
                  </button>{' '}
                  <button
                    className="btn btn-sm"
                    type="button"
                    disabled={busy === session.sessionCode}
                    onClick={() => run(session.sessionCode, 'detach')}
                  >
                    摘除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(sessions ?? []).length === 0 && <p className="state">该活动暂无场次。</p>}
      </div>
      <p className="hint">
        geo 为后端解析结果（场次场馆覆盖 → 父活动场馆 → 空，不造假地点）；摘除只清场次覆盖，
        不动父活动场馆。接口见 GET /api/v1/admin/venues 与 POST/DELETE
        /api/v1/admin/sessions/{'{sessionCode}'}/venue。
      </p>
    </>
  )
}
