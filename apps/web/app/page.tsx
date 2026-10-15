'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { listCampaigns, listEvents, type CampaignView, type EventView } from '@awn/api-client'
import { CampaignList } from '../components/CampaignList'
import { eventStatusLabel, formatEventDateTime } from '../lib/event-labels'

/**
 * 发现列表 /（原型 web/home.html）：已发布与已结束活动，按开始时间倒序。
 * 封面为几何占位（素材红线：不使用图像素材）；城市 / 距离排序待后端地理字段落地。
 * 专场区（GET /api/v1/campaigns，只含 PUBLISHED）：运营配置的活动聚合页，
 * 与活动目录互相独立——加载互不阻塞，专场失败只降级专场区不影响活动列表。
 */
export default function HomePage() {
  const [events, setEvents] = useState<EventView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [campaigns, setCampaigns] = useState<CampaignView[]>([])
  const [campaignsFailed, setCampaignsFailed] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    listEvents()
      .then((data) => {
        if (!alive) return
        setEvents(data)
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

  useEffect(() => {
    let alive = true
    listCampaigns()
      .then((data) => {
        if (!alive) return
        setCampaigns(data)
        setCampaignsFailed(false)
      })
      .catch(() => {
        if (alive) setCampaignsFailed(true)
      })
    return () => {
      alive = false
    }
  }, [])

  return (
    <main className="page">
      <header className="section-head">
        <h1>发现</h1>
        <p className="muted">正在售票与近期结束的活动。</p>
      </header>

      {!campaignsFailed && campaigns.length > 0 && (
        <section aria-labelledby="home-campaigns-title">
          <div className="section-head">
            <h2 id="home-campaigns-title">专场</h2>
            <p className="muted">运营配置的活动聚合页。</p>
          </div>
          <CampaignList campaigns={campaigns} />
        </section>
      )}
      {campaignsFailed && (
        <p className="state">专场列表加载失败，可稍后刷新重试。</p>
      )}

      {loading && <p className="state">加载中…</p>}
      {failed && (
        <p className="state state--error">
          活动数据加载失败，请确认接口已启动后重试。
        </p>
      )}
      {!loading && !failed && events.length === 0 && (
        <p className="state">暂无可浏览的活动，稍后再来看看。</p>
      )}

      {!loading && !failed && events.length > 0 && (
        <ul className="card-grid">
          {events.map((event) => (
            <li key={event.id}>
              <Link className="event-card" href={`/events/${event.id}`}>
                <div className="event-card__cover" aria-hidden>
                  <span>示例封面 · 几何装饰</span>
                </div>
                <div className="event-card__body">
                  <div className="row-between">
                    <strong>{event.name}</strong>
                    <span className="badge">{eventStatusLabel(event.status)}</span>
                  </div>
                  {event.startTime && (
                    <span className="muted num">
                      {formatEventDateTime(event.startTime)} 开始
                    </span>
                  )}
                  <span className="muted">查看详情 →</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
