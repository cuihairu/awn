import { describe, expect, it } from 'vitest'
import { GEO_IMAGE_CAPTION, mapMarkerStyle } from './geo-image'

/**
 * 位置示意图前端契约（docs/design/map-display-plan.md 方案 B）：
 * 标记位置由运营随图给定（百分比 0-100，相对图片左上角），成对出现；
 * 半截或越界标记一律不渲染——半截标记必然指错位置，比没有更糟。
 */

describe('mapMarkerStyle', () => {
  it('positions the marker at the given percentages', () => {
    expect(mapMarkerStyle(50, 50)).toEqual({ left: '50%', top: '50%' })
    expect(mapMarkerStyle(0, 0)).toEqual({ left: '0%', top: '0%' })
    expect(mapMarkerStyle(100, 100)).toEqual({ left: '100%', top: '100%' })
  })

  it('returns null when only half of the marker pair is given', () => {
    expect(mapMarkerStyle(50, null)).toBeNull()
    expect(mapMarkerStyle(null, 50)).toBeNull()
    expect(mapMarkerStyle(undefined, 50)).toBeNull()
    expect(mapMarkerStyle(50, undefined)).toBeNull()
  })

  it('returns null when either coordinate is out of the 0-100 range', () => {
    expect(mapMarkerStyle(-1, 50)).toBeNull()
    expect(mapMarkerStyle(50, 101)).toBeNull()
    expect(mapMarkerStyle(101, -1)).toBeNull()
  })
})

describe('GEO_IMAGE_CAPTION', () => {
  it('states plainly that the image is a schematic, not a precise map', () => {
    expect(GEO_IMAGE_CAPTION).toBe('位置示意图，非精确地图')
  })
})
