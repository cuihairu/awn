/**
 * 统一商品 / 票档组件契约。
 *
 * 商品卡片上的库存信息可以是缓存展示值，但点击购买后必须重新校验真实库存
 * （见 docs/architecture/campaign-rendering.md）。
 */

/** 可售状态展示枚举（与后端 availability 接口对齐） */
export type AvailabilityLabel = 'Selling' | 'PreSale' | 'LowStock' | 'Restocking' | 'SoldOut'

export interface TicketTier {
  productId: string
  name: string
  sessionId: string
  /** 最小货币单位整数，禁止浮点金额 */
  unitPriceMinor: number
  currency: 'CNY'
  quantityLimit: number
  availability: AvailabilityLabel
}

/** 展示层文案映射（情报与真实库存必须区分） */
export const AVAILABILITY_LABEL_ZH: Record<AvailabilityLabel, string> = {
  Selling: '售票中',
  PreSale: '预售中',
  LowStock: '余票紧张',
  Restocking: '补货中',
  SoldOut: '已售罄',
}
