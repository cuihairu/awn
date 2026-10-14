import { describe, expect, it } from 'vitest'
import { MP_SECTION_TYPES, normalizeSection, normalizeSections } from './sections'
import type { RenderedSection } from './mp-api'

function section(type: string, props: Record<string, unknown> = {}): RenderedSection {
  return { id: `${type}-1`, type, props, content: null }
}

describe('专场渲染子集（文本优先 7 类）', () => {
  it('keeps the documented subset', () => {
    expect(MP_SECTION_TYPES).toEqual([
      'heroBanner',
      'richText',
      'noticeBar',
      'rulePanel',
      'faq',
      'priceTrend',
      'countdown',
    ])
  })

  it('normalizes text props per type with default titles', () => {
    expect(normalizeSection(section('heroBanner', { title: '星河巡回', subtitle: '深圳站' })))
      .toMatchObject({ type: 'heroBanner', title: '星河巡回' })
    expect(
      normalizeSection(section('richText', { title: '演出介绍', paragraphs: ['第一段', 42] })),
    ).toMatchObject({ type: 'richText', paragraphs: ['第一段'] })
    expect(normalizeSection(section('rulePanel', { rules: ['实名入场', '不可退'] })))
      .toMatchObject({ title: '购票须知', paragraphs: ['实名入场', '不可退'] })
    expect(normalizeSection(section('noticeBar', { message: '谨防黄牛' })))
      .toMatchObject({ type: 'noticeBar', entries: [{ key: 'message', value: '谨防黄牛' }] })
    expect(normalizeSection(section('countdown', { until: '2026-11-02 19:30' })))
      .toMatchObject({ title: '倒计时', entries: [{ key: 'until', value: '2026-11-02 19:30' }] })
    expect(normalizeSection(section('priceTrend', { summary: '近 30 天均价下行' })))
      .toMatchObject({ title: '价格趋势', entries: [{ key: 'summary', value: '近 30 天均价下行' }] })
    expect(
      normalizeSection(section('faq', {
        entries: [{ question: '能退吗', answer: '看规则' }, { q: '旧键', a: '不认' }],
      })),
    ).toMatchObject({
      title: '常见问题',
      entries: [
        { key: '能退吗', value: '看规则' },
        { key: '问题 2', value: '' },
      ],
    })
  })

  it('skips subset-external types with a warning record', () => {
    const result = normalizeSections([
      section('imageGallery'),
      section('ticketGrid'),
      section('videoEmbed'),
      section('not-in-whitelist'),
      section('heroBanner', { title: '保留' }),
    ])
    expect(result.sections.map((s) => s.type)).toEqual(['heroBanner'])
    expect(result.skipped).toEqual(['imageGallery', 'ticketGrid', 'videoEmbed', 'not-in-whitelist'])
  })
})
