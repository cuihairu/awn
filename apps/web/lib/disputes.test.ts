import { describe, expect, it } from 'vitest'
import type { DisputeStatus, DisputeView } from '@awn/api-client'
import {
  DISPUTE_EVIDENCE_DOMAINS,
  canOpenDispute,
  canWithdrawDispute,
  disputeReasonLabel,
  disputeResolutionLabel,
  disputeStatusBadge,
  disputeStatusLabel,
  hasInFlightDispute,
  isAllowedEvidenceUrl,
  parseYuanToFen,
  validateDisputeForm,
} from './disputes'

/**
 * 订单页争议入口的文案与守卫镜像（docs/api/contracts.md「争议端点」 +
 * modules/awn-order DisputeService 守卫）：
 * 前端只做与服务端同口径的自检提示，最终裁决始终以服务端结论为准。
 */

function dispute(status: DisputeStatus): DisputeView {
  return {
    disputeNo: `DSP-${status}`,
    orderNo: 'ORD-1',
    openedBy: 7,
    sellerId: null,
    listingNo: null,
    unitNo: null,
    reason: 'NOT_DELIVERED',
    description: '示例',
    evidenceUrl: null,
    requestedResolution: 'REFUND',
    requestedAmount: 68000,
    status,
    resolution: null,
    resolutionNote: null,
    decidedBy: null,
    decidedAt: null,
    createdAt: '2026-10-15T10:00:00Z',
  }
}

describe('labels and badges', () => {
  it('labels every dispute status and reason in Chinese', () => {
    expect(disputeStatusLabel('OPEN')).toBe('待受理')
    expect(disputeStatusLabel('UNDER_REVIEW')).toBe('受理中')
    expect(disputeStatusLabel('RESOLVED')).toBe('已裁决')
    expect(disputeStatusLabel('WITHDRAWN')).toBe('已撤回')
    expect(disputeReasonLabel('NOT_DELIVERED')).toBe('未交付')
    expect(disputeReasonLabel('TICKET_INVALID')).toBe('票券无效')
    expect(disputeReasonLabel('DESCRIPTION_MISMATCH')).toBe('与描述不符')
    expect(disputeReasonLabel('OTHER')).toBe('其他')
  })

  it('picks a badge tone per status', () => {
    expect(disputeStatusBadge('OPEN')).toContain('warning')
    expect(disputeStatusBadge('UNDER_REVIEW')).toBe('')
    expect(disputeStatusBadge('RESOLVED')).toContain('success')
    expect(disputeStatusBadge('WITHDRAWN')).toBe('')
  })

  it('labels every adjudication resolution', () => {
    expect(disputeResolutionLabel('REJECTED')).toContain('不支持')
    expect(disputeResolutionLabel('REFUND_APPROVED')).toContain('退款')
    expect(disputeResolutionLabel('REDELIVER_APPROVED')).toContain('重新交付')
    expect(disputeResolutionLabel(null)).toBe('')
  })
})

describe('eligibility', () => {
  it('allows opening a dispute only for paid-through-fulfilled orders', () => {
    expect(canOpenDispute('PAID')).toBe(true)
    expect(canOpenDispute('FULFILLING')).toBe(true)
    expect(canOpenDispute('FULFILLED')).toBe(true)
    for (const status of ['PENDING_PAYMENT', 'REFUND_PENDING', 'REFUNDED', 'CANCELLED', 'EXPIRED']) {
      expect(canOpenDispute(status)).toBe(false)
    }
  })

  it('treats OPEN and UNDER_REVIEW as in-flight and withdrawable', () => {
    expect(hasInFlightDispute([dispute('OPEN'), dispute('RESOLVED')])).toBe(true)
    expect(hasInFlightDispute([dispute('RESOLVED'), dispute('WITHDRAWN')])).toBe(false)
    expect(hasInFlightDispute([])).toBe(false)
    expect(canWithdrawDispute('OPEN')).toBe(true)
    expect(canWithdrawDispute('UNDER_REVIEW')).toBe(true)
    expect(canWithdrawDispute('RESOLVED')).toBe(false)
    expect(canWithdrawDispute('WITHDRAWN')).toBe(false)
  })
})

