import { ApiError, type ProductListItem, type SessionView } from '@awn/api-client'
import type { CampaignSection, CampaignSectionType } from '@awn/campaign-renderer'
import { formatEventDateTime } from './event-labels'

/**
 * 专场模块数据解析层（docs/architecture/campaign-rendering.md「查询统一商品 API」）。
 *
 * 后端存的 props 是**引用型**（eventIds / eventId / ruleDocumentId / faqId / assetIds），
 * 渲染器要的是**展示型**（sessions / tiers / paragraphs / rules）——中间必须过一层解析：
 * 页面按引用查公开只读 API 组装展示数据，查不到就明确标记待接入，
 * 既不渲染空白区块，也不为后端没有的数据源造假内容。
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

/** 按 section 类型解析为展示型 props；无公开数据源的模块明确标记待接入。 */
export async function resolveSections(
  sections: CampaignSection[],
  api: CampaignDataApi,
): Promise<SectionResolution[]> {
  return Promise.all(sections.map((section) => resolveOne(section, api)))
}

async function resolveOne(
  section: CampaignSection,
  api: CampaignDataApi,
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
      return pending(section, '须知内容未接入（规则文档接口未落地）')
    case 'faq':
      return pending(section, '常见问题内容未接入（FAQ 接口未落地）')
    case 'imageGallery':
      return pending(section, '图片素材未接入（素材公开接口未落地）')
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
