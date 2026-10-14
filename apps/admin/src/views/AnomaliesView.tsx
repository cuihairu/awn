import { useState } from 'react'
import { ApiError } from '@awn/api-client'
import { adminApi } from '../lib/admin-client'
import { formatAdminTime } from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

/** 异常视图（原型 admin/anomalies.html）：未确认告警 + 确认动作 */
export function AnomaliesView({ token }: { token: string }) {
  const { data: alerts, loading, failed, reload } = useAdminData(
    () => adminApi.anomalies(token),
    [token],
  )
  const [busy, setBusy] = useState<number | null>(null)
  const [notice, setNotice] = useState('')

  if (loading) return <p className="state">加载中…</p>
  if (failed)
    return (
      <p className="state state--error">
        告警加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={reload}>
          重试
        </button>
      </p>
    )

  const acknowledge = async (id: number) => {
    setBusy(id)
    setNotice('')
    try {
      await adminApi.acknowledgeAlert(token, id)
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `确认失败：${err.status} ${err.code}`
          : '确认失败：网络或服务异常。',
      )
    } finally {
      setBusy(null)
    }
  }

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
              <th scope="col">类别</th>
              <th scope="col">级别</th>
              <th scope="col">内容</th>
              <th scope="col">引用</th>
              <th scope="col">时间</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {(alerts ?? []).map((alert) => (
              <tr key={alert.id}>
                <td>
                  <span className="badge badge--info">{alert.category}</span>
                </td>
                <td>
                  <span
                    className={`badge ${
                      alert.severity === 'CRITICAL'
                        ? 'badge--danger'
                        : alert.severity === 'WARN'
                          ? 'badge--warning'
                          : ''
                    }`}
                  >
                    {alert.severity}
                  </span>
                </td>
                <td>{alert.message}</td>
                <td className="num">{alert.reference}</td>
                <td className="num">{formatAdminTime(alert.createdAt)}</td>
                <td>
                  <button
                    className="btn btn-sm btn-ghost"
                    type="button"
                    disabled={busy === alert.id}
                    onClick={() => acknowledge(alert.id)}
                  >
                    {busy === alert.id ? '确认中…' : '确认'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(alerts ?? []).length === 0 && <p className="state">暂无未确认告警。</p>}
      </div>
      <p className="hint">数据来自 GET /api/v1/admin/anomalies（未确认告警）；确认后从本视图消失且不可恢复。</p>
    </>
  )
}