describe('parseYuanToFen', () => {
  it('parses yuan amounts into minor units', () => {
    expect(parseYuanToFen('680')).toBe(68000)
    expect(parseYuanToFen('680.5')).toBe(68050)
    expect(parseYuanToFen('0.01')).toBe(1)
  })

  it('rejects negative, malformed and over-precision input', () => {
    expect(parseYuanToFen('-1')).toBeNull()
    expect(parseYuanToFen('abc')).toBeNull()
    expect(parseYuanToFen('680.123')).toBeNull()
    expect(parseYuanToFen('')).toBeNull()
  })
})

describe('isAllowedEvidenceUrl', () => {
  it('accepts platform https hosts and relative paths only', () => {
    for (const url of [
      'https://assets.awn.site/evidence/1.png',
      'https://cdn.awn.site/e/2.jpg',
      'https://img.awn.site/e/3.png',
      'https://video.awn.site/e/4.mp4',
      '/evidence/relative.png',
    ]) {
      expect(isAllowedEvidenceUrl(url), url).toBe(true)
    }
  })

  it('rejects other hosts, plain http and malformed values', () => {
    for (const url of [
      'https://evil.example.com/e.png',
      'http://assets.awn.site/e.png',
      'assets.awn.site/e.png',
      'ftp://assets.awn.site/e.png',
      'javascript:alert(1)',
    ]) {
      expect(isAllowedEvidenceUrl(url), url).toBe(false)
    }
  })

  it('keeps the whitelist aligned with the backend StorageUrlValidator set', () => {
    expect([...DISPUTE_EVIDENCE_DOMAINS].sort()).toEqual([
      'assets.awn.site',
      'cdn.awn.site',
      'img.awn.site',
      'video.awn.site',
    ])
  })
})

describe('validateDisputeForm', () => {
  const payAmount = 100000

  it('accepts a refund within the payable amount', () => {
    expect(
      validateDisputeForm(
        { reason: 'NOT_DELIVERED', description: '未出票', evidenceUrl: '', resolution: 'REFUND', amountYuan: '500' },
        payAmount,
      ),
    ).toBeNull()
  })

  it('accepts redelivery without any amount', () => {
    expect(
      validateDisputeForm(
        { reason: 'TICKET_INVALID', description: '票券无效', evidenceUrl: '', resolution: 'REDELIVER', amountYuan: '' },
        payAmount,
      ),
    ).toBeNull()
  })

  it('requires a description', () => {
    expect(
      validateDisputeForm(
        { reason: 'OTHER', description: '   ', evidenceUrl: '', resolution: 'REFUND', amountYuan: '1' },
        payAmount,
      ),
    ).toContain('问题描述')
  })

  it('requires a refund amount within the payable amount', () => {
    expect(
      validateDisputeForm(
        { reason: 'OTHER', description: 'x', evidenceUrl: '', resolution: 'REFUND', amountYuan: '' },
        payAmount,
      ),
    ).toContain('退款金额')
    expect(
      validateDisputeForm(
        { reason: 'OTHER', description: 'x', evidenceUrl: '', resolution: 'REFUND', amountYuan: '0' },
        payAmount,
      ),
    ).toContain('退款金额')
    expect(
      validateDisputeForm(
        { reason: 'OTHER', description: 'x', evidenceUrl: '', resolution: 'REFUND', amountYuan: '1000.01' },
        payAmount,
      ),
    ).toContain('退款金额')
  })

  it('rejects bad evidence urls with the allowed domains spelled out', () => {
    const error = validateDisputeForm(
      { reason: 'OTHER', description: 'x', evidenceUrl: 'https://example.com/e.png', resolution: 'REDELIVER', amountYuan: '' },
      payAmount,
    )
    expect(error).toContain('凭证链接')
    expect(error).toContain('assets.awn.site')
  })
})
