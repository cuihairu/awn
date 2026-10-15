import type { CampaignFaqView, CampaignRuleView, RenderedSection } from './mp-api'

/**
 * 小程序端引用型内容解析（docs/api/contracts.md「专场列表与内容接口」）。
 *
 * 后端 props 有两种形态：内联展示型（rules 文本数组 / entries 问答对）与引用型
 * （ruleDocumentId / faqId / faqIds）。小程序按编码访问专场，引用型没有可查端点时会渲染空区块，
 * 因此这里按内容端点的条目 id 解析成内联形态再交给 normalizeSection：
 * 内联优先，匹配不到就保持原样（sections 层照旧渲染空条目，不造假内容）。
 *
 * 纯函数：不依赖 transport，页面负责取内容后传入。
 */
export interface CampaignContent {
  rules: CampaignRuleView[]
  faq: CampaignFaqView[]
}

function texts(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

function idOf(props: Record<string, unknown>, key: string): string {
  const value = props[key]
  return typeof value === 'string' ? value : ''
}

function idsOf(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

/** 须知正文按空行分段（与后端口径一致：纯文本，段落以空行分隔） */
function paragraphsOf(content: string): string[] {
  return content
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0)
}

/**
 * 按引用解析 rulePanel / faq 两个模块的 props：
 * 内联形态原样透传，引用型按 id 命中后转成内联；无引用或全部未命中则不改 props。
 */
export function resolveCampaignContent(
  sections: RenderedSection[],
  content: CampaignContent,
): RenderedSection[] {
  return (sections ?? []).map((section) => {
    if (section.type === 'rulePanel') {
      if (texts(section.props, 'rules').length > 0) return section
      const ruleId = idOf(section.props, 'ruleDocumentId')
      const rule = content.rules.find((entry) => String(entry.id) === ruleId)
      const paragraphs = rule ? paragraphsOf(rule.content) : []
      if (paragraphs.length === 0) return section
      return { ...section, props: { ...section.props, rules: paragraphs } }
    }
    if (section.type === 'faq') {
      const inline = section.props['entries']
      if (Array.isArray(inline) && inline.length > 0) return section
      const referenced = [idOf(section.props, 'faqId'), ...idsOf(section.props, 'faqIds')].filter(
        (id) => id.length > 0,
      )
      const entries = referenced
        .map((id) => content.faq.find((entry) => String(entry.id) === id))
        .filter((entry): entry is CampaignFaqView => entry !== undefined)
        .map((entry) => ({ question: entry.question, answer: entry.answer }))
      if (entries.length === 0) return section
      return { ...section, props: { ...section.props, entries } }
    }
    return section
  })
}