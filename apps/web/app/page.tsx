'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { listEvents, type EventView } from '@awn/api-client'
import { eventStatusLabel, formatEventDateTime } from '../lib/event-labels'

/**
 * 发现列表 /（原型 web/home.html）：已发布与已结束活动，按开始时间倒序。
 * 封面为几何占位（素材红线：不使用图像素材）；城市 / 距离排序待后端地理字段落地。
 */
export default function HomePage() {
  const [events, setEvents] = useState<EventView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

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

  return (
    <main className="page">
      <header className="section-head">
        <h1>发现</h1>
        <p className="muted">正在售票与近期结束的活动。</p>
      </header>

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
