'use client'

import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import {
  ApiError,
  getSeatmap,
  getSession,
  getSessionProducts,
  type ProductListItem,
  type SeatMapItem,
  type SessionView,
} from '@awn/api-client'
import { TierCard, type TicketTier } from '@awn/ticket-components'
import { SeatMap } from '../../../components/SeatMap'
import { GeoRow } from '../../../components/GeoRow'
import { formatEventDateTime, productToTier } from '../../../lib/event-labels'
import { groupSeatSections, seatProductCodes, seatsOfProduct } from '../../../lib/seatmap'

/**
 * 场次票档页 /sessions/{id}（原型 web/session-*.html 票档区）。
 * 票档渲染真实商品与可售状态（GET /api/v1/sessions/{sessionId}/products，
 * available 为缓存展示口径，下单时后端原子重校验）；
 * 票档序号按价格降序（1 = 最高价档），与原型 tier-dot-1..5 一致。
 * 座位型商品（座位图载荷中出现过的商品编码）附座位图选座区
 * （GET /api/v1/sessions/{sessionId}/seatmap）；座位订单链路后端未开放，
 * 选座只收集不提交，不渲染假下单入口。
 * 数量选择在结算页选（/checkout?session={id}），本页只读展示。
 */
export default function SessionProductsPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const sessionId = params.id

  const [session, setSession] = useState<SessionView | null>(null)
  const [products, setProducts] = useState<ProductListItem[]>([])
  const [seats, setSeats] = useState<SeatMapItem[]>([])
  const [selected, setSelected] = useState<Record<string, string[]>>({})
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
    // 座位图为附加展示：加载失败不影响票档页（无座位型商品即不渲染选座区）
    getSeatmap(sessionId)
      .then((seatmap) => {
        if (alive) setSeats(seatmap)
      })
      .catch(() => {
        if (alive) setSeats([])
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

  /** 座位型商品：座位图载荷中出现过的商品编码（后端无显式标记，不发明字段） */
  const seatCodes = useMemo(() => seatProductCodes(seats), [seats])
  const seatProducts = useMemo(
    () => products.filter((product) => seatCodes.has(product.productCode)),
    [products, seatCodes],
  )

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

  const toggleSeat = (productCode: string, seatNo: string) => {
    setSelected((prev) => {
      const current = prev[productCode] ?? []
      return {
        ...prev,
        [productCode]: current.includes(seatNo)
          ? current.filter((no) => no !== seatNo)
          : [...current, seatNo],
      }
    })
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
        <GeoRow geo={session.geo} />
      </header>

      <section className="detail-section">
        <h2>票档</h2>
        {tiers.length === 0 && <p className="muted">该场次暂未开放票档。</p>}
        <div className="tier-list">
          {tiers.map((tier, index) => (
            <TierCard key={tier.productId} tier={tier} tierLevel={(index % 5) + 1} />
          ))}
        </div>
        {seatProducts.length > 0 && (
          <p className="muted">
            其中{seatProducts.length === 1 ? '该档为选座票，' : `${seatProducts.length} 档为选座票，`}
            到下方座位图选座。
          </p>
        )}
        <p className="muted">可售数量为缓存展示，实际以下单结果为准。</p>
        <div className="cols-2">
          <span className="muted">选票档与数量在结算页完成。</span>
          <button
            type="button"
            className="btn"
            disabled={tiers.length === 0}
            onClick={() => router.push(`/checkout?session=${encodeURIComponent(sessionId)}`)}
          >
            去选票
          </button>
        </div>
      </section>

      {seatProducts.map((product) => (
        <section
          key={product.productCode}
          id={`seats-${product.productCode}`}
          className="detail-section"
        >
          <h2>{product.name} · 选座</h2>
          <SeatMap
            sections={groupSeatSections(seatsOfProduct(seats, product.productCode))}
            selected={selected[product.productCode] ?? []}
            onToggleSeat={(seatNo) => toggleSeat(product.productCode, seatNo)}
          />
          <p className="muted">
            已选 {(selected[product.productCode] ?? []).length} 座
            {selected[product.productCode]?.length ? `：${selected[product.productCode].join('、')}` : ''}。
            座位图上「可选」仅为数据标记；座位订单链路后端未开放，选座暂不参与下单。
          </p>
        </section>
      ))}
    </main>
  )
}
