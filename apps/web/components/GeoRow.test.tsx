// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import type { SessionVenueView } from '@awn/api-client'
import { GEO_IMAGE_CAPTION } from '../lib/geo-image'
import { GeoRow } from './GeoRow'

/**
 * 位置示意图渲染契约（docs/design/map-display-plan.md 方案 B）：
 * 无图回退文字行（链路问题不报白屏）；图加载失败同样回退；
 * 标记半截 / 越界不渲染；无图且无文字不渲染假地点。
 */

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let container: HTMLDivElement | null = null
let root: Root | null = null

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  container = null
  root = null
})

function render(element: React.ReactNode) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(element))
  return container
}

const geo = (overrides: Partial<SessionVenueView> = {}): SessionVenueView => ({
  venueCode: 'v-1',
  venueName: '北京工人体育场',
  city: '北京',
  address: '朝阳工人体育场北路',
  latitude: 39.9308,
  longitude: 116.4435,
  mapImageUrl: 'https://assets.awn.site/venues/gongti.png',
  mapMarkerX: 62,
  mapMarkerY: 40,
  ...overrides,
})

describe('GeoRow', () => {
  it('renders the text-only row when there is no map image (regression)', () => {
    const node = render(<GeoRow geo={geo({ mapImageUrl: null, mapMarkerX: null, mapMarkerY: null })} />)
    expect(node.querySelector('.geo-row')?.textContent).toContain('北京工人体育场')
    expect(node.querySelector('.geo-map')).toBeNull()
  })

  it('renders nothing for missing geo or a map-less all-empty venue', () => {
    expect(render(<GeoRow geo={null} />).querySelector('.geo-row-block')).toBeNull()
    expect(
      render(
        <GeoRow
          geo={{
            venueCode: 'v-1',
            venueName: '',
            city: null,
            address: null,
            latitude: null,
            longitude: null,
            mapImageUrl: null,
            mapMarkerX: null,
            mapMarkerY: null,
          }}
        />,
      ).querySelector('.geo-row-block'),
    ).toBeNull()
  })

  it('renders the schematic image with caption and venue name when present', () => {
    const node = render(<GeoRow geo={geo()} />)
    const map = node.querySelector('.geo-map')
    expect(map?.getAttribute('role')).toBe('img')
    expect(map?.getAttribute('aria-label')).toBe('北京工人体育场位置示意图，非精确地图')
    expect(node.querySelector('img')?.getAttribute('src')).toBe(
      'https://assets.awn.site/venues/gongti.png',
    )
    expect(node.querySelector('.geo-map-caption')?.textContent).toBe(GEO_IMAGE_CAPTION)
  })

  it('positions the marker at the ops-given percentages', () => {
    const node = render(<GeoRow geo={geo()} />)
    const marker = node.querySelector<HTMLElement>('.geo-map-marker')
    expect(marker?.style.left).toBe('62%')
    expect(marker?.style.top).toBe('40%')
  })

  it('skips the marker when only half of the pair is given', () => {
    const node = render(<GeoRow geo={geo({ mapMarkerX: 62, mapMarkerY: null })} />)
    expect(node.querySelector('.geo-map-marker')).toBeNull()
    expect(node.querySelector('.geo-map')).not.toBeNull()
  })

  it('falls back to the text row when the image fails to load', () => {
    const node = render(<GeoRow geo={geo()} />)
    const img = node.querySelector('img')
    expect(img).not.toBeNull()
    act(() => {
      img?.dispatchEvent(new Event('error'))
    })
    expect(node.querySelector('img')).toBeNull()
    expect(node.querySelector('.geo-map')).toBeNull()
    expect(node.querySelector('.geo-row')?.textContent).toContain('北京工人体育场')
  })
})
