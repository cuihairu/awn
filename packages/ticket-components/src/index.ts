/**
 * 统一商品 / 票档组件契约。
 *
 * 商品卡片上的库存信息可以是缓存展示值，但点击购买后必须重新校验真实库存
 * （见 docs/architecture/campaign-rendering.md）。
 */

/** 可售状态展示枚举（与后端 availability 接口对齐） */
/**
 * 与后端可售摘要对齐（GET /api/v1/products/{id}/availability 的 available 字段）：
 * null = 未建库存池（未开售）、0 = 已售罄、> 阈值 = 余票紧张、其余 = 售票中。
 * 枚举不留无后端来源的取值。
 */
export type AvailabilityLabel = 'Selling' | 'LowStock' | 'SoldOut' | 'NotOnSale'

export interface TicketTier {
  productId: string
  name: string
  sessionId: string
  /** 数量行下单所需（POST /orders 的 lines[].poolCode）；后端未建池时为 null */
  poolCode?: string | null
  /** 最小货币单位整数，禁止浮点金额 */
  unitPriceMinor: number
  currency: 'CNY'
  /** 单次限购张数；后端购买规则未下发时为 null（卡片不渲染限购行） */
  quantityLimit: number | null
  availability: AvailabilityLabel
}

/** 展示层文案映射（情报与真实库存必须区分） */
export const AVAILABILITY_LABEL_ZH: Record<AvailabilityLabel, string> = {
  Selling: '售票中',
  LowStock: '余票紧张',
  SoldOut: '已售罄',
  NotOnSale: '未开售',
}

export { TierCard } from './tier-card'
export type { TierCardProps } from './tier-card'
