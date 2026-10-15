import type { CSSProperties } from 'react'

/**
 * 位置示意图（docs/design/map-display-plan.md 方案 B）前端契约。
 *
 * 图由运营从公开来源取用，平台不提供也不代发任何地图数据 API；图上标记位置由运营
 * 随图给定（百分比 0-100，相对图片左上角）——示意图没有地理范围，场馆经纬度无法
 * 映射到图上位置，客户端不得自行猜测。
 */

/** 示意图说明文案：明示非精确地图，避免被当成精确导航或路线规划依据 */
export const GEO_IMAGE_CAPTION = '位置示意图，非精确地图'

/**
 * 图上标记样式（百分比定位）；成对且在 0-100 内才落标记。
 * 半截标记必然指错位置，比没有更糟，一律不渲染。
 */
export function mapMarkerStyle(
  x: number | null | undefined,
  y: number | null | undefined,
): CSSProperties | null {
  if (x == null || y == null) return null
  if (x < 0 || x > 100 || y < 0 || y > 100) return null
  return { left: `${x}%`, top: `${y}%` }
}
