'use client'

import type { ReactNode } from 'react'
import type { CampaignConfig, CampaignSection, CampaignSectionType } from './index'

/**
 * 专场页面 React 渲染器（对应原型 web/activity-*.html 的模板区块）。
 *
 * 边界约束与 validateCampaignConfig 一致：
 * - 只渲染白名单类型，未命中一律拒绝（渲染 null + 控制台告警，不注入任意内容）；
 * - props 为受控键值对，只渲染文本与结构，不执行任何 HTML / JS（不用 dangerouslySetInnerHTML）；
 * - 渲染层不读库存、不改价格——可售状态由 ticket-components 的票档卡展示，下单后端重校验。
 */

function textOf(props: Record<string, unknown>, key: string): string {
  const value = props[key]
  return typeof value === 'string' ? value : ''
}

function listTexts(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

function itemsOf(props: Record<string, unknown>, key: string): Record<string, unknown>[] {
  const value = props[key]
  return Array.isArray(value)
    ? value.filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null)
    : []
}

function HeroBanner({ section }: { section: CampaignSection }) {
  return (
    <section className="hero" data-section={section.type}>
      <h1 className="title">{textOf(section.props, 'title')}</h1>
      <p className="subtitle">{textOf(section.props, 'subtitle')}</p>
    </section>
  )
}

function RichText({ section }: { section: CampaignSection }) {
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title')}</h2>
      {listTexts(section.props, 'paragraphs').map((paragraph, i) => (
        <p key={i}>{paragraph}</p>
      ))}
    </section>
  )
}

function ImageGallery({ section }: { section: CampaignSection }) {
  // 素材红线：只渲染 alt 文本占位块，图片 URL 为空时不输出 img
  const items = itemsOf(section.props, 'images')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title')}</h2>
      <div className="card-grid">
        {items.map((item, i) => {
          const alt = typeof item.alt === 'string' ? item.alt : ''
          const src = typeof item.src === 'string' ? item.src : ''
          return (
            <figure key={i} className="card">
              {src ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={src} alt={alt} />
              ) : (
                <div className="state" role="img" aria-label={alt}>
                  {alt || '未收录图片'}
                </div>
              )}
            </figure>
          )
        })}
      </div>
    </section>
  )
}

function VideoEmbed({ section }: { section: CampaignSection }) {
  // 渲染层只留占位框；真实嵌入由接入方以受控 URL 决定，不渲染任意 iframe
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title')}</h2>
      <div className="sunken state" role="img" aria-label="视频占位">
        {textOf(section.props, 'description') || '视频占位'}
      </div>
    </section>
  )
}

function EventList({ section }: { section: CampaignSection }) {
  const items = itemsOf(section.props, 'sessions')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '选择场次'}</h2>
      <div className="card-grid">
        {items.map((item, i) => (
          <article key={i} className="card session-card">
            <strong>{typeof item.name === 'string' ? item.name : ''}</strong>
            <div className="muted" style={{ fontSize: 'var(--fs-sm)' }}>
              {typeof item.venue === 'string' ? item.venue : ''}
            </div>
            {typeof item.startAt === 'string' && (
              <span className="num">{item.startAt}</span>
            )}
          </article>
        ))}
      </div>
    </section>
  )
}

function TicketGrid({ section }: { section: CampaignSection }) {
  // 票档数据经 ticket-components 的 TierCard 渲染，此处只做占位结构展示
  const items = itemsOf(section.props, 'tiers')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '票档'}</h2>
      {items.map((item, i) => (
        <div key={i} className="tier-row">
          <div className="row" style={{ gap: 12 }}>
            <span className="tier-dot tier-dot-1" aria-hidden />
            <div>
              <strong>{typeof item.name === 'string' ? item.name : ''}</strong>
            </div>
          </div>
          {typeof item.price === 'number' && (
            <span className="price price-yuan num">{(item.price / 100).toFixed(2)}</span>
          )}
        </div>
      ))}
    </section>
  )
}

function TicketComparison({ section }: { section: CampaignSection }) {
  const items = itemsOf(section.props, 'rows')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '票档对比'}</h2>
      {items.map((item, i) => (
        <div key={i} className="tier-row">
          <strong>{typeof item.name === 'string' ? item.name : ''}</strong>
          <span className="muted">{typeof item.note === 'string' ? item.note : ''}</span>
        </div>
      ))}
    </section>
  )
}

function PriceTrend({ section }: { section: CampaignSection }) {
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '价格趋势'}</h2>
      <p className="muted">{textOf(section.props, 'summary')}</p>
    </section>
  )
}

function ChannelComparison({ section }: { section: CampaignSection }) {
  const items = itemsOf(section.props, 'channels')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '渠道对比'}</h2>
      {items.map((item, i) => (
        <div key={i} className="entry">
          <span className="entry__title">{typeof item.name === 'string' ? item.name : ''}</span>
          <span className="entry__meta">{typeof item.note === 'string' ? item.note : ''}</span>
        </div>
      ))}
    </section>
  )
}

function Countdown({ section }: { section: CampaignSection }) {
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '倒计时'}</h2>
      <p className="num">{textOf(section.props, 'until')}</p>
    </section>
  )
}

function RulePanel({ section }: { section: CampaignSection }) {
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '购票须知'}</h2>
      <ul className="entry-list">
        {listTexts(section.props, 'rules').map((rule, i) => (
          <li key={i} className="entry">
            {rule}
          </li>
        ))}
      </ul>
    </section>
  )
}

function Faq({ section }: { section: CampaignSection }) {
  const items = itemsOf(section.props, 'entries')
  return (
    <section className="detail-section" data-section={section.type}>
      <h2>{textOf(section.props, 'title') || '常见问题'}</h2>
      {items.map((item, i) => (
        <div key={i} className="group">
          <h3>{typeof item.question === 'string' ? item.question : ''}</h3>
          <p>{typeof item.answer === 'string' ? item.answer : ''}</p>
        </div>
      ))}
    </section>
  )
}

function NoticeBar({ section }: { section: CampaignSection }) {
  return (
    <section className="notice-bar" data-section={section.type} role="status">
      {textOf(section.props, 'message')}
    </section>
  )
}

const SECTION_COMPONENTS: Record<
  CampaignSectionType,
  (props: { section: CampaignSection }) => ReactNode
> = {
  heroBanner: HeroBanner,
  richText: RichText,
  imageGallery: ImageGallery,
  videoEmbed: VideoEmbed,
  eventList: EventList,
  ticketGrid: TicketGrid,
  ticketComparison: TicketComparison,
  priceTrend: PriceTrend,
  channelComparison: ChannelComparison,
  countdown: Countdown,
  rulePanel: RulePanel,
  faq: Faq,
  noticeBar: NoticeBar,
}

export interface CampaignRendererProps {
  config: CampaignConfig
}

export function CampaignRenderer({ config }: CampaignRendererProps) {
  return (
    <>
      {config.sections.map((section) => {
        const Component = SECTION_COMPONENTS[section.type]
        if (!Component) {
          console.warn(`campaign-renderer: unknown section type ${section.type}`)
          return null
        }
        return <Component key={section.id} section={section} />
      })}
    </>
  )
}
