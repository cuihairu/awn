'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import {
  ApiError,
  getSession,
  getSessionProducts,
  type ProductListItem,
  type SessionView,
} from '@awn/api-client'
import { TierCard, type TicketTier } from '@awn/ticket-components'
import { formatEventDateTime, productToTier } from '../../../lib/event-labels'

/**
 * 场次票档页 /sessions/{id}（原型 web/session-*.html 票档区）。
 * 票档渲染真实商品与可售状态（GET /api/v1/sessions/{sessionId}/products，
 * available 为缓存展示口径，下单时后端原子重校验）；
 * 票档序号按价格降序（1 = 最高价档），与原型 tier-dot-1..5 一致。
 * 下单链路由后续购买流程接入，本页暂只读。
 */
export default function SessionProductsPage() {
  const params = useParams<{ id: string }>()
  const sessionId = params.id

  const [session, setSession] = useState<SessionView | null>(null)
  const [products, setProducts] = useState<ProductListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    Promise.all([getSession(sessionId), getSessionProducts(sessionId)])
      .then(([sessionData, productList]) => {
        if (!alive) return
        setSession(sessionData)
        setProducts(productList)
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
  }, [sessionId])

  /** 价格降序 → tier-dot 序号（1 = 最高价档，最多 5 档样式） */
  const tiers = useMemo<TicketTier[]>(() => {
    const sorted = [...products].sort((a, b) => b.unitPrice - a.unitPrice)
    return sorted.map(productToTier)
  }, [products])

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
        <p className="state state--error">
          场次不存在或未开放。<Link href="/">返回发现列表</Link>
        </p>
      </main>
    )
  }

  if (failed || !session) {
    return (
      <main className="page">
        <p className="state state--error">票档数据加载失败，请稍后重试。</p>
      </main>
    )
  }

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">发现</Link>
        <span aria-hidden>/</span>
        {session.eventId ? (
          <Link href={`/events/${session.eventId}`}>活动详情</Link>
        ) : (
          <span>活动详情</span>
        )}
        <span aria-hidden>/</span>
        <span>{session.name}</span>
      </nav>

      <header className="page-head">
        <h1>{session.name}</h1>
        {session.startTime && (
          <p className="muted num">{formatEventDateTime(session.startTime)} 开演</p>
        )}
      </header>

      <section className="detail-section">
        <h2>票档</h2>
        {tiers.length === 0 && <p className="muted">该场次暂未开放票档。</p>}
        <div className="tier-list">
          {tiers.map((tier, index) => (
            <TierCard
              key={tier.productId}
              tier={tier}
              tierLevel={(index % 5) + 1}
            />
          ))}
        </div>
        <p className="muted">可售数量为缓存展示，实际以下单结果为准。</p>
      </section>
    </main>
  )
}
