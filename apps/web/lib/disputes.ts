import type { DisputeReason, DisputeStatus, DisputeView, OrderStatus } from '@awn/api-client'

/**
 * 订单页争议入口的文案与守卫镜像（docs/api/contracts.md「争议端点」）。
 *
 * 前端自检只做与服务端同口径的提示（description 必填 / REFUND 金额 ∈ (0, 应付] /
 * REDELIVER 不带金额 / evidenceUrl 白名单域），最终裁决一律以服务端结论为准；
 * 这里拦不住的（订单状态、在途争议、非下单人）由后端 409 / 400 兜底展示。
 */

const REASON_LABEL_ZH: Record<DisputeReason, string> = {
  NOT_DELIVERED: '未交付',
  TICKET_INVALID: '票券无效',
  DESCRIPTION_MISMATCH: '与描述不符',
  OTHER: '其他',
}

const STATUS_LABEL_ZH: Record<DisputeStatus, string> = {
  OPEN: '待受理',
  UNDER_REVIEW: '受理中',
  RESOLVED: '已裁决',
  WITHDRAWN: '已撤回',
}

/** 素材/凭证存储域，与后端 StorageUrlValidator 兜底白名单同口径 */
export const DISPUTE_EVIDENCE_DOMAINS = ['assets.awn.site', 'cdn.awn.site', 'img.awn.site', 'video.awn.site'] as const

export function disputeReasonLabel(reason: DisputeReason): string {
  return REASON_LABEL_ZH[reason] ?? reason
}

const RESOLUTION_LABEL_ZH: Record<string, string> = {
  REJECTED: '裁决：不支持',
  REFUND_APPROVED: '裁决：退款',
  REDELIVER_APPROVED: '裁决：重新交付',
}

/** 裁决结论文案（裁决只记结论不动钱，退款执行走既有退款流） */
export function disputeResolutionLabel(resolution: string | null): string {
  if (!resolution) return ''
  return RESOLUTION_LABEL_ZH[resolution] ?? resolution
}

export function disputeStatusLabel(status: DisputeStatus): string {
  return STATUS_LABEL_ZH[status] ?? status
}

/** 状态徽章配色：待受理警示 / 已裁决成功；受理中与已撤回用默认样式 */
export function disputeStatusBadge(status: DisputeStatus): string {
  if (status === 'OPEN') return 'badge--warning'
  if (status === 'RESOLVED') return 'badge--success'
  return ''
}

/** 开争议资格：只认后端守卫同款状态集（PAID / FULFILLING / FULFILLED） */
export function canOpenDispute(status: OrderStatus | string): boolean {
  return status === 'PAID' || status === 'FULFILLING' || status === 'FULFILLED'
}

/** 在途争议（后端部分唯一索引同口径）：OPEN / UNDER_REVIEW */
export function hasInFlightDispute(disputes: DisputeView[]): boolean {
  return disputes.some((dispute) => dispute.status === 'OPEN' || dispute.status === 'UNDER_REVIEW')
}

/** 撤回资格：受理前后都可由买家撤回，进入裁决或撤回后不可 */
export function canWithdrawDispute(status: DisputeStatus): boolean {
  return status === 'OPEN' || status === 'UNDER_REVIEW'
}

/**
 * 元字符串转分：允许 0–2 位小数；负数 / 非数字 / 超两位小数一律 null（不猜值）。
 */
export function parseYuanToFen(input: string): number | null {
  const text = input.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null
  const [yuan, decimals = ''] = text.split('.')
  const padded = decimals.padEnd(2, '0')
  return Number(yuan) * 100 + Number(padded)
}

/** 凭证链接白名单（镜像 StorageUrlValidator：https + 白名单域，或 / 起的相对路径） */
export function isAllowedEvidenceUrl(url: string): boolean {
  if (url.startsWith('/')) return true
  if (!url.startsWith('https://')) return false
  const rest = url.slice('https://'.length)
  const host = (rest.includes('/') ? rest.slice(0, rest.indexOf('/')) : rest).split(':')[0]
  return (DISPUTE_EVIDENCE_DOMAINS as readonly string[]).includes(host)
}

export interface DisputeFormInput {
  reason: DisputeReason
  description: string
  evidenceUrl: string
  resolution: 'REFUND' | 'REDELIVER'
  amountYuan: string
}

/** 表单自检：返回中文错误文案或 null；通过不代表后端必过，只省一次必然失败的提交 */
export function validateDisputeForm(form: DisputeFormInput, payAmount: number): string | null {
  if (!form.description.trim()) return '请填写问题描述。'
  if (form.evidenceUrl.trim() && !isAllowedEvidenceUrl(form.evidenceUrl.trim())) {
    return `凭证链接需为平台存储域（${DISPUTE_EVIDENCE_DOMAINS.join(' / ')}）或 / 起的相对路径。`
  }
  if (form.resolution === 'REFUND') {
    const amount = parseYuanToFen(form.amountYuan)
    if (amount === null || amount <= 0 || amount > payAmount) {
      return `退款金额需大于 0 且不超过订单应付金额。`
    }
  }
  return null
}
