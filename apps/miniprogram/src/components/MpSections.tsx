import { View, Text } from '@tarojs/components'
import type { NormalizedSection } from '../lib/sections'

/**
 * 专场 section 渲染（Taro 子集，View/Text 文本优先）。
 * 未收录类型已在 normalizeSections 阶段跳过（skipped 列表由页面 console.warn，
 * 与 web 渲染器「白名单外拒绝渲染」同语义）；此处无 dangerouslySetInnerHTML 类通道。
 */
export function MpSections({ sections }: { sections: NormalizedSection[] }) {
  return (
    <View className="sections">
      {sections.map((section) => {
        if (section.type === 'noticeBar') {
          return (
            <View key={section.id} className="notice-bar">
              <Text>{section.entries[0]?.value}</Text>
            </View>
          )
        }
        return (
          <View key={section.id} className="section-card">
            {section.title ? <Text className="section-title">{section.title}</Text> : null}
            {section.paragraphs.map((paragraph, i) => (
              <Text key={i} className="section-paragraph">
                {paragraph}
              </Text>
            ))}
            {section.entries
              .filter((entry) => entry.value)
              .map((entry, i) => (
                <View key={i} className="section-entry">
                  {section.type === 'faq' ? (
                    <>
                      <Text className="entry-q">{entry.key}</Text>
                      <Text className="entry-a">{entry.value}</Text>
                    </>
                  ) : (
                    <Text className="entry-a">{entry.value}</Text>
                  )}
                </View>
              ))}
          </View>
        )
      })}
    </View>
  )
}
