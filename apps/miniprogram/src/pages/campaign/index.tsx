import { useState } from 'react'
import { Input, View, Text } from '@tarojs/components'
import { mpApi } from '../../lib/mp-api'
import { normalizeSections, type NormalizedSection } from '../../lib/sections'
import { MpSections } from '../../components/MpSections'

/**
 * 专场预览（渲染契约子集）：按编码读取已发布版本（GET /campaigns/{code}/render）
 * 并用 MpSections 渲染文本优先子集；子集外模块跳过并告警。
 * 专场编码暂无公开列表入口（运营侧生成），此处手输编码，不为演示造假数据。
 */
export default function CampaignPage() {
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [sections, setSections] = useState<NormalizedSection[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  const load = (campaignCode: string) => {
    if (!campaignCode) return
    setLoading(true)
    setFailed(false)
    mpApi
      .renderCampaign(campaignCode)
      .then((page) => {
        const result = normalizeSections(page.sections ?? [])
        if (result.skipped.length > 0) {
          console.warn(`[campaign] 未在小程序子集内渲染的模块：${result.skipped.join(', ')}`)
        }
        setTitle(page.seoTitle)
        setSections(result.sections)
      })
      .catch(() => {
        setFailed(true)
        setTitle('')
        setSections([])
      })
      .finally(() => setLoading(false))
  }

  return (
    <View className="page">
      <Text className="title">专场预览</Text>
      <View className="row-between">
        <Input
          className="input"
          style="flex: 1"
          placeholder="输入专场编码，如 CAMP-F1-2026"
          value={code}
          onInput={(e) => setCode(e.detail.value)}
        />
        <Text className="btn btn-ghost" onClick={() => load(code.trim())}>
          查看
        </Text>
      </View>

      {loading && <Text className="state">加载中…</Text>}
      {failed && !loading && (
        <Text className="state state-error">专场不存在或未发布（只读已发布版本）。</Text>
      )}
      {!loading && !failed && title && (
        <View>
          <MpSections sections={sections} />
        </View>
      )}
      {!loading && !failed && title && sections.length === 0 && (
        <Text className="state">该专场没有小程序子集内可渲染的模块。</Text>
      )}
    </View>
  )
}
