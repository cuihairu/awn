import { useEffect, useState } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text } from '@tarojs/components'
import { mpApi, type EventView, type SessionView } from '../../lib/mp-api'
import { formatMpTime } from '../../lib/order-flow'

/** 活动详情（原型 ②）：Hero + 场次列表，点场次进票档页（③） */
export default function EventPage() {
  const { params } = useRouter()
  const eventId = params.id ?? ''

  const [event, setEvent] = useState<EventView | null>(null)
  const [sessions, setSessions] = useState<SessionView[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    Promise.all([mpApi.getEvent(eventId), mpApi.listEventSessions(eventId)])
      .then(([eventData, sessionList]) => {
        if (!alive) return
        setEvent(eventData)
        setSessions(sessionList)
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
  }, [eventId])

  const openSession = (sessionId: string) =>
    Taro.navigateTo({ url: `/pages/session/index?id=${sessionId}` })

  if (loading)
    return (
      <View className="page">
        <Text className="state">加载中…</Text>
      </View>
    )
  if (failed || !event)
    return (
      <View className="page">
        <Text className="state state-error">活动不存在或未发布。</Text>
      </View>
    )

  return (
    <View className="page">
      <View className="row-between">
        <Text className="title">{event.name}</Text>
        <Text className="badge">{event.status === 'PUBLISHED' ? '售票中' : '已结束'}</Text>
      </View>
      {event.startTime ? (
        <Text className="muted num">
          {formatMpTime(event.startTime)} 至 {formatMpTime(event.endTime)}
        </Text>
      ) : null}

      <Text className="card-title">选择场次</Text>
      {sessions.length === 0 && <Text className="state">该活动暂未开放场次。</Text>}
      {sessions.map((session) => (
        <View key={session.id} className="card" onClick={() => openSession(session.id)}>
          <Text className="card-title">{session.name}</Text>
          {session.startTime ? (
            <Text className="muted num">{formatMpTime(session.startTime)}</Text>
          ) : null}
          <Text className="link">查看票档 →</Text>
        </View>
      ))}
    </View>
  )
}

