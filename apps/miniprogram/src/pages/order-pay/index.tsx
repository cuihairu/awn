import { useEffect, useState } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text } from '@tarojs/components'
import { mpApi, type PaymentView } from '../../lib/mp-api'
import { minorToYuan } from '../../lib/order-flow'

/**
 * 支付页（原型 ⑤）：发起支付（POST /orders/{orderNo}/payments，渠道 sandbox）→
 * 展示支付单状态。微信小程序拉起支付（Taro.requestPayment）需商户号与签名参数，
 * 沙箱环境无此配置，故「拉起微信支付」为显式占位，不伪造支付成功。
 */
export default function OrderPayPage() {
  const { params } = useRouter()
  const orderNo = params.orderNo ?? ''
  const amount = Number(params.amount ?? 0)

  const [payment, setPayment] = useState<PaymentView | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const pay = async () => {
    setSubmitting(true)
    setError('')
    try {
      const created = await mpApi.createPayment(orderNo, 'sandbox')
      setPayment(created)
    } catch (err) {
      setError(err instanceof Error ? err.message : '支付发起失败')
    } finally {
      setSubmitting(false)
    }
  }

  useEffect(() => {
    if (!orderNo) return
    mpApi
      .listPayments(orderNo)
      .then((list) => {
        if (list.length > 0) setPayment(list[list.length - 1])
      })
      .catch(() => undefined)
  }, [orderNo])

  return (
    <View className="page">
      <Text className="title">支付</Text>

      <View className="card">
        <View className="row-between">
          <Text>订单号</Text>
          <Text className="num">{orderNo}</Text>
        </View>
        <View className="row-between">
          <Text className="card-title">应付金额</Text>
          <Text className="price num">¥{minorToYuan(amount)}</Text>
        </View>
      </View>

      {payment ? (
        <View className="card">
          <View className="row-between">
            <Text>支付单号</Text>
            <Text className="num">{payment.paymentNo}</Text>
          </View>
          <View className="row-between">
            <Text>渠道</Text>
            <Text className="num">{payment.channel}</Text>
          </View>
          <View className="row-between">
            <Text>状态</Text>
            <Text className={`badge ${payment.status === 'SUCCEEDED' ? 'badge-success' : 'badge-warning'}`}>
              {payment.status}
            </Text>
          </View>
          {payment.paidAt ? (
            <Text className="muted num">支付时间 {payment.paidAt}</Text>
          ) : null}
        </View>
      ) : (
        <Text className="state">尚未发起支付。</Text>
      )}

      {error ? (
        <Text className="state state-error" style="padding: 8px 0">
          {error}
        </Text>
      ) : null}

      <Text
        className={`btn${submitting ? ' btn--disabled' : ''}`}
        onClick={submitting ? undefined : pay}
      >
        {submitting ? '发起中…' : '发起支付（沙箱）'}
      </Text>
      <Text className="muted">
        沙箱渠道不产生真实扣款；微信小程序拉起支付（Taro.requestPayment）需商户号与签名，
        当前环境未配置，支付成功以支付单状态为准。
      </Text>
    </View>
  )
}
