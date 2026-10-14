// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CampaignRenderer } from '../src/renderer'
import type { CampaignConfig } from '../src/index'

/**
 * 渲染器契约（docs/architecture/campaign-rendering.md）：
 * 白名单类型渲染对应区块；未知类型拒绝渲染（不抛错不注入）；
 * props 只输出文本与结构——任何 section 都不得产生 script/iframe/onclick 注入面。
 */

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let container: HTMLDivElement | null = null
let root: Root | null = null

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  container = null
  root = null
  vi.restoreAllMocks()
})

function render(config: CampaignConfig) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(<CampaignRenderer config={config} />))
  return container
}

const baseConfig: CampaignConfig = {
  schemaVersion: 1,
  template: 'concert',
  seo: { title: '专场', description: '示例' },
  sections: [],
}

describe('CampaignRenderer', () => {
  it('renders whitelisted sections with their props as text', () => {
    const node = render({
      ...baseConfig,
      sections: [
        { id: 's1', type: 'heroBanner', props: { title: '星河巡回', subtitle: '深圳站' } },
        { id: 's2', type: 'rulePanel', props: { title: '购票须知', rules: ['实名购票', '限购 4 张'] } },
      ],
    })
    expect(node.querySelector('[data-section="heroBanner"]')?.textContent).toContain('星河巡回')
    expect(node.querySelectorAll('.entry')).toHaveLength(2)
  })

  it('renders every whitelisted section type without throwing', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const node = render({
      ...baseConfig,
      sections: (
        [
          'heroBanner',
          'richText',
          'imageGallery',
          'videoEmbed',
          'eventList',
          'ticketGrid',
          'ticketComparison',
          'priceTrend',
          'channelComparison',
          'countdown',
          'rulePanel',
          'faq',
          'noticeBar',
        ] as const
      ).map((type, i) => ({ id: `s${i}`, type, props: {} })),
    })
    expect(node.querySelectorAll('[data-section]')).toHaveLength(13)
    expect(warn).not.toHaveBeenCalled()
  })

  it('refuses to render unknown section types', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const node = render({
      ...baseConfig,
      sections: [
        // 模拟白名单外的越权类型（构造期绕过类型系统）
        { id: 'x', type: 'arbitraryHtml' as never, props: { html: '<img src=x onerror=alert(1)>' } },
        { id: 'ok', type: 'noticeBar', props: { message: '正常区块' } },
      ],
    })
    expect(warn).toHaveBeenCalledWith('campaign-renderer: unknown section type arbitraryHtml')
    expect(node.textContent).toContain('正常区块')
    expect(node.querySelector('img')).toBeNull()
  })

  it('never emits script/iframe/inline handlers even with hostile props', () => {
    const node = render({
      ...baseConfig,
      sections: [
        { id: 's1', type: 'heroBanner', props: { title: '<script>alert(1)</script>' } },
        { id: 's2', type: 'richText', props: { title: 'x', paragraphs: ['<iframe src="evil">'] } },
      ],
    })
    expect(node.querySelector('script')).toBeNull()
    expect(node.querySelector('iframe')).toBeNull()
    expect(node.innerHTML).not.toContain('onerror')
    expect(node.textContent).toContain('<script>alert(1)</script>')
  })

  it('renders image placeholder instead of img when src is missing', () => {
    const node = render({
      ...baseConfig,
      sections: [
        {
          id: 'g',
          type: 'imageGallery',
          props: { title: '图集', images: [{ alt: '示意封面' }, { alt: '授权图', src: 'https://example.com/licensed.jpg' }] },
        },
      ],
    })
    expect(node.querySelectorAll('img')).toHaveLength(1)
    expect(node.textContent).toContain('示意封面')
  })
})
