// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import type { CampaignView } from '@awn/api-client'
import { CampaignList } from './CampaignList'

/**
 * 首页专场列表渲染契约（原型 web/home.html 的活动卡片 + GET /api/v1/campaigns）。
 * 卡片的目的地是 /campaigns/{code}；封面只出文字占位（素材红线：不生成图片代餐）。
 */

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

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

const campaign: CampaignView = {
  id: 1,
  campaignCode: 'jay-carnival-2026',
  title: '周杰伦「嘉年华」世界巡回演唱会',
  subtitle: '上海站 · 11 月 6 日 — 11 月 8 日',
  status: 'PUBLISHED',
  currentRevisionId: 3,
}

describe('CampaignList', () => {
  it('renders one card per published campaign linked to its page', () => {
    const el = render(
      <CampaignList
        campaigns={[
          campaign,
          {
            id: 2,
            campaignCode: 'f1-2026-china',
            title: '2026 F1 中国大奖赛',
            subtitle: '上海国际赛车场 · 10 月 30 日 — 11 月 1 日',
            status: 'PUBLISHED',
            currentRevisionId: 1,
          },
        ]}
      />,
    )

    const links = Array.from(el.querySelectorAll('a'))
    expect(links).toHaveLength(2)
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/campaigns/jay-carnival-2026',
      '/campaigns/f1-2026-china',
    ])
    expect(el.textContent).toContain('周杰伦「嘉年华」世界巡回演唱会')
    expect(el.textContent).toContain('上海站 · 11 月 6 日 — 11 月 8 日')
    expect(el.textContent).toContain('2026 F1 中国大奖赛')
    expect(el.textContent).toContain('查看详情')
  })

  it('renders nothing for an empty list', () => {
    const el = render(<CampaignList campaigns={[]} />)
    expect(el.textContent).toBe('')
    expect(el.querySelector('a')).toBeNull()
  })

  it('omits a missing subtitle instead of rendering an empty line', () => {
    const el = render(<CampaignList campaigns={[{ ...campaign, subtitle: null }]} />)
    const link = el.querySelector('a')
    expect(link).not.toBeNull()
    const spans = Array.from(link?.querySelectorAll('span') ?? [])
    expect(spans.some((span) => span.textContent?.trim() === '')).toBe(false)
  })

  it('uses a text placeholder cover and never emits an image', () => {
    const el = render(<CampaignList campaigns={[campaign]} />)
    expect(el.querySelectorAll('img')).toHaveLength(0)
    expect(el.textContent).toContain('专场 · 几何装饰')
  })
})
