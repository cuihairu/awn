import { useMemo, useState } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { Input, View, Text } from '@tarojs/components'
import { mpApi } from '../../lib/mp-api'
import {
  buildDraft,
  minorToYuan,
  newIdempotencyKey,
  validateDraft,
  type CartLine,
} from '../../lib/order-flow'

/**
 * 实名确认（原型 ④）：核对票档/数量 → 生成幂等键 → 创建订单（POST /orders）。
 * 后端下单行不带实名字段（实名制属后续购票规则），本页收集的联系人仅随Toast提示，
 * 不参与下单请求——不为后端没有的字段造假数据。userId 为登录联调前的演示输入。
 */
export default function OrderConfirmPage() {
  const { params } = useRouter()
  const cart: CartLine[] = useMemo(() => {
    try {
      return JSON.parse(decodeURIComponent(params.cart ?? '[]')) as CartLine[]
    } catch {
      return []
    }
  }, [params.cart])

  const [userId, setUserId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const draft = useMemo(() => buildDraft(cart), [cart])
  const issues = useMemo(() => validateDraft(cart), [cart])
  const idempotencyKey = useMemo(() => newIdempotencyKey(), [])

  const submit = async () => {
    if (issues.length > 0) {
      setError(issues[0].message)
      return
    }
    if (!userId.trim()) {
      setError('请填写下单用户 ID（登录联调前的演示输入）。')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const order = await mpApi.createOrder(idempotencyKey, userId.trim(), draft.lines)
      Taro.redirectTo({
        url: `/pages/order-pay/index?orderNo=${order.orderNo}&amount=${order.payAmount}`,
      })
    } catch (err) {
      const message = err instanceof Error ? err.message : ''
      setError(
        `下单失败：${message || '库存或参数校验未通过'}（幂等键已保留，重试不会重复下单）`,
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View className="page">
      <Text className="title">实名确认</Text>

      <View className="card">
        {cart.map((line: CartLine) => (
          <View key={line.product.productCode} className="row-between">
            <Text>
              {line.product.name} × {line.quantity}
            </Text>
            <Text className="num">¥{minorToYuan(line.product.unitPrice * line.quantity)}</Text>
          </View>
        ))}
        <View className="row-between">
          <Text className="card-title">合计</Text>
          <Text className="price num">¥{minorToYuan(draft.totalMinor)}</Text>
        </View>
        <Text className="muted">展示合计仅供参考，下单时服务端按订单行重算金额。</Text>
      </View>

      <View className="card">
        <Text className="card-title">下单用户（演示输入）</Text>
        <Input placeholder="用户 ID（登录联调前手填）" value={userId} onInput={(e) => setUserId(e.detail.value)} />
        <Text className="muted">
          实名制字段后端下单接口暂未提供；联系人信息不参与本次请求。观看人须与证件一致，
          入场核验以现场为准。
        </Text>
      </View>

      {issues.length > 0 && (
        <View className="notice-bar">
          {issues.map((issue) => (
            <Text key={issue.productCode}>
              {issue.productCode}：{issue.message}
            </Text>
          ))}
        </View>
      )}
      {error ? (
        <Text className="state state-error" style="padding: 8px 0">
          {error}
        </Text>
      ) : null}

      <Text
        className={`btn${submitting || issues.length > 0 ? ' btn--disabled' : ''}`}
        onClick={submitting ? undefined : submit}
      >
        {submitting ? '提交中…' : '提交订单'}
      </Text>
    </View>
  )
}
