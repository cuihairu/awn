'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { listCampaigns, type CampaignView } from '@awn/api-client'

/**
 * 专场入口 /campaigns（docs/api/contracts.md「专场列表与内容接口」）。
 * 列表只含已发布专场（后端过滤，草稿与已撤回不出现）；专场由运营在后台配置并发布，
 * 没有已发布专场时说明发布入口，不造假示例专场。
 */
export default function CampaignsIndexPage() {
  const [campaigns, setCampaigns] = useState<CampaignView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    listCampaigns()
      .then((data) => {
        if (!alive) return
        setCampaigns(data)
        setFailed(false)
      })
      .catch(() => {
        if (alive) setFailed(true)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [])

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">发现</Link>
        <span aria-hidden>/</span>
        <span>专场</span>
      </nav>

      <header className="section-head">
        <h1>专场</h1>
        <p className="muted">已发布的专场，按最近变更排序。</p>
      </header>

      {loading && <p className="state">加载中…</p>}
      {failed && (
        <p className="state state--error">
          专场列表加载失败，请确认接口已启动后重试。
        </p>
      )}
      {!loading && !failed && campaigns.length === 0 && (
        <p className="state">暂无已发布专场。专场由运营在后台配置并发布，发布后在此列出。</p>
      )}

      {campaigns.length > 0 && (
        <ul className="card-grid">
          {campaigns.map((campaign) => (
            <li key={campaign.campaignCode}>
              <Link
                className="event-card"
                href={`/campaigns/${encodeURIComponent(campaign.campaignCode)}`}
              >
                <div className="event-card__cover" aria-hidden>
                  <span>专场封面 · 几何占位</span>
                </div>
                <div className="event-card__body">
                  <strong>{campaign.title}</strong>
                  {campaign.subtitle && <span className="muted">{campaign.subtitle}</span>}
                  <span className="tag tag--quiet">{campaign.campaignCode}</span>
                  <span className="muted">查看专场 →</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
