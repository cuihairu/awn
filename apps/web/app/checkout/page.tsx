'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useMemo, useState } from 'react'
import {
  ApiError,
  createOrder,
  getSession,
  getSessionProducts,
  quoteCheckout,
  type ProductListItem,
  type QuoteView,
  type SessionView,
} from '@awn/api-client'
import { formatEventDateTime } from '../../lib/event-labels'
import {
  addToCart,
  cartTotalQuantity,
  formatMinor,
  newIdempotencyKey,
  quoteTotals,
  setQuantity,
  toOrderLines,
  validateCart,
  type CartLine,
} from '../../lib/checkout'

/**
 * 购买页 /checkout（原型 web/purchase.html 步骤 ②③④）。
 *
 * 步骤：选场次（/sessions/{id} 进来）→ 选票档与数量 → 服务端报价 → 下单。
 * 关键红线：
 * - 金额一律取服务端报价（POST /checkout/quote 不扣库存），下单时后端重算并原子预留；
 * - 幂等键在一次结算尝试内固定，改购物车后换新键，防重复下单又不误判冲突；
 * - 座位选区与实名信息后端契约里没有，不渲染假表单（见页面内说明）。
 */

const STEPS = ['选场次', '票档/数量', '报价', '下单', '支付', '出票'] as const

/** 演示身份：无登录态时下单需要 userId，显式说明而不是伪造静默身份 */
const DEMO_USER_KEY = 'awn-demo-user'

