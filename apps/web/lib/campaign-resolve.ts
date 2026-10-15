import {
  ApiError,
  type CampaignAssetView,
  type CampaignFaqView,
  type CampaignRuleView,
  type ProductListItem,
  type SessionView,
} from '@awn/api-client'
import type { CampaignSection, CampaignSectionType } from '@awn/campaign-renderer'
import { formatEventDateTime } from './event-labels'

/**
 * 专场模块数据解析层（docs/architecture/campaign-rendering.md「查询统一商品 API」）。
 *
 * 后端存的 props 是**引用型**（eventIds / eventId / ruleDocumentId / faqId / assetIds），
 * 渲染器要的是**展示型**（sessions / tiers / paragraphs / rules）——中间必须过一层解析：
 * 页面按引用查公开只读 API 组装展示数据，查不到就明确标记待接入，
 * 既不渲染空白区块，也不为后端没有的数据源造假内容。
 *
 * 须知 / 常见问题 / 素材三类已有公开内容端点（docs/api/contracts.md「专场列表与内容接口」）：
 * 引用型 ID 按列表条目 id 匹配，匹配不到即视为查不到；内联展示型 props
 * （rules 文本数组 / entries 问答对 / images 含 alt 条目，口径与 @awn/campaign-renderer
 * 和小程序 sections 子集一致）优先于引用，两种形态都能渲染。
 */

export interface SectionResolution {
  id: string
  type: CampaignSectionType
  status: 'ready' | 'pending'
  /** 渲染器可读的展示型 props（status=ready 时有效） */
  props: Record<string, unknown>
  /** 待接入原因（status=pending 时有效） */
  reason?: string
}

/** 解析所需的最小数据接口；由页面注入 api-client 实现，测试注入假数据。 */
export interface CampaignDataApi {
  listEventSessions(eventId: string): Promise<SessionView[]>
  getSessionProducts(sessionId: string): Promise<ProductListItem[]>
  getCampaignRules?(campaignCode: string): Promise<CampaignRuleView[]>
  getCampaignFaq?(campaignCode: string): Promise<CampaignFaqView[]>
  getCampaignAssets?(campaignCode: string): Promise<CampaignAssetView[]>
}

function idsOf(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

function idOf(props: Record<string, unknown>, key: string): string {
  const value = props[key]
  return typeof value === 'string' && value ? value : ''
}

function textsOf(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

/** 富文本模块：服务端 content 为转义后的 HTML 片段，取纯文本再按空行分段。 */
function paragraphsOf(section: CampaignSection): string[] {
  const fromProps = textsOf(section.props, 'paragraphs')
  if (fromProps.length > 0) return fromProps
  return String(section.props['content'] ?? '')
    .replace(/<[^>]*>/g, '')
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0)
}

/** 展示型条目：只保留对象并取字符串字段（渲染层另有兜底，此处只剔除无意义条目）。 */
function itemsOf(props: Record<string, unknown>, key: string): Record<string, unknown>[] {
  const value = props[key]
  if (!Array.isArray(value)) return []
  return value.filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
}

/** 须知模块：内联 rules 文本数组优先；否则按 ruleDocumentId 查内容端点，正文按空行分段。 */
async function resolveRulePanel(
  section: CampaignSection,
  api: CampaignDataApi,
  campaignCode?: string,
): Promise<SectionResolution> {
  const rules = textsOf(section.props, 'rules').filter((rule) => rule.length > 0)
  if (rules.length > 0) {
    return { ...section, status: 'ready', props: { ...section.props, rules } }
  }
  if (section.props['rules'] !== undefined) {
    return pending(section, '须知条目格式不符（rules 需为非空文本数组）')
  }
  const ruleId = idOf(section.props, 'ruleDocumentId')
  if (!ruleId) {
    return pending(section, '须知内容未接入（未配置 rules 或 ruleDocumentId）')
  }
  const documents = await campaignContent(api.getCampaignRules, campaignCode)
  const referenced = documents.find((document) => String(document.id) === ruleId)
  if (!referenced) {
    return pending(section, `须知内容未接入（ruleDocumentId ${ruleId} 无对应条目）`)
  }
  const paragraphs = referenced.content
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0)
  if (paragraphs.length === 0) {
    return pending(section, `须知内容未接入（ruleDocumentId ${ruleId} 条目正文为空）`)
  }
  return { ...section, status: 'ready', props: { ...section.props, rules: paragraphs } }
}

/** 常见问题模块：条目为 {question, answer} 文本对，与 @awn/campaign-renderer 口径一致。 */
async function resolveFaq(
  section: CampaignSection,
  api: CampaignDataApi,
  campaignCode?: string,
): Promise<SectionResolution> {
  const entries = itemsOf(section.props, 'entries')
    .map((entry) => ({
      question: typeof entry['question'] === 'string' ? entry['question'] : '',
      answer: typeof entry['answer'] === 'string' ? entry['answer'] : '',
    }))
    .filter((entry) => entry.question.length > 0 || entry.answer.length > 0)
  if (entries.length > 0) {
    return { ...section, status: 'ready', props: { ...section.props, entries } }
  }
  if (section.props['entries'] !== undefined) {
    return pending(section, '常见问题条目格式不符（entries 需含 question / answer 文本）')
  }
  // 引用型：faqId 单条 / faqIds 多条，按引用顺序取对应条目
  const referenced = [idOf(section.props, 'faqId'), ...idsOf(section.props, 'faqIds')].filter(
    (id) => id.length > 0,
  )
  if (referenced.length === 0) {
    return pending(section, '常见问题内容未接入（未配置 entries 或 faqId）')
  }
  const documents = await campaignContent(api.getCampaignFaq, campaignCode)
  const resolved = referenced
    .map((id) => documents.find((entry) => String(entry.id) === id))
    .filter((entry): entry is CampaignFaqView => entry !== undefined)
    .map((entry) => ({ question: entry.question, answer: entry.answer }))
  if (resolved.length === 0) {
    return pending(section, '常见问题内容未接入（faqId 无对应条目）')
  }
  return { ...section, status: 'ready', props: { ...section.props, entries: resolved } }
}

/** 图片模块：条目为 {src, alt}，src 缺失时渲染层按 alt 出占位块（素材红线）。 */
async function resolveImageGallery(
  section: CampaignSection,
  api: CampaignDataApi,
  campaignCode?: string,
): Promise<SectionResolution> {
  const images = itemsOf(section.props, 'images')
    .map((image) => ({
      src: typeof image['src'] === 'string' ? image['src'] : '',
      alt: typeof image['alt'] === 'string' ? image['alt'] : '',
    }))
    .filter((image) => image.alt.length > 0)
  if (images.length > 0) {
    return { ...section, status: 'ready', props: { ...section.props, images } }
  }
  if (section.props['images'] !== undefined) {
    return pending(section, '图片素材格式不符（images 需为非空数组且含 alt 文本）')
  }
  const assetIds = idsOf(section.props, 'assetIds')
  if (assetIds.length === 0) {
    return pending(section, '图片素材未接入（未配置 images 或 assetIds）')
  }
  const assets = await campaignContent(api.getCampaignAssets, campaignCode)
  const resolved = assetIds
    .map((id) => assets.find((asset) => String(asset.id) === id))
    .filter((asset): asset is CampaignAssetView => asset !== undefined)
    // 无 alt 的素材不出图（素材红线：不用生成图顶替，也不留空图）
    .filter((asset) => (asset.altText ?? '').length > 0)
    .map((asset) => ({ src: asset.url, alt: asset.altText as string }))
  if (resolved.length === 0) {
    return pending(section, '图片素材未接入（assetIds 无对应条目或素材缺 alt）')
  }
  return { ...section, status: 'ready', props: { ...section.props, images: resolved } }
}

/**
 * 内容端点读取：未注入端点或缺专场码一律按查不到处理；404 同样按查不到
 * （引用未必是真实条目 id），网络类异常照常抛出（与引用型活动查询同口径）。
 */
async function campaignContent<T>(
  load: ((campaignCode: string) => Promise<T[]>) | undefined,
  campaignCode: string | undefined,
): Promise<T[]> {
  if (!load || !campaignCode) {
    return []
  }
  try {
    return await load(campaignCode)
  } catch (error) {
    fallback(error)
    return []
  }
}

