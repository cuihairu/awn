import Taro from '@tarojs/taro'
import type { Transport, TransportRequest, TransportResponse } from './mp-request'

/**
 * Taro.request 适配器：页面运行时的默认 transport。
 * API 域名取运行时配置（开发默认本地 awn-api；发布时在 app 启动处覆盖）。
 * 小程序对数据序列化：data 传对象即 JSON body，header 显式声明内容类型。
 */

export const API_HOST = process.env.AWN_API_HOST ?? 'http://localhost:8080'

export const taroTransport: Transport = async (
  options: TransportRequest,
): Promise<TransportResponse> => {
  const res = await Taro.request({
    url: `${API_HOST}${options.url}`,
    method: options.method,
    header: {
      'content-type': 'application/json',
      ...(options.header ?? {}),
    },
    data: options.data as never,
  })
  return { statusCode: res.statusCode, data: res.data }
}
