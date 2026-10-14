'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import {
  ApiError,
  getEvent,
  listEventSessions,
  type EventView,
  type SessionView,
} from '@awn/api-client'
import { eventStatusLabel, formatEventDateTime } from '../../../lib/event-labels'

/**
 * 活动页 /events/{id}（原型 web/activity-*.html 模板区块①Hero + ③场次列表）。
 * 模板其余区块（演出信息 / 票档 / 须知）与后台可配置字段待专场发布版本接入；
 * 本场次列表已接真实 API（GET /api/v1/events/{eventId}/sessions，按开始时间升序）。
 * 未发布或不存在一律 404，不泄露草稿。
 */
export default function EventDetailPage() {
  const params = useParams<{ id: string }>()
  const eventId = params.id

  const [event, setEvent] = useState<EventView | null>(null)
  const [sessions, setSessions] = useState<SessionView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    Promise.all([getEvent(eventId), listEventSessions(eventId)])
      .then(([data, sessionList]) => {
        if (!alive) return
        setEvent(data)
        setSessions(sessionList)
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
  }, [eventId])

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
        </nav>
        <p className="state state--error">
          活动不存在或未发布。<Link href="/">返回发现列表</Link>
        </p>
      </main>
    )
  }

  if (failed || !event) {
    return (
      <main className="page">
        <p className="state state--error">活动数据加载失败，请稍后重试。</p>
      </main>
    )
  }

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">发现</Link>
        <span aria-hidden>/</span>
        <span>{event.name}</span>
      </nav>

      <header className="page-head">
        <div className="row-between">
          <h1>{event.name}</h1>
          <span className="badge">{eventStatusLabel(event.status)}</span>
        </div>
        <p className="muted num">
          {formatEventDateTime(event.startTime)} 至 {formatEventDateTime(event.endTime)}
        </p>
      </header>

      <section className="detail-section">
        <h2>选择场次</h2>
        {sessions.length === 0 && (
          <p className="muted">该活动暂未开放场次。</p>
        )}
        <div className="card-grid">
          {sessions.map((session) => (
            <Link
              key={session.id}
              className="session-card"
              href={`/sessions/${session.id}`}
            >
              <div className="row-between">
                <strong>{session.name}</strong>
                <span className="badge">选择场次</span>
              </div>
              <span className="muted num">{formatEventDateTime(session.startTime)}</span>
              <span className="muted">查看票档 →</span>
            </Link>
          ))}
        </div>
      </section>
    </main>
  )
}
