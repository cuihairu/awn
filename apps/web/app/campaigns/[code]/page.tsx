'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { CampaignRenderer, type CampaignConfig } from '@awn/campaign-renderer'
import {
  ApiError,
  getSessionProducts,
  listEventSessions,
  renderCampaign,
} from '@awn/api-client'
import { PENDING_SECTION_LABELS, resolveSections } from '../../../lib/campaign-resolve'
import { toCampaignConfig } from '../../../lib/campaign-config'

/**
 * 专场页 /campaigns/{code}（原型 web/activity-*.html 模板 + docs/architecture/campaign-rendering.md）。
 *
 * 只读已发布版本快照（GET /api/v1/campaigns/{code}/render）：未发布 / 未知专场一律 404，
 * 草稿改动不影响线上页面。渲染分两步：
 * 1. toCampaignConfig —— 载荷适配 + 白名单过滤（渲染器自身还有一层拒绝渲染）；
 * 2. resolveSections —— 把后端的引用型 props（eventIds / ruleDocumentId / faqId …）
 *    解析成渲染器要读的展示型 props（sessions / tiers / paragraphs …）；
 *    无公开数据源的模块标记待接入并显式说明，不渲染空白区块也不造假内容。
 */
const CAMPAIGN_API = {
  listEventSessions,
  getSessionProducts,
}

interface PendingSection {
  id: string
  label: string
  reason: string
}

export default function CampaignPage() {
  const params = useParams<{ code: string }>()
  const campaignCode = params.code

  const [config, setConfig] = useState<CampaignConfig | null>(null)
  const [pending, setPending] = useState<PendingSection[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    renderCampaign(campaignCode)
      .then(async (page) => {
        const adapted = toCampaignConfig(page)
        if (alive && adapted.skipped.length > 0) {
          console.warn(
            `campaign ${campaignCode}: dropped ${adapted.skipped.length} section(s) outside the whitelist`,
          )
        }
        const resolutions = await resolveSections(adapted.config.sections, CAMPAIGN_API)
        if (!alive) return
        setConfig({
          ...adapted.config,
          sections: resolutions.filter((section) => section.status === 'ready'),
        })
        setPending(
          resolutions
            .filter((section) => section.status === 'pending')
            .map((section) => ({
              id: section.id,
              label: PENDING_SECTION_LABELS[section.type] ?? section.type,
              reason: section.reason ?? '内容待接入',
            })),
        )
      })
      .catch((error) => {
        if (!alive) return
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true)
        } else {
          setFailed(true)
        }
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [campaignCode])

  useEffect(() => {
    if (config) {
      document.title = config.seo.title
    }
  }, [config])

  if (loading) {
    return (
      <main className="page">
        <p className="state">加载中…</p>
      </main>
    )
  }

  if (notFound) {
    return (
      <main className="page">
        <nav className="crumbs">
          <Link href="/">发现</Link>
          <span aria-hidden>/</span>
          <span>专场</span>
        </nav>
        <p className="state state--error">
          专场不存在或未发布。<Link href="/">返回首页</Link>
        </p>
      </main>
    )
  }

  if (failed || !config) {
    return (
      <main className="page">
        <p className="state state--error">专场加载失败，请稍后重试。</p>
      </main>
    )
  }

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">发现</Link>
        <span aria-hidden>/</span>
        <span>专场</span>
      </nav>

      {config.sections.length === 0 && (
        <p className="state">该专场尚未配置已发布内容。</p>
      )}

      <CampaignRenderer config={config} />

      {pending.length > 0 && (
        <section className="detail-section" data-section="pending">
          <h2>以下内容待接入</h2>
          <ul className="entry-list">
            {pending.map((section) => (
              <li key={section.id} className="entry">
                <span className="entry__title">{section.label}</span>
                <span className="entry__meta muted">{section.reason}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}
