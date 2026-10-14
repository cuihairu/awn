import type { SessionVenueView } from '@awn/api-client'
import { geoLine } from '../lib/event-labels'

/**
 * geo-row（场馆名 / 城市 / 地址行，样式见 globals.css .geo-row）。
 * geo 为 null 或全空返回 null——场馆信息不全不渲染假地点。
 */
export function GeoRow({ geo }: { geo: SessionVenueView | null }) {
  const line = geoLine(geo)
  if (!line) return null

  return (
    <span className="geo-row muted">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 21s-6.5-5.6-6.5-10.2a6.5 6.5 0 1 1 13 0C18.5 15.4 12 21 12 21z" />
        <circle cx="12" cy="10.5" r="2.4" fill="var(--t-surface, #fff)" />
      </svg>
      {line}
    </span>
  )
}
