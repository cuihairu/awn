import { afterEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, ApiError, listOrderDisputes, openDispute, withdrawDispute } from '../src/index'

/**
 * 争议客户端契约（docs/api/contracts.md「争议端点」）：
 * 买家面只有开单 / 列表 / 撤回三个端点，裁决在 /admin/disputes/**（客户端不提供）。
 * 开单成功 201；守卫失败按后端错误信封抛 ApiError（DISPUTE_CONFLICT / VALIDATION_FAILED）。
 */

function mockFetchOnce(body: unknown, res: { ok?: boolean; status?: number } = {}) {
  const fn = vi.fn().mockResolvedValue({
    ok: res.ok ?? true,
    status: res.status ?? 200,
    statusText: 'OK',
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

const disputeView = {
  disputeNo: 'DSP-1',
  orderNo: 'ORD-1',
  openedBy: 7,
  sellerId: null,
  listingNo: null,
  unitNo: null,
  reason: 'NOT_DELIVERED',
  description: '演出当天未出票',
  evidenceUrl: null,
  requestedResolution: 'REFUND',
  requestedAmount: 68000,
  status: 'OPEN',
  resolution: null,
  resolutionNote: null,
  decidedBy: null,
  decidedAt: null,
  createdAt: '2026-10-15T10:00:00Z',
}

describe('openDispute', () => {
  it('posts to the order dispute endpoint and reads the 201 view', async () => {
    const fn = mockFetchOnce(disputeView, { status: 201 })
    const view = await openDispute('ORD-1', {
      userId: 7,
      reason: 'NOT_DELIVERED',
      description: '演出当天未出票',
      evidenceUrl: null,
      requestedResolution: 'REFUND',
      requestedAmount: 68000,
    })

    expect(view.disputeNo).toBe('DSP-1')
    const [url, init] = fn.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/orders/ORD-1/disputes`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      userId: 7,
      reason: 'NOT_DELIVERED',
      description: '演出当天未出票',
      evidenceUrl: null,
      requestedResolution: 'REFUND',
      requestedAmount: 68000,
    })
  })

  it('surfaces guard failures as ApiError with the backend code', async () => {
    mockFetchOnce(
      { code: 'DISPUTE_CONFLICT', message: 'order already has an in-flight dispute', traceId: 't' },
      { ok: false, status: 409 },
    )
    await expect(
      openDispute('ORD-1', {
        userId: 7,
        reason: 'NOT_DELIVERED',
        description: 'x',
        evidenceUrl: null,
        requestedResolution: 'REDELIVER',
        requestedAmount: null,
      }),
    ).rejects.toMatchObject({ name: 'ApiError', status: 409, code: 'DISPUTE_CONFLICT' })
  })
})

describe('listOrderDisputes', () => {
  it('queries the order disputes with the userId parameter', async () => {
    const fn = mockFetchOnce([disputeView])
    const list = await listOrderDisputes('ORD-1', 7)

    expect(list).toHaveLength(1)
    const [url] = fn.mock.calls[0] as [string]
    expect(url).toBe(`${API_BASE}/orders/ORD-1/disputes?userId=7`)
  })

  it('propagates the backend 400 when userId is missing', async () => {
    mockFetchOnce({ code: 'VALIDATION_FAILED', message: 'userId is required' }, { ok: false, status: 400 })
    await expect(listOrderDisputes('ORD-1', 0)).rejects.toBeInstanceOf(ApiError)
  })
})

describe('withdrawDispute', () => {
  it('posts the userId to the withdraw endpoint', async () => {
    const fn = mockFetchOnce({ ...disputeView, status: 'WITHDRAWN' })
    const view = await withdrawDispute('DSP-1', 7)

    expect(view.status).toBe('WITHDRAWN')
    const [url, init] = fn.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/disputes/DSP-1/withdraw`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ userId: 7 })
  })

  it('maps a 404 to DISPUTE_NOT_FOUND', async () => {
    mockFetchOnce({ code: 'DISPUTE_NOT_FOUND', message: 'dispute not found' }, { ok: false, status: 404 })
    await expect(withdrawDispute('DSP-404', 7)).rejects.toMatchObject({
      status: 404,
      code: 'DISPUTE_NOT_FOUND',
    })
  })
})
