import { useState } from 'react'
import { ApiError } from '@awn/api-client'
import { adminApi } from '../lib/admin-client'
import { eventStatusZh, formatAdminTime } from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

/** 活动视图（发布流）：全状态列表，草稿可发布（DRAFT → PUBLISHED，重复发布 409） */
export function EventsView({ token }: { token: string }) {
  const { data: events, loading, failed, reload } = useAdminData(
    () => adminApi.events(token),
    [token],
  )
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState('')

  if (loading) return <p className="state">加载中…</p>
  if (failed)
    return (
      <p className="state state--error">
        活动加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={reload}>
          重试
        </button>
      </p>
    )

  const publish = async (eventCode: string) => {
    setBusy(eventCode)
    setNotice('')
    try {
      await adminApi.publishEvent(token, eventCode)
      setNotice(`已发布 ${eventCode}，公开列表立即可见。`)
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `发布失败：${err.status} ${err.code}${err.status === 409 ? '（仅草稿可发布）' : ''}`
          : '发布失败：网络或服务异常。',
      )
    } finally {
      setBusy('')
    }
  }

  return (
    <>
      {notice && (
        <p className={notice.startsWith('已发布') ? 'state' : 'state state--error'} role="status">
          {notice}
        </p>
      )}
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">活动编码</th>
              <th scope="col">名称</th>
              <th scope="col">状态</th>
              <th scope="col">开始时间</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {(events ?? []).map((event) => (
              <tr key={event.eventCode}>
                <td className="num">{event.eventCode}</td>
                <td>{event.name}</td>
                <td>
                  <span className="badge">{eventStatusZh(event.status)}</span>
                </td>
                <td className="num">{formatAdminTime(event.startTime)}</td>
                <td>
                  {event.status === 'DRAFT' ? (
                    <button
                      className="btn btn-sm"
                      type="button"
                      disabled={busy === event.eventCode}
                      onClick={() => publish(event.eventCode)}
                    >
                      {busy === event.eventCode ? '发布中…' : '发布'}
                    </button>
                  ) : (
                    <span className="hint">—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(events ?? []).length === 0 && <p className="state">暂无活动。</p>}
      </div>
      <p className="hint">
        数据来自 GET /api/v1/admin/events（全状态）；发布走 POST /api/v1/admin/events/{'{eventCode}'}/publish，重复发布返回 409。
      </p>
    </>
  )
}
