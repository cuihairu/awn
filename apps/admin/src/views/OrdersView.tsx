import { useState } from 'react'
import { adminApi, type AdminOrderStatus } from '../lib/admin-client'
import {
  filterOrdersByStatus,
  formatAdminTime,
  minorToYuan,
  orderBadgeClass,
  orderStatusZh,
} from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

const TABS: { key: 'ALL' | AdminOrderStatus; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待支付' },
  { key: 'PAID', label: '已支付' },
  { key: 'FULFILLING', label: '履约中' },
  { key: 'REFUND_PENDING', label: '退款中' },
  { key: 'REFUNDED', label: '已退款' },
  { key: 'EXPIRED', label: '已超时' },
]

/** 订单视图（原型 admin/orders.html）：状态筛选 + 订单表（真实数据，只读） */
export function OrdersView({ token }: { token: string }) {
  const { data: orders, loading, failed, reload } = useAdminData(
    () => adminApi.orders(token),
    [token],
  )
  const [tab, setTab] = useState<'ALL' | AdminOrderStatus>('ALL')
  const visible = filterOrdersByStatus(orders ?? [], tab)

  if (loading) return <p className="state">加载中…</p>
  if (failed)
    return (
      <p className="state state--error">
        订单加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={reload}>
          重试
        </button>
      </p>
    )

  return (
    <>
      <div className="toolbar">
        <div className="tabs" role="tablist" aria-label="订单状态筛选">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              className={`tab${tab === t.key ? ' tab--on' : ''}`}
              onClick={() => setTab(t.key)}
            >
              {t.label}{' '}
              <span className="num">{filterOrdersByStatus(orders ?? [], t.key).length}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">订单号</th>
              <th scope="col">件数</th>
              <th scope="col">应付金额</th>
              <th scope="col">状态</th>
              <th scope="col">到期时间</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((order) => (
              <tr key={order.orderNo}>
                <td className="num">{order.orderNo}</td>
                <td className="num">
                  {order.items.reduce((sum, item) => sum + item.quantity, 0)}
                </td>
                <td className="num">{minorToYuan(order.payAmount)}</td>
                <td>
                  <span className={`badge ${orderBadgeClass(order.status)}`}>
                    {orderStatusZh(order.status)}
                  </span>
                </td>
                <td className="num">{formatAdminTime(order.expiresAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {visible.length === 0 && <p className="state">暂无符合条件的订单。</p>}
      </div>
      <p className="hint">
        数据来自 GET /api/v1/admin/orders（最近订单，新→旧）；金额为应付总额（最小货币单位换算）。
      </p>
    </>
  )
}
