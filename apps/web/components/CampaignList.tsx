'use client'

import Link from 'next/link'
import type { CampaignView } from '@awn/api-client'

/**
 * 首页专场列表（原型 web/home.html 的活动卡片 + GET /api/v1/campaigns）。
 * 数据源只含 PUBLISHED 专场；卡片目的地是 /campaigns/{code}。
 * 封面为文字占位（素材红线：不生成图片代餐，真实素材待运营提供后替换）。
 */
interface CampaignListProps {
  campaigns: CampaignView[]
}

export function CampaignList({ campaigns }: CampaignListProps) {
  if (campaigns.length === 0) {
    return null
  }

  return (
    <ul className="card-grid">
      {campaigns.map((campaign) => (
        <li key={campaign.campaignCode}>
          <Link className="event-card" href={`/campaigns/${campaign.campaignCode}`}>
            <div className="event-card__cover" aria-hidden>
              <span>专场 · 几何装饰</span>
            </div>
            <div className="event-card__body">
              <strong>{campaign.title}</strong>
              {campaign.subtitle && (
                <span className="muted">{campaign.subtitle}</span>
              )}
              <span className="muted">查看详情 →</span>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}
