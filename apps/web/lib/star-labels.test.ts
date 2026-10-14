import { describe, expect, it } from 'vitest'
import {
  CAST_ROLE_ORDER,
  PROFESSION_LABEL_ZH,
  PROFESSION_ORDER,
  ROLE_LABEL_ZH,
  WORK_TYPE_LABEL_ZH,
  WORK_TYPE_ORDER,
  formatRole,
  formatYear,
  groupCastByRole,
  groupPersonsByProfession,
  groupWorksByType,
  professionLabel,
  roleLabel,
  workTypeLabel,
  type Group,
  type PersonView,
  type PersonWorkEntry,
  type WorkType,
} from './star-labels'

/**
 * 图鉴展示层契约（docs/design/star-catalog.md）：
 * 枚举编码与后端白名单一一对应（modules/awn-star StarCatalogTypes）；
 * 分组顺序固定，未知编码排末尾不丢数据；空角色名不渲染成 "undefined"。
 */

const ALL_PROFESSIONS = [
  'actor',
  'singer',
  'host',
  'director',
  'musician',
  'producer',
  'screenwriter',
  'dancer',
  'model',
]

const ALL_WORK_TYPES = ['movie', 'tv', 'variety', 'song', 'album', 'concert', 'stage']

const ALL_ROLE_TYPES = [
  'cast_lead',
  'cast_supporting',
  'cast_cameo',
  'director',
  'screenwriter',
  'producer',
  'composer',
  'lyricist',
  'theme_song',
  'voice',
]

function person(id: string, professionTypes: string[]): PersonView {
  return {
    id,
    name: `人物${id}`,
    originalName: null,
    aliases: [],
    nationality: '中国',
    professionTypes: professionTypes as PersonView['professionTypes'],
    bio: null,
    photoUrl: null,
    status: 'PUBLISHED',
  }
}

function entry(overrides: Partial<PersonWorkEntry>): PersonWorkEntry {
  return {
    personId: '1',
    workId: '10',
    personName: '沈流舒',
    workTitle: '长风渡',
    workType: 'tv',
    workYear: 2025,
    roleType: 'cast_lead',
    roleName: '',
    billingOrder: null,
    ...overrides,
  }
}

describe('枚举文案映射', () => {
  it('covers every profession code with a non-empty Chinese label', () => {
    expect(Object.keys(PROFESSION_LABEL_ZH).sort()).toEqual([...ALL_PROFESSIONS].sort())
    for (const code of ALL_PROFESSIONS) {
      expect(PROFESSION_LABEL_ZH[code as keyof typeof PROFESSION_LABEL_ZH]).toMatch(/[一-鿿]/)
    }
  })

  it('covers every work type code', () => {
    expect(Object.keys(WORK_TYPE_LABEL_ZH).sort()).toEqual([...ALL_WORK_TYPES].sort())
    for (const code of ALL_WORK_TYPES) {
      expect(WORK_TYPE_LABEL_ZH[code as keyof typeof WORK_TYPE_LABEL_ZH]).toMatch(/[一-鿿]/)
    }
  })

  it('covers every role type code', () => {
    expect(Object.keys(ROLE_LABEL_ZH).sort()).toEqual([...ALL_ROLE_TYPES].sort())
    for (const code of ALL_ROLE_TYPES) {
      expect(ROLE_LABEL_ZH[code as keyof typeof ROLE_LABEL_ZH]).toMatch(/[一-鿿]/)
    }
  })

  it('falls back to the raw code for unknown enums', () => {
    expect(professionLabel('astronaut')).toBe('astronaut')
    expect(workTypeLabel('hologram')).toBe('hologram')
    expect(roleLabel('stunt-double')).toBe('stunt-double')
  })
})

describe('groupPersonsByProfession', () => {
  it('puts a multi-profession person in each matching group', () => {
    const groups = groupPersonsByProfession([person('1', ['actor', 'singer'])])
    expect(groups.map((g) => g.key)).toEqual(['actor', 'singer'])
    for (const group of groups) {
      expect(group.items.map((p) => p.id)).toEqual(['1'])
    }
  })

  it('keeps the fixed profession order regardless of input order', () => {
    const groups = groupPersonsByProfession([
      person('1', ['singer']),
      person('2', ['actor']),
    ])
    expect(groups.map((g) => g.key)).toEqual(['actor', 'singer'])
  })

  it('drops professions with no members', () => {
    const groups = groupPersonsByProfession([person('1', ['actor'])])
    expect(groups).toHaveLength(1)
    expect(groups[0].key).toBe('actor')
  })
})

describe('groupWorksByType / groupCastByRole', () => {
  it('orders known groups by the declared order', () => {
    const groups = groupWorksByType([
      entry({ workType: 'song' }),
      entry({ workType: 'movie' }),
    ])
    expect(groups.map((g) => g.key)).toEqual(['movie', 'song'])
  })

  it('appends unknown work types at the end without losing data', () => {
    const groups = groupWorksByType([
      entry({ workType: 'hologram' as WorkType }),
      entry({ workType: 'movie' }),
    ])
    expect(groups.map((g) => g.key)).toEqual(['movie', 'hologram'])
  })

  it('orders cast groups by the declared role order', () => {
    const groups = groupCastByRole([
      entry({ roleType: 'voice' }),
      entry({ roleType: 'cast_lead' }),
    ])
    expect(groups.map((g) => g.key)).toEqual(['cast_lead', 'voice'])
  })

  it('matches the declared cast order end to end', () => {
    const groups = groupCastByRole(CAST_ROLE_ORDER.map((roleType) => entry({ roleType })))
    expect(groups.map((g) => g.key)).toEqual(CAST_ROLE_ORDER)
  })
})

describe('formatRole / formatYear', () => {
  it('combines role and role name when the name is present', () => {
    expect(formatRole(entry({ roleType: 'cast_lead', roleName: '饰 沈砚' }))).toBe('主演 · 饰 沈砚')
  })

  it('drops the separator when the role name is blank', () => {
    expect(formatRole(entry({ roleType: 'theme_song', roleName: '' }))).toBe('演唱主题曲')
    expect(formatRole(entry({ roleType: 'theme_song', roleName: '   ' }))).toBe('演唱主题曲')
  })

  it('renders unknown years as 年份未知', () => {
    expect(formatYear(null)).toBe('年份未知')
    expect(formatYear(undefined)).toBe('年份未知')
    expect(formatYear(2025)).toBe('2025')
  })
})

describe('Group shape', () => {
  it('exposes key, label and items', () => {
    const group: Group<PersonView> = {
      key: 'actor',
      label: '演员',
      items: [person('1', ['actor'])],
    }
    expect(group.label).toBe('演员')
    expect(group.items).toHaveLength(1)
  })
})
