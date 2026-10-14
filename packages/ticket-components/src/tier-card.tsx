'use client'

import { AVAILABILITY_LABEL_ZH, type AvailabilityLabel, type TicketTier } from './index'

/**
 * 票档卡（原型 tier-row）：票档圆点 + 名称 + 可售状态 + 价格 + 限购。
 * 展示数量为缓存值，下单时后端仍原子重校验；
 * 售罄档置灰并给出「已售罄」状态，不下发可点击的购买动作。
 */

const TIER_DOT_CLASS: Record<number, string> = {
  1: 'tier-dot-1',
  2: 'tier-dot-2',
  3: 'tier-dot-3',
  4: 'tier-dot-4',
  5: 'tier-dot-5',
}

const STATUS_BADGE: Record<AvailabilityLabel, string> = {
  Selling: 'badge-success',
  PreSale: 'badge-info',
  LowStock: 'badge-warning',
  Restocking: 'badge-info',
  SoldOut: 'badge-outline',
}

export interface TierCardProps {
  tier: TicketTier
  /** 1–5，映射 tier-dot 样式（与原型 tier-dot-1..5 一致） */
  tierLevel: number
  onSelect?: (tier: TicketTier) => void
}

export function TierCard({ tier, tierLevel, onSelect }: TierCardProps) {
  const dotClass = TIER_DOT_CLASS[tierLevel] ?? 'tier-dot-1'
  const isSoldOut = tier.availability === 'SoldOut'
  const yuan = (tier.unitPriceMinor / 100).toFixed(2)

  return (
    <button
      type="button"
      className="tier-row"
      aria-disabled={isSoldOut}
      onClick={() => !isSoldOut && onSelect?.(tier)}
    >
      <div className="row" style={{ gap: 12 }}>
        <span className={`tier-dot ${dotClass}`} aria-hidden />
        <div>
          <strong>{tier.name}</strong>
          <div className="muted" style={{ fontSize: 'var(--fs-xs)' }}>
            {AVAILABILITY_LABEL_ZH[tier.availability]} · 限购 {tier.quantityLimit} 张
          </div>
        </div>
      </div>
      <div className="row">
        <span className="price price-yuan num">{yuan}</span>
        <span className={`badge ${STATUS_BADGE[tier.availability]}`}>
          {AVAILABILITY_LABEL_ZH[tier.availability]}
        </span>
      </div>
    </button>
  )
}
