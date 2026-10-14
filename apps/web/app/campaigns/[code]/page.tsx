'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { CampaignRenderer } from '@awn/campaign-renderer'
import { ApiError, renderCampaign, type RenderedPageView } from '@awn/api-client'
import { toCampaignConfig } from '../../../lib/campaign-config'

/**
 * 专场页 /campaigns/{code}（原型 web/activity-*.html 模板 + docs/architecture/campaign-rendering.md）。
 * 只读已发布版本快照（GET /api/v1/campaigns/{code}/render）：
 * 未发布 / 未知专场一律 404，草稿改动不影响线上页面。
 * 模块类型白名单与渲染由 @awn/campaign-renderer 负责，本页只做载荷适配与状态呈现。
 */
const EMPTY_SKIPPED: { id: string; type: string }[] = []

export default function CampaignPage() {
  const params = useParams<{ code: string }>()
  const campaignCode = params.code

  const [page_, setPage] = useState<RenderedPageView | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    renderCampaign(campaignCode)
      .then((data) => {
        if (alive) setPage(data)
      })
      .catch((err) => {
        if (!alive) return
        if (err instanceof ApiError && err.status === 404) {
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

  const adapted = useMemo(() => (page_ ? toCampaignConfig(page_) : null), [page_])
  const config = adapted?.config ?? null
  const skipped = adapted?.skipped ?? EMPTY_SKIPPED

  useEffect(() => {
    if (page_ && skipped && skipped.length > 0) {
      console.warn(
        `campaign ${campaignCode}: dropped ${skipped.length} section(s) outside the whitelist`,
      )
    }
  }, [campaignCode, page_, skipped])

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
    </main>
  )
}