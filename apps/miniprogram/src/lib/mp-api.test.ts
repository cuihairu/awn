import { afterEach, describe, expect, it } from 'vitest'
import { mpApi, setTransport } from './mp-api'
import type { Transport, TransportRequest } from './mp-request'

afterEach(() => {
  setTransport(async (options: TransportRequest) => ({
    statusCode: 200,
    data: null,
  }))
})

/** 记录调用并返回空成功体的 transport */
function recordingTransport(data: unknown = []) {
  const calls: TransportRequest[] = []
  const transport: Transport = async (options) => {
    calls.push(options)
    return { statusCode: 200, data }
  }
  return { calls, transport }
}

describe('mp-api 端点契约', () => {
  it('sends the idempotency key header on order creation', async () => {
    const { calls, transport } = recordingTransport({ orderNo: 'AWN-1' })
    setTransport(transport)
    await mpApi.createOrder('key-1', '42', [
      {
        poolCode: 'POOL-1',
        productId: 'p-1',
        productNameSnapshot: 'VIP票',
        sessionId: '9',
        quantity: 2,
        unitPrice: 199900,
        discountAmount: 0,
        feeAmount: 0,
      },
    ])
    const orderCall = calls[0]
    expect(orderCall.url).toBe('/api/v1/orders')
    expect(orderCall.method).toBe('POST')
    expect(orderCall.header?.['Idempotency-Key']).toBe('key-1')
    expect(orderCall.data).toMatchObject({ userId: '42', currency: 'CNY' })
  })

  it('targets the session products and campaign render endpoints', async () => {
    const { calls, transport } = recordingTransport([])
    setTransport(transport)
    await mpApi.getSessionProducts('9')
    await mpApi.renderCampaign('CAMP-1')
    expect(calls[0].url).toBe('/api/v1/sessions/9/products')
    expect(calls[1].url).toBe('/api/v1/campaigns/CAMP-1/render')
  })

  it('creates payments with channel only (amount is server-decided)', async () => {
    const { calls, transport } = recordingTransport({ paymentNo: 'PAY-1' })
    setTransport(transport)
    await mpApi.createPayment('AWN-1', 'sandbox')
    expect(calls[0].url).toBe('/api/v1/orders/AWN-1/payments')
    expect(calls[0].data).toEqual({ channel: 'sandbox' })
  })

  it('rejects calls before setTransport is wired', async () => {
    setTransport(null as never)
    await expect(mpApi.listEvents()).rejects.toThrow(/transport not set/)
  })
})
