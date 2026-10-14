import { useEffect, useMemo, useState } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text } from '@tarojs/components'
import { mpApi, type ProductListItem, type SessionView } from '../../lib/mp-api'
import {
  AVAILABILITY_LABEL_ZH,
  deriveAvailability,
  formatMpTime,
  minorToYuan,
} from '../../lib/order-flow'

/** 场次票档（原型 ③）：真实商品 + 可售状态；选数量后进实名确认（④） */
export default function SessionPage() {
  const { params } = useRouter()
  const sessionId = params.id ?? ''

  const [session, setSession] = useState<SessionView | null>(null)
  const [products, setProducts] = useState<ProductListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [quantities, setQuantities] = useState<Record<string, number>>({})

  useEffect(() => {
    let alive = true
    setLoading(true)
    Promise.all([mpApi.getSession(sessionId), mpApi.getSessionProducts(sessionId)])
      .then(([sessionData, productList]) => {
        if (!alive) return
        setSession(sessionData)
        setProducts(productList)
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
  }, [sessionId])

  /** 价格降序 → tier-dot 序号（1 = 最高价档，与 web 端一致） */
  const sorted = useMemo(
    () => [...products].sort((a, b) => b.unitPrice - a.unitPrice),
    [products],
  )

  const selected = Object.entries(quantities).filter(([, qty]) => qty > 0)

  const adjust = (product: ProductListItem, delta: number) => {
    const availability = deriveAvailability(product.available)
    if (availability === 'SoldOut' || availability === 'NotOnSale') return
    setQuantities((prev) => {
      const next = Math.max(0, (prev[product.productCode] ?? 0) + delta)
      return { ...prev, [product.productCode]: next }
    })
  }

  const goConfirm = () => {
    const cart = sorted
      .filter((product) => (quantities[product.productCode] ?? 0) > 0)
      .map((product) => ({ product, quantity: quantities[product.productCode] }))
    if (cart.length === 0) {
      Taro.showToast({ title: '请先选择票档与数量', icon: 'none' })
      return
    }
    Taro.navigateTo({
      url: `/pages/order-confirm/index?cart=${encodeURIComponent(JSON.stringify(cart))}&sessionId=${sessionId}`,
    })
  }

  if (loading)
    return (
      <View className="page">
        <Text className="state">加载中…</Text>
      </View>
    )
  if (failed || !session)
    return (
      <View className="page">
        <Text className="state state-error">场次不存在或未开放。</Text>
      </View>
    )

  return (
    <View className="page">
      <Text className="title">{session.name}</Text>
      {session.startTime ? (
        <Text className="muted num">{formatMpTime(session.startTime)} 开演</Text>
      ) : null}

      <Text className="card-title">票档</Text>
      {sorted.length === 0 && <Text className="state">该场次暂未开放票档。</Text>}
      {sorted.map((product, index) => {
        const availability = deriveAvailability(product.available)
        const off = availability === 'SoldOut' || availability === 'NotOnSale'
        const qty = quantities[product.productCode] ?? 0
        return (
          <View key={product.productCode} className={`tier-row${off ? ' tier-row--off' : ''}`}>
            <View className="row-between">
              <View className="row-between">
                <Text className={`tier-dot tier-dot--${(index % 5) + 1}`} />
                <Text className="card-title">{product.name}</Text>
              </View>
              <Text className="badge">
                {AVAILABILITY_LABEL_ZH[availability]}
              </Text>
            </View>
            <View className="row-between">
              <Text className="price">¥{minorToYuan(product.unitPrice)}</Text>
              {off ? (
                <Text className="muted">{AVAILABILITY_LABEL_ZH[availability]}</Text>
              ) : (
                <View className="row-between">
                  <Text className="btn btn-ghost" onClick={() => adjust(product, -1)}>
                    −
                  </Text>
                  <Text className="num" style="padding: 0 16px">
                    {qty}
                  </Text>
                  <Text className="btn btn-ghost" onClick={() => adjust(product, 1)}>
                    ＋
                  </Text>
                </View>
              )}
            </View>
          </View>
        )
      })}
      <Text className="muted">可售数量为缓存展示，实际以下单结果为准。</Text>

      <Text className={`btn${selected.length === 0 ? ' btn--disabled' : ''}`} onClick={goConfirm}>
        下一步 · 实名确认
      </Text>
    </View>
  )
}
