import { describe, expect, it } from 'vitest'
import { resolveCampaignContent } from './campaign-content'
import type { RenderedSection } from './mp-api'

function section(type: string, props: Record<string, unknown>): RenderedSection {
  return { id: type, type, props, content: null }
}

const RULES = [{ id: 7, title: '入场须知', content: '凭电子票入场\n\n一米二以下儿童免票' }]
const FAQ = [{ id: 11, question: '如何取票？', answer: '开演前 1 小时取票' }]

describe('引用型内容解析', () => {
  it('resolves rulePanel ruleDocumentId into inline paragraphs', () => {
    const [resolved] = resolveCampaignContent(
      [section('rulePanel', { ruleDocumentId: '7' })],
      { rules: RULES, faq: FAQ },
    )

    expect(resolved.props.rules).toEqual(['凭电子票入场', '一米二以下儿童免票'])
  })

  it('keeps inline props untouched even when content exists', () => {
    const [resolved] = resolveCampaignContent(
      [section('rulePanel', { rules: ['内联须知'] }), section('faq', { entries: [{ question: '内联问', answer: '内联答' }] })],
      { rules: RULES, faq: FAQ },
    )

    expect(resolved.props.rules).toEqual(['内联须知'])
  })

  it('resolves faq faqId and faqIds in reference order', () => {
    const faq = [
      { id: 1, question: '问一', answer: '答一' },
      { id: 2, question: '问二', answer: '答二' },
    ]
    const [resolved] = resolveCampaignContent(
      [section('faq', { faqIds: [2, 1] })],
      { rules: [], faq },
    )

    expect(resolved.props.entries).toEqual([
      { question: '问二', answer: '答二' },
      { question: '问一', answer: '答一' },
    ])
  })

  it('leaves sections unchanged when ids do not match', () => {
    const [rule, faq] = resolveCampaignContent(
      [section('rulePanel', { ruleDocumentId: '999' }), section('faq', { faqId: '888' })],
      { rules: RULES, faq: FAQ },
    )

    expect(rule.props.rules).toBeUndefined()
    expect(faq.props.entries).toBeUndefined()
  })

  it('leaves sections without references untouched', () => {
    const [resolved] = resolveCampaignContent([section('heroBanner', { title: 'F1' })], {
      rules: RULES,
      faq: FAQ,
    })

    expect(resolved.props).toEqual({ title: 'F1' })
  })
})