import { useEffect, useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { View, Text } from '@tarojs/components'
import { mpApi, type EventView } from '../../lib/mp-api'
import { formatMpTime } from '../../lib/order-flow'

/**
 * 首页（原型 mobile/miniprogram.html ①发现）：公开活动列表（PUBLISHED / COMPLETED）。
 * 点击进入活动详情（②）；专场内容经「专场」页按编码渲染（渲染契约子集）。
 */
export default function Index() {
  const [events, setEvents] = useState<EventView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  const load = () => {
    setLoading(true)
    mpApi
      .listEvents()
      .then((list) => {
        setEvents(list)
        setFailed(false)
      })
      .catch(() => setFailed(true))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])
  useDidShow(() => {
    if (!loading && !failed && events.length === 0) load()
  })

  const openEvent = (id: string) => Taro.navigateTo({ url: `/pages/event/index?id=${id}` })
  const openCampaign = () => Taro.navigateTo({ url: '/pages/campaign/index' })

  return (
    <View className="page">
      <View className="row-between">
        <Text className="title">发现</Text>
        <Text className="link" onClick={openCampaign}>
          专场预览
        </Text>
      </View>

      {loading && <Text className="state">加载中…</Text>}
      {failed && (
        <View>
          <Text className="state state-error">活动加载失败，请确认接口已启动。</Text>
          <Text className="link" onClick={load}>
            重试
          </Text>
        </View>
      )}
      {!loading && !failed && events.length === 0 && (
        <Text className="state">暂无可浏览的活动。</Text>
      )}

      {events.map((event) => (
        <View key={event.id} className="card" onClick={() => openEvent(event.id)}>
          <View className="row-between">
            <Text className="card-title">{event.name}</Text>
            <Text className="badge">{event.status === 'PUBLISHED' ? '售票中' : '已结束'}</Text>
          </View>
          {event.startTime ? (
            <Text className="muted num">{formatMpTime(event.startTime)} 开始</Text>
          ) : null}
        </View>
      ))}
    </View>
  )
}