function CheckoutFlow() {
  const params = useSearchParams()
  const router = useRouter()
  const sessionId = params.get('session') ?? ''
  const productCode = params.get('product') ?? ''

  const [session, setSession] = useState<SessionView | null>(null)
  const [products, setProducts] = useState<ProductListItem[]>([])
  const [cart, setCart] = useState<CartLine[]>([])
  const [quote, setQuote] = useState<QuoteView | null>(null)
  const [userId, setUserId] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [idempotencyKey, setIdempotencyKey] = useState(() => newIdempotencyKey())

  useEffect(() => {
    const saved = window.localStorage.getItem(DEMO_USER_KEY)
    if (saved) setUserId(saved)
  }, [])

  useEffect(() => {
    if (!sessionId) {
      setLoadError('缺少场次参数')
      setLoading(false)
      return
    }
    let alive = true
    setLoading(true)
    Promise.all([getSession(sessionId), getSessionProducts(sessionId)])
      .then(([sessionData, productList]) => {
        if (!alive) return
        setSession(sessionData)
        setProducts(productList)
        const picked = productList.find((p) => p.productCode === productCode)
        if (picked) setCart(addToCart([], picked))
      })
      .catch((err) => {
        if (!alive) return
        setLoadError(err instanceof ApiError && err.status === 404 ? '场次不存在或未开放' : '票档数据加载失败，请稍后重试')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [sessionId, productCode])

  /** 购物车变化即重算服务端报价；报价失败不阻塞选择，只是不展示金额 */
  useEffect(() => {
    const blocker = validateCart(cart)
    if (blocker || cart.length === 0) {
      setQuote(null)
      return
    }
    let alive = true
    quoteCheckout({ currency: 'CNY', lines: toOrderLines(cart) })
      .then((q) => {
        if (alive) setQuote(q)
      })
      .catch(() => {
        if (alive) setQuote(null)
      })
    return () => {
      alive = false
    }
  }, [cart])

  /** 改购物车即换幂等键：同键不同内容后端会判冲突 */
  const mutateCart = (next: CartLine[]) => {
    setCart(next)
    setIdempotencyKey(newIdempotencyKey())
    setSubmitError(null)
  }

  const totals = useMemo(() => quoteTotals(quote), [quote])
  const blocker = validateCart(cart)
  const userIdOk = userId.trim().length > 0

  const submit = async () => {
    if (!userIdOk || blocker || !quote || submitting) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      window.localStorage.setItem(DEMO_USER_KEY, userId.trim())
      const order = await createOrder({
        idempotencyKey,
        userId: userId.trim(),
        currency: 'CNY',
        lines: toOrderLines(cart),
      })
      router.push(`/orders/${order.orderNo}`)
    } catch (err) {
      setSubmitting(false)
      if (err instanceof ApiError) {
        // 库存是缓存展示口径，下单时后端原子重校验 —— 冲突按服务端结论告知
        setSubmitError(
          err.code === 'INSUFFICIENT_INVENTORY'
            ? '库存不足，请调整数量后重试'
            : err.code === 'IDEMPOTENCY_CONFLICT'
              ? '提交内容已变更，请重新提交'
              : `下单失败（${err.code}），请稍后重试`,
        )
      } else {
        setSubmitError('下单失败，请稍后重试')
      }
    }
  }

  if (loading) {
    return (
      <main className="page">
        <p className="state">加载中…</p>
      </main>
    )
  }

  if (loadError || !session) {
    return (
      <main className="page">
        <p className="state state--error">
          {loadError}。<Link href="/">返回发现列表</Link>
        </p>
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
        <Link href={`/sessions/${session.id}`}>{session.name}</Link>
        <span aria-hidden>/</span>
        <span>结算</span>
      </nav>

      <header className="page-head">
        <h1>结算</h1>
        {session.startTime && (
          <p className="muted num">{formatEventDateTime(session.startTime)} 开演</p>
        )}
      </header>

      <ol className="steps" aria-label="购买步骤">
        {STEPS.map((label, index) => (
          <li key={label} className="step" data-done={index < 3 ? 'true' : undefined}>
            <span className="n" aria-hidden>
              {index + 1}
            </span>
            {label}
          </li>
        ))}
      </ol>

      <div className="purchase-grid">
        <section className="card">
          <h2>票档与数量</h2>
          {products.length === 0 && <p className="muted">该场次暂未开放票档。</p>}
          <div className="tier-list">
            {products.map((product) => {
              const line = cart.find((l) => l.product.productCode === product.productCode)
              const cap = product.available == null ? 0 : Math.min(4, product.available)
              const blocked = cap <= 0
              return (
                <div key={product.productCode} className="tier-row" aria-disabled={blocked}>
                  <div className="row" style={{ gap: 12 }}>
                    <div>
                      <strong>{product.name}</strong>
                      <div className="muted" style={{ fontSize: 'var(--fs-xs)' }}>
                        {product.poolCode
                          ? product.available === 0
                            ? '已售罄'
                            : `可购 ${cap} 张`
                          : '未开售'}
                      </div>
                    </div>
                  </div>
                  <div className="row">
                    <span className="price price-yuan num">{formatMinor(product.unitPrice)}</span>
                    <div className="stepper">
                      <button
                        type="button"
                        aria-label={`减少 ${product.name} 数量`}
                        disabled={blocked || !line}
                        onClick={() =>
                          mutateCart(setQuantity(cart, product.productCode, (line?.quantity ?? 0) - 1))
                        }
                      >
                        −
                      </button>
                      <span className="num">{line?.quantity ?? 0}</span>
                      <button
                        type="button"
                        aria-label={`增加 ${product.name} 数量`}
                        disabled={blocked || (line?.quantity ?? 0) >= cap}
                        onClick={() =>
                          mutateCart(
                            line
                              ? setQuantity(cart, product.productCode, line.quantity + 1)
                              : addToCart(cart, product),
                          )
                        }
                      >
                        +
                      </button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          <div className="field field--grow" style={{ marginTop: 16 }}>
            <label htmlFor="userId">下单身份 userId</label>
            <input
              id="userId"
              value={userId}
              placeholder="演示用：后端尚无登录态，userId 必填"
              onChange={(e) => setUserId(e.target.value)}
            />
            <span className="hint">
              后端下单契约要求 userId；正式登录接入前用演示值，本机记忆在 localStorage。
            </span>
          </div>

          <div className="sunken" style={{ marginTop: 16, textAlign: 'left' }}>
            <strong>座位选区与实名信息</strong>
            <p className="hint">
              选区/选排（座位图 2D，见 todo 126）与观演人实名后端契约暂未提供，
              本页不渲染无法提交的表单；数量型票档按后端库存池规则售卖。
            </p>
          </div>
        </section>

        <aside className="cart-summary">
          <h2>结算摘要</h2>
          {cart.length === 0 ? (
            <p className="hint">未选择票档。</p>
          ) : (
            cart.map((line) => (
              <div key={line.product.productCode} className="row-between" style={{ margin: '12px 0' }}>
                <span className="muted">
                  {line.product.name} × {line.quantity}
                </span>
                <span className="price num">{formatMinor(line.product.unitPrice * line.quantity)}</span>
              </div>
            ))
          )}

          <hr className="divider" />
          <div className="row-between">
            <span className="muted">商品合计</span>
            <span className="price num">{totals.total}</span>
          </div>
          <div className="row-between">
            <span className="muted">优惠</span>
            <span className="price num">{totals.discount}</span>
          </div>
          <div className="row-between">
            <span className="muted">服务费</span>
            <span className="price num">{totals.fee}</span>
          </div>
          <div className="row-between" style={{ marginTop: 8 }}>
            <strong>应付</strong>
            <span className="price price-yuan num">{totals.pay}</span>
          </div>

          {blocker && <p className="hint">{blocker}</p>}
          {!blocker && !quote && <p className="hint">报价计算中…</p>}
          {submitError && <p className="state--error">{submitError}</p>}

          <button
            type="button"
            className="btn"
            style={{ marginTop: 16, alignSelf: 'stretch' }}
            disabled={Boolean(blocker) || !quote || !userIdOk || submitting}
            onClick={submit}
          >
            {submitting ? '提交中…' : `提交订单（共 ${cartTotalQuantity(cart)} 张）`}
          </button>
          <p className="hint">金额以服务端报价为准；提交后进入待支付，超时自动关单释放库存。</p>
        </aside>
      </div>
    </main>
  )
}

export default function CheckoutPage() {
  return (
    <Suspense fallback={<p className="state">加载中…</p>}>
      <CheckoutFlow />
    </Suspense>
  )
}