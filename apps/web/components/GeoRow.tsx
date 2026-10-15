import { useState } from 'react'
import type { SessionVenueView } from '@awn/api-client'
import { GEO_IMAGE_CAPTION, mapMarkerStyle } from '../lib/geo-image'
import { geoLine } from '../lib/event-labels'

/**
 * geo-row（位置示意图 + 场馆名 / 城市 / 地址行，样式见 globals.css .geo-row / .geo-map）。
 *
 * 位置示意图（方案 B 首方静态图）：运营提供的图 + 运营给定的图上标记位置，
 * 明示「非精确地图」；无图或图加载失败一律回退现有文字行（链路问题不报白屏），
 * 文字行全空且无图返回 null——不渲染假地点。
 */
export function GeoRow({ geo }: { geo: SessionVenueView | null }) {
  const line = geoLine(geo)
  const [broken, setBroken] = useState(false)
  const imageUrl = geo?.mapImageUrl ?? null
  const marker = !broken && imageUrl ? mapMarkerStyle(geo?.mapMarkerX, geo?.mapMarkerY) : null

  if (!line && !imageUrl) return null

  return (
    <span className="geo-row-block">
      {imageUrl && !broken && (
        <span
          className="geo-map"
          role="img"
          aria-label={`${geo?.venueName ?? '场馆'}${GEO_IMAGE_CAPTION}`}
        >
          <span className="geo-map-frame">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              className="geo-map-image"
              src={imageUrl}
              alt=""
              loading="lazy"
              onError={() => setBroken(true)}
            />
            {marker && <span className="geo-map-marker" style={marker} aria-hidden="true" />}
          </span>
          <span className="geo-map-caption">{GEO_IMAGE_CAPTION}</span>
        </span>
      )}
      {line && (
        <span className="geo-row muted">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 21s-6.5-5.6-6.5-10.2a6.5 6.5 0 1 1 13 0C18.5 15.4 12 21 12 21z" />
            <circle cx="12" cy="10.5" r="2.4" fill="var(--t-surface, #fff)" />
          </svg>
          {line}
        </span>
      )}
    </span>
  )
}
