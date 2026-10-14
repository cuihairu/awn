/**
 * 小程序请求内核（与平台解耦）。
 *
 * Taro.request 的形态抽象为 Transport（页面运行时由 taro-transport 适配），
 * 单测注入内存 transport，不依赖 @tarojs 运行时。
 * 非 2xx 一律抛 MpApiError（带 statusCode 与后端错误码），调用方据此区分
 * 「资源不存在 / 未发布」（404）与「网络或服务故障」，错误信封不当成功体用。
 */

/** 与 Taro.request 参数/返回同构的最小面 */
export interface TransportRequest {
  url: string
  method: 'GET' | 'POST'
  header?: Record<string, string>
  data?: unknown
}

export interface TransportResponse {
  statusCode: number
  data: unknown
}

export type Transport = (options: TransportRequest) => Promise<TransportResponse>

export class MpApiError extends Error {
  readonly statusCode: number
  readonly code: string

  constructor(statusCode: number, code: string, message: string) {
    super(message)
    this.name = 'MpApiError'
    this.statusCode = statusCode
    this.code = code
  }
}

function errorMessage(data: unknown, fallback: string): string {
  const envelope = data as { message?: unknown } | null
  return typeof envelope?.message === 'string' ? envelope.message : fallback
}

function errorCode(data: unknown, statusCode: number): string {
  const envelope = data as { code?: unknown } | null
  return typeof envelope?.code === 'string' ? envelope.code : `HTTP_${statusCode}`
}

export async function requestJson<T>(
  transport: Transport,
  options: TransportRequest,
): Promise<T> {
  const res = await transport(options)
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw new MpApiError(
      res.statusCode,
      errorCode(res.data, res.statusCode),
      errorMessage(res.data, `request failed: ${res.statusCode}`),
    )
  }
  return res.data as T
}