/** eventList：按引用活动查场次，组装场次卡片数据。 */
async function resolveEventList(section: CampaignSection, api: CampaignDataApi): Promise<SectionResolution> {
  const sessions = await sessionsOf(idsOf(section.props, 'eventIds'), api)
  if (sessions.length === 0) {
    return { ...section, status: 'pending', props: {}, reason: '引用的活动不存在或未公开' }
  }
  return {
    ...section,
    status: 'ready',
    props: {
      ...section.props,
      sessions: sessions.map((session) => ({
        name: session.name,
        startAt: formatEventDateTime(session.startTime),
      })),
    },
  }
}

/** ticketGrid：按引用活动逐场次查票档，价格沿用后端「分」口径。 */
async function resolveTicketGrid(section: CampaignSection, api: CampaignDataApi): Promise<SectionResolution> {
  const tiers = await productTiersOf(idsOf(section.props, 'eventIds'), api)
  if (tiers.length === 0) {
    return { ...section, status: 'pending', props: {}, reason: '引用的活动暂无已发布票档' }
  }
  return {
    ...section,
    status: 'ready',
    props: { ...section.props, tiers },
  }
}

async function sessionsOf(eventIds: string[], api: CampaignDataApi): Promise<SessionView[]> {
  const sessions = await Promise.all(
    eventIds.map((eventId) => api.listEventSessions(eventId).catch((error) => fallback(error))),
  )
  return sessions.flat()
}

async function productTiersOf(eventIds: string[], api: CampaignDataApi): Promise<{ name: string; price: number }[]> {
  const sessions = await sessionsOf(eventIds, api)
  const tiers = await Promise.all(
    sessions.map((session) => api.getSessionProducts(session.id).catch((error) => fallback(error))),
  )
  return tiers.flat().map((product) => ({ name: product.name, price: product.unitPrice }))
}

function fallback(error: unknown): never[] {
  // 引用型 ID 未必是真实活动 ID（模板占位或缺失），视为查不到，不中断整页渲染。
  if (error instanceof ApiError) return []
  throw error
}

/**
 * 按 section 类型解析为展示型 props；无公开数据源的模块明确标记待接入。
 * campaignCode 用于查询须知 / 常见问题 / 素材内容端点（引用型 ID 按条目 id 匹配）。
 */
export async function resolveSections(
  sections: CampaignSection[],
  api: CampaignDataApi,
  campaignCode?: string,
): Promise<SectionResolution[]> {
  return Promise.all(sections.map((section) => resolveOne(section, api, campaignCode)))
}

async function resolveOne(
  section: CampaignSection,
  api: CampaignDataApi,
  campaignCode?: string,
): Promise<SectionResolution> {
  switch (section.type) {
    case 'eventList':
      return resolveEventList(section, api)
    case 'ticketGrid':
      return resolveTicketGrid(section, api)
    case 'richText':
      return { ...section, status: 'ready', props: { ...section.props, paragraphs: paragraphsOf(section) } }
    case 'countdown':
      return { ...section, status: 'ready', props: { ...section.props, until: idOf(section.props, 'endTime') } }
    case 'heroBanner':
    case 'noticeBar':
    case 'videoEmbed':
      return { ...section, status: 'ready', props: section.props }
    case 'rulePanel':
      return resolveRulePanel(section, api, campaignCode)
    case 'faq':
      return resolveFaq(section, api, campaignCode)
    case 'imageGallery':
      return resolveImageGallery(section, api, campaignCode)
    case 'priceTrend':
      return pending(section, '价格趋势未接入（情报接口未接入专场）')
    case 'ticketComparison':
      return pending(section, '票档对比未接入（缺少可对比数据源）')
    case 'channelComparison':
      return pending(section, '渠道对比未接入（缺少渠道数据源）')
    default:
      return pending(section, '该模块类型暂不支持')
  }
}

function pending(section: CampaignSection, reason: string): SectionResolution {
  return { ...section, status: 'pending', props: {}, reason }
}

/** 待接入模块的中文标签（页面展示用，不发明后端没有的内容）。 */
export const PENDING_SECTION_LABELS: Record<string, string> = {
  rulePanel: '购票须知',
  faq: '常见问题',
  imageGallery: '图片',
  videoEmbed: '视频',
  priceTrend: '价格趋势',
  ticketComparison: '票档对比',
  channelComparison: '渠道对比',
}
