import type { AdminOrder, AdminOrderStatus, PoolBalance } from './admin-client'
import type { DisputeReason, DisputeResolution, DisputeStatus } from '@awn/api-client'

/**
 * 运营台展示层纯函数（无 React、无请求依赖）。
 * 金额为最小货币单位整数；文案与元展示在此维护，不发明后端没有的状态。
 * 争议文案与买家面（apps/web/lib/disputes.ts）同口径，两端看到同一套措辞。
 */

export const ORDER_STATUS_ZH: Record<AdminOrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  FULFILLING: '履约中',
  FULFILLED: '已履约',
  REFUND_PENDING: '退款中',
  REFUNDED: '已退款',
  CANCELLED: '已取消',
  EXPIRED: '已超时',
}

export function orderStatusZh(status: string): string {
  return ORDER_STATUS_ZH[status as AdminOrderStatus] ?? '未知状态'
}

/** 状态 → 角标修饰类（badge badge--xxx；与 web 端 BEM 风格一致） */
export const ORDER_STATUS_BADGE: Record<AdminOrderStatus, string> = {
  PENDING_PAYMENT: 'badge--warning',
  PAID: 'badge--success',
  FULFILLING: 'badge--info',
  FULFILLED: 'badge--success',
  REFUND_PENDING: 'badge--warning',
  REFUNDED: '',
  CANCELLED: '',
  EXPIRED: 'badge--danger',
}

export function orderBadgeClass(status: string): string {
  return ORDER_STATUS_BADGE[status as AdminOrderStatus] ?? ''
}

export const EVENT_STATUS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CANCELLED: '已取消',
  COMPLETED: '已结束',
}

export function eventStatusZh(status: string): string {
  return EVENT_STATUS_ZH[status] ?? '未知状态'
}

/** 最小货币单位整数 → 「3,880.00」（先除 100，千分位 + 两位小数） */
export function minorToYuan(minor: number): string {
  if (!Number.isFinite(minor)) return ''
  return (minor / 100).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** ISO → 「10-13 09:47」（本地时区），非法时间返回空串 */
export function formatAdminTime(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 可用量占比（0–1）：总量非正时按 0 处理（后端不返回负总量，防御展示） */
export function availableRatio(balance: Pick<PoolBalance, 'totalQuantity' | 'available'>): number {
  if (balance.totalQuantity <= 0) return 0
  return Math.max(0, Math.min(1, balance.available / balance.totalQuantity))
}

/** 预警线：可用量占比低于 20% 转预警色（原型 balance-bar 口径） */
export const LOW_STOCK_RATIO = 0.2

export function isLowStock(ratio: number): boolean {
  return ratio < LOW_STOCK_RATIO
}

/** 订单状态筛选：ALL 不过滤，其余按状态精确匹配 */
export function filterOrdersByStatus(
  orders: AdminOrder[],
  tab: 'ALL' | AdminOrderStatus,
): AdminOrder[] {
  return tab === 'ALL' ? orders : orders.filter((order) => order.status === tab)
}

export const DISPUTE_STATUS_ZH: Record<DisputeStatus, string> = {
  OPEN: '待受理',
  UNDER_REVIEW: '受理中',
  RESOLVED: '已裁决',
  WITHDRAWN: '已撤回',
}

export function disputeStatusZh(status: string): string {
  return DISPUTE_STATUS_ZH[status as DisputeStatus] ?? '未知状态'
}

/** 状态角标：待受理警示 / 受理中提示（待裁决）/ 已裁决成功；已撤回用默认样式 */
export const DISPUTE_STATUS_BADGE: Record<DisputeStatus, string> = {
  OPEN: 'badge--warning',
  UNDER_REVIEW: 'badge--info',
  RESOLVED: 'badge--success',
  WITHDRAWN: '',
}

export function disputeBadgeClass(status: string): string {
  return DISPUTE_STATUS_BADGE[status as DisputeStatus] ?? ''
}

export const DISPUTE_REASON_ZH: Record<DisputeReason, string> = {
  NOT_DELIVERED: '未交付',
  TICKET_INVALID: '票券无效',
  DESCRIPTION_MISMATCH: '与描述不符',
  OTHER: '其他',
}

export function disputeReasonZh(reason: string): string {
  return DISPUTE_REASON_ZH[reason as DisputeReason] ?? '未知原因'
}

export const DISPUTE_RESOLUTION_ZH: Record<DisputeResolution, string> = {
  REJECTED: '裁决：不支持',
  REFUND_APPROVED: '裁决：退款',
  REDELIVER_APPROVED: '裁决：重新交付',
}

export function disputeResolutionZh(resolution: string): string {
  return DISPUTE_RESOLUTION_ZH[resolution as DisputeResolution] ?? '未知结论'
}
