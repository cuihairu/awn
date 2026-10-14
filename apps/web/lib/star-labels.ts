/**
 * 图鉴展示层文案与分组（纯函数，无 React 依赖）。
 *
 * 枚举编码与后端白名单一一对应：StarCatalogTypes.PROFESSIONS / WORK_TYPES /
 * ROLE_TYPES（modules/awn-star）。后端只存编码，中文文案在前端维护，
 * 加枚举时改这里一处。
 */
import type {
  PersonView,
  PersonWorkEntry,
  ProfessionType,
  RoleType,
  WorkType,
} from '@awn/api-client'

export type {
  PersonView,
  PersonWorkEntry,
  ProfessionType,
  RoleType,
  WorkType,
} from '@awn/api-client'

export const PROFESSION_LABEL_ZH: Record<ProfessionType, string> = {
  actor: '演员',
  singer: '歌手',
  host: '主持人',
  director: '导演',
  musician: '音乐人',
  producer: '制片人',
  screenwriter: '编剧',
  dancer: '舞者',
  model: '模特',
}

/** 分组顺序即 chips 顺序，与后端白名单顺序一致 */
export const PROFESSION_ORDER: ProfessionType[] = [
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

export const WORK_TYPE_LABEL_ZH: Record<WorkType, string> = {
  movie: '电影',
  tv: '剧集',
  variety: '综艺',
  song: '歌曲',
  album: '专辑',
  concert: '演唱会',
  stage: '舞台',
}

export const WORK_TYPE_ORDER: WorkType[] = [
  'movie',
  'tv',
  'variety',
  'stage',
  'album',
  'song',
  'concert',
]

export const ROLE_LABEL_ZH: Record<RoleType, string> = {
  cast_lead: '主演',
  cast_supporting: '配角',
  cast_cameo: '客串',
  director: '导演',
  screenwriter: '编剧',
  producer: '制片',
  composer: '作曲',
  lyricist: '作词',
  theme_song: '演唱主题曲',
  voice: '配音',
}

/** 作品详情的演员表按身份分组，组序与上面的声明序一致 */
export const CAST_ROLE_ORDER: RoleType[] = [
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

export interface Group<T> {
  key: string
  label: string
  items: T[]
}

export function professionLabel(code: string): string {
  return PROFESSION_LABEL_ZH[code as ProfessionType] ?? code
}

export function workTypeLabel(code: string): string {
  return WORK_TYPE_LABEL_ZH[code as WorkType] ?? code
}

export function roleLabel(code: string): string {
  return ROLE_LABEL_ZH[code as RoleType] ?? code
}

export function formatYear(year: number | null | undefined): string {
  return year === null || year === undefined ? '年份未知' : `${year}`
}

/**
 * 身份行文案：角色名非空时拼成「主演 · 饰 沈砚」，
 * 为空时只给身份（「演唱主题曲」）。空角色名不显示成 "undefined"。
 */
export function formatRole(entry: PersonWorkEntry): string {
  const role = roleLabel(entry.roleType)
  const name = entry.roleName?.trim()
  return name ? `${role} · ${name}` : role
}

/**
 * 人物列表按职业类型分组。一人可属多类型，在每个命中的组里各出现一次；
 * 组合顺序按 PROFESSION_ORDER 固定，不受后端返回顺序影响。
 */
export function groupPersonsByProfession(
  persons: PersonView[],
): Group<PersonView>[] {
  const buckets = new Map<string, PersonView[]>()
  for (const profession of PROFESSION_ORDER) {
    const members = persons.filter((p) => p.professionTypes.includes(profession))
    if (members.length > 0) {
      buckets.set(profession, members)
    }
  }
  return [...buckets.entries()].map(([key, items]) => ({
    key,
    label: professionLabel(key),
    items,
  }))
}

/** 人物详情：参与作品按作品类型分组 */
export function groupWorksByType(
  entries: PersonWorkEntry[],
): Group<PersonWorkEntry>[] {
  return groupBy(entries, (e) => e.workType, WORK_TYPE_ORDER, workTypeLabel)
}

/** 作品详情：人员表按身份分组 */
export function groupCastByRole(
  entries: PersonWorkEntry[],
): Group<PersonWorkEntry>[] {
  return groupBy(entries, (e) => e.roleType, CAST_ROLE_ORDER, roleLabel)
}

function groupBy<T>(
  entries: T[],
  keyOf: (entry: T) => string,
  order: string[],
  labelOf: (key: string) => string,
): Group<T>[] {
  const buckets = new Map<string, T[]>()
  for (const entry of entries) {
    const key = keyOf(entry)
    const bucket = buckets.get(key)
    if (bucket) bucket.push(entry)
    else buckets.set(key, [entry])
  }
  // 已知编码走固定序；后端新增的未知编码排到末尾，不丢数据
  const known = order.filter((key) => buckets.has(key))
  const unknown = [...buckets.keys()]
    .filter((key) => !order.includes(key))
    .sort()
  return [...known, ...unknown].map((key) => ({
    key,
    label: labelOf(key),
    items: buckets.get(key)!,
  }))
}