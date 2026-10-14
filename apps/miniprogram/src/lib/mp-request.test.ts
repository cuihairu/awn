import { describe, expect, it } from 'vitest'
import { MpApiError, requestJson, type Transport } from './mp-request'

function transportOf(responses: Array<{ statusCode: number; data: unknown }>): Transport {
  let index = 0
  return async () => {
    const res = responses[Math.min(index, responses.length - 1)]
    index += 1
    return res
  }
}

describe('mp-request 内核', () => {
  it('resolves 2xx bodies as typed json', async () => {
    const body = [{ id: 'e1' }]
    const result = await requestJson<{ id: string }[]>(
      transportOf([{ statusCode: 200, data: body }]),
      { url: '/api/v1/events', method: 'GET' },
    )
    expect(result).toEqual(body)
  })

  it('rejects non-2xx with MpApiError carrying backend code', async () => {
    const promise = requestJson(
      transportOf([
        { statusCode: 404, data: { code: 'EVENT_NOT_FOUND', message: 'event not found: 9' } },
      ]),
      { url: '/api/v1/events/9', method: 'GET' },
    )
    await expect(promise).rejects.toMatchObject({
      name: 'MpApiError',
      statusCode: 404,
      code: 'EVENT_NOT_FOUND',
    })
    await promise.catch((err: unknown) => {
      expect((err as MpApiError).message).toBe('event not found: 9')
    })
  })

  it('falls back to HTTP_x code when envelope is missing', async () => {
    await expect(
      requestJson(transportOf([{ statusCode: 500, data: null }]), {
        url: '/api/v1/events',
        method: 'GET',
      }),
    ).rejects.toMatchObject({ statusCode: 500, code: 'HTTP_500' })
  })
})
