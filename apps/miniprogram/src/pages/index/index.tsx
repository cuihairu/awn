import { View, Text } from '@tarojs/components'
import './index.css'

/**
 * 首页骨架。Phase 02 接入统一商品 API 与专场渲染契约：
 * 专场配置由 packages/campaign-renderer 的 Taro 实现渲染，
 * 票档组件使用 packages/ticket-components，接口见 docs/api/contracts.md。
 */
export default function Index() {
  return (
    <View className="page">
      <View className="brand">
        <View className="logo-mark" />
        <Text className="title">麦芒网</Text>
      </View>
      <Text className="desc">票务交易与情报平台 · 小程序端（Taro + React）</Text>
      <View className="card">
        <Text>monorepo 骨架：Phase 02 实现活动、商品与专场渲染。</Text>
      </View>
    </View>
  )
}
