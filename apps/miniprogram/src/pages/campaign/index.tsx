import { useState } from 'react'
import { Input, View, Text } from '@tarojs/components'
import { mpApi } from '../../lib/mp-api'
import { resolveCampaignContent } from '../../lib/campaign-content'
import { normalizeSections, type NormalizedSection } from '../../lib/sections'
import { MpSections } from '../../components/MpSections'

/**
 * 专场入口 /pages/campaign（docs/api/contracts.md「专场列表与内容接口」）。
 *
 * 入口来自公开列表 GET /campaigns（只含已发布专场，草稿与已撤回不出现在这里），
 * 点选后读已发布版本 GET /campaigns/{code}/render；须知 / 常见问题的引用型 props
 * 按内容端点条目 id 解析成内联形态（resolveCampaignContent），
 * 未配置内容或未发布的模块渲染空条目，不为演示造假数据。
 * 仍保留按编码直达，便于运营核对未在列表中的专场。
 */
export default function CampaignPage() {
  const [list, setList] = useState<{ campaignCode: string; title: string }[]>([])
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [sections, setSections] = useState<NormalizedSection[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [listFailed, setListFailed] = useState(false)

  const load = (campaignCode: string) => {
    if (!campaignCode) return
    setLoading(true)
    setFailed(false)
    Promise.all([
      mpApi.renderCampaign(campaignCode),
      mpApi.getCampaignRules(campaignCode).catch(() => []),
      mpApi.getCampaignFaq(campaignCode).catch(() => []),
    ])
      .then(([page, rules, faq]) => {
        const resolved = resolveCampaignContent(page.sections ?? [], { rules, faq })
        const result = normalizeSections(resolved)
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

  const loadList = () => {
    mpApi
      .listCampaigns()
      .then((items) => {
        setList(
          items.map((item) => ({ campaignCode: item.campaignCode, title: item.title })),
        )
        setListFailed(false)
      })
      .catch(() => setListFailed(true))
  }

  return (
    <View className="page">
      <Text className="title">专场</Text>
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

      {list.length === 0 && !listFailed && (
        <Text className="btn btn-ghost" onClick={loadList}>
          加载已发布专场
        </Text>
      )}
      {listFailed && <Text className="state state-error">已发布专场列表加载失败。</Text>}
      {list.map((item) => (
        <View
          className="row-between"
          key={item.campaignCode}
          onClick={() => load(item.campaignCode)}
        >
          <Text>{item.title}</Text>
          <Text className="state">{item.campaignCode}</Text>
        </View>
      ))}

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
