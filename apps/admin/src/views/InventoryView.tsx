import { useState } from 'react'
import { adminApi } from '../lib/admin-client'
import { ApiError } from '@awn/api-client'
import { availableRatio, formatAdminTime, isLowStock } from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

/** 库存中心（原型 admin/inventory.html）：池余额表 + 调整表单（原因必填、留审计）+ 审计记录 */
export function InventoryView({ token }: { token: string }) {
  const balances = useAdminData(() => adminApi.inventory(token), [token])
  const audit = useAdminData(() => adminApi.auditLogs(token), [token])
  const reloadAll = () => {
    balances.reload()
    audit.reload()
  }

  const [poolCode, setPoolCode] = useState('')
  const [delta, setDelta] = useState('')
  const [reason, setReason] = useState('')
  const [actor, setActor] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState('')

  if (balances.loading) return <p className="state">加载中…</p>
  if (balances.failed)
    return (
      <p className="state state--error">
        库存加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={reloadAll}>
          重试
        </button>
      </p>
    )

  const pools = balances.data ?? []

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setDone('')
    const parsed = Number(delta)
    if (!poolCode) return setError('请选择池编码。')
    if (!Number.isInteger(parsed) || parsed === 0) return setError('调整数量必须是正负整数且不为 0。')
    if (!reason.trim()) return setError('原因必填（调整会写 ADJUST 流水与审计）。')
    if (!actor.trim()) return setError('操作人必填。')
    setSubmitting(true)
    try {
      await adminApi.adjustInventory(token, {
        poolCode,
        delta: parsed,
        reason: reason.trim(),
        actor: actor.trim(),
      })
      setDone(`已调整 ${poolCode} ${parsed > 0 ? '+' : ''}${parsed}，余额已刷新。`)
      setDelta('')
      setReason('')
      reloadAll()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `调整失败：${err.status} ${err.code}`
          : '调整失败：网络或服务异常。',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="cols-2">
      <div className="col">
        <section>
          <div className="row-between">
            <h2>库存池余额</h2>
            <button className="btn btn-sm btn-ghost" type="button" onClick={reloadAll}>
              刷新
            </button>
          </div>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">池编码</th>
                  <th scope="col">总量</th>
                  <th scope="col">已预留</th>
                  <th scope="col">已售</th>
                  <th scope="col">可用</th>
                  <th scope="col">可用量占比</th>
                </tr>
              </thead>
              <tbody>
                {pools.map((pool) => {
                  const ratio = availableRatio(pool)
                  const pct = `${(ratio * 100).toFixed(1)}%`
                  const low = isLowStock(ratio)
                  return (
                    <tr key={pool.poolCode}>
                      <td className="num">{pool.poolCode}</td>
                      <td className="num">{pool.totalQuantity}</td>
                      <td className="num">{pool.reservedQuantity}</td>
                      <td className="num">{pool.soldQuantity}</td>
                      <td className="num">{pool.available}</td>
                      <td>
                        <div
                          className="balance-bar"
                          role="img"
                          aria-label={`可用量占比 ${pct}${low ? '，低于 20% 预警线' : ''}`}
                        >
                          <i className={low ? 'low' : ''} style={{ width: pct }} />
                        </div>
                        <span className="hint num">
                          {pct}
                          {low && ' · 低于预警线'}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {pools.length === 0 && <p className="state">暂无库存池。</p>}
          </div>
          <p className="hint">可用量 = 总量 − 已预留 − 已售；进度条低于 20% 转为预警色。</p>
        </section>

        <section>
          <h2>审计记录</h2>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">操作人</th>
                  <th scope="col">动作</th>
                  <th scope="col">对象</th>
                  <th scope="col">时间</th>
                </tr>
              </thead>
              <tbody>
                {(audit.data ?? []).map((entry) => (
                  <tr key={entry.id}>
                    <td className="num">{entry.actor}</td>
                    <td>
                      <span className="badge badge--info num">{entry.action}</span>
                    </td>
                    <td className="num">
                      {entry.targetType}/{entry.targetId}
                    </td>
                    <td className="num">{formatAdminTime(entry.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {audit.failed && <p className="state state--error">审计记录加载失败。</p>}
          </div>
        </section>
      </div>

      <section className="card form-card" aria-labelledby="adj-title">
        <h2 id="adj-title" className="card-title">
          库存调整
        </h2>
        <p className="hint">对应 POST /api/v1/admin/inventory/adjustments</p>
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="adj-pool">池编码</label>
            <select
              id="adj-pool"
              className="input"
              value={poolCode}
              onChange={(e) => setPoolCode(e.target.value)}
            >
              <option value="">请选择…</option>
              {pools.map((pool) => (
                <option key={pool.poolCode} value={pool.poolCode}>
                  {pool.poolCode}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="adj-qty">调整数量</label>
            <input
              id="adj-qty"
              className="input num"
              type="number"
              step="1"
              inputMode="numeric"
              placeholder="正数加库存 / 负数减库存，如 120"
              value={delta}
              onChange={(e) => setDelta(e.target.value)}
            />
            <span className="hint">支持正负；「已预留」中的数量不可直接调减。</span>
          </div>
          <div className="field">
            <label htmlFor="adj-reason">原因（必填）</label>
            <textarea
              id="adj-reason"
              className="input"
              required
              placeholder="例：实物盘点盘盈 120 张，附盘点单号"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <span className="hint">调整要求原因并留审计（生成 ADJUST 流水）。</span>
          </div>
          <div className="field">
            <label htmlFor="adj-op">操作人</label>
            <input
              id="adj-op"
              className="input"
              type="text"
              placeholder="如 cui"
              value={actor}
              onChange={(e) => setActor(e.target.value)}
            />
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
            {submitting ? '提交中…' : '提交调整'}
          </button>
        </form>
        <p className="hint">提交成功后写入审计记录（INVENTORY_ADJUSTED），可在左侧「审计记录」中核对。</p>
      </section>
    </div>
  )
}
