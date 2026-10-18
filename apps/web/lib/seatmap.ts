/**
 * 2D 座位图数据层（原型 docs/design/prototypes/web/seatmap-2d.html + V17__platform_seats.sql）。
 *
 * 边界（原型已定）：
 * - 座位图 = 空间与编号，库存 = 可售状态，两者不是一个模型；
 *   「可选」仅表示座位数据标记为可售，实际可售以下单时后端原子预留为准。
 * - 座位坐标是 S2 契约先行（platform_seats V27 pos_x/pos_y，公开面 x/y 可选）：
 *   null / 缺省 = 坐标缺失，渲染照旧程序化网格排布；坐标写入路径随数据管线
 *   （挂起项拍板）落地，落地前恒为 null，有值也不在本层消费（S3 再接）。
 * - 图例与原型一致三态：可选 / 已售不可售（置灰）/ 本次选中；
 *   RESERVED / SOLD / DELIVERED / INVALID 在图上同为不可选，悬停文案区分。
 */

export const SEAT_STATUSES = ['IN_STOCK', 'RESERVED', 'SOLD', 'DELIVERED', 'INVALID'] as const

export type SeatStatus = (typeof SEAT_STATUSES)[number]

/**
 * 后端 GET /sessions/{sessionId}/seatmap 载荷条目（对齐 api 座位图条目；
 * productCode 供票档页把座位归到座位型商品，分组渲染不消费它）。
 */
export interface SeatInput {
  seatNo: string
  productCode?: string
  section: string
  row: string
  seat: string
  status: SeatStatus
  /** S2 契约先行：可选座位坐标，null / 缺省 = 坐标缺失，回退程序化网格（本层不消费） */
  x?: number | null
  y?: number | null
}

export interface SeatCell {
  seatNo: string
  label: string
  status: SeatStatus
}

export interface SeatRow {
  label: string
  seats: SeatCell[]
}

export interface SeatSection {
  name: string
  rows: SeatRow[]
}

/** 状态中文文案（逐座状态机五个取值，不发明后端没有的状态）。 */
export const SEAT_STATUS_LABEL_ZH: Record<SeatStatus, string> = {
  IN_STOCK: '可选',
  RESERVED: '已预留',
  SOLD: '已售',
  DELIVERED: '已出票',
  INVALID: '不可用',
}

/** 原型口径：只有 IN_STOCK 可选；其余在图上同为「已售 / 不可售（置灰）」。 */
export function isSeatSelectable(status: SeatStatus): boolean {
  return status === 'IN_STOCK'
}

export function seatOccupied(status: SeatStatus): boolean {
  return !isSeatSelectable(status)
}

/** 编号感知排序：'2' < '10'，字母排用字典序。 */
function compareLabel(a: string, b: string): number {
  return a.localeCompare(b, 'zh-Hans-CN', { numeric: true })
}

/** 把后端平面座位列表按 分区 → 排 → 座 分组排序，供渲染层直接使用。 */
export function groupSeatSections(seats: SeatInput[]): SeatSection[] {
  const sections = new Map<string, Map<string, SeatCell[]>>()
  for (const seat of seats) {
    const rows = sections.get(seat.section) ?? new Map<string, SeatCell[]>()
    const cells = rows.get(seat.row) ?? []
    cells.push({ seatNo: seat.seatNo, label: seat.seat, status: seat.status })
    rows.set(seat.row, cells)
    sections.set(seat.section, rows)
  }
  return [...sections.entries()]
    .sort(([a], [b]) => compareLabel(a, b))
    .map(([name, rows]) => ({
      name,
      rows: [...rows.entries()]
        .sort(([a], [b]) => compareLabel(a, b))
        .map(([label, cells]) => ({
          label,
          seats: cells.sort((a, b) => compareLabel(a.label, b.label)),
        })),
    }))
}

/** 网格布局常量（渲染层共用，保证行内座位等距）。 */
export const SEAT_LAYOUT = {
  seatSize: 18,
  seatGap: 4,
  rowGap: 6,
  sectionGap: 24,
  labelWidth: 30,
} as const

/**
 * 座位型商品编码集合：后端没有显式「座位型」标记，以座位图载荷中出现过的
 * 商品编码为准，不发明字段。空载荷 = 无座位型商品。
 */
export function seatProductCodes(seats: SeatInput[]): Set<string> {
  return new Set(
    seats.filter((s) => typeof s.productCode === 'string').map((s) => s.productCode as string),
  )
}

/** 某座位型商品的座位（按载荷顺序，分组交给 groupSeatSections）。 */
export function seatsOfProduct(seats: SeatInput[], productCode: string): SeatInput[] {
  return seats.filter((s) => s.productCode === productCode)
}

/* ------------------------------------------------------------------ *
 * 选区携带（/sessions/{id} 选座 → /checkout 下单）
 * 后端契约没有座位购物车字段，也不引入登录态：sessionStorage 按场次暂存，
 * 关页即失、不进 URL、跨场次隔离。读取一律容错——脏数据按「未选座」处理，
 * 不让解析异常打断页面。
 * ------------------------------------------------------------------ */

const SEAT_SELECTION_KEY = 'awn-seat-selection'

type SelectionStore = Record<string, SeatSelection>

/** 某场次的选座结果：票档编码 → 已选座位号（一行一座） */
export type SeatSelection = Record<string, string[]>

function readStore(): SelectionStore {
  try {
    const raw = window.sessionStorage.getItem(SEAT_SELECTION_KEY)
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    return typeof parsed === 'object' && parsed !== null ? (parsed as SelectionStore) : {}
  } catch {
    return {}
  }
}

function writeStore(store: SelectionStore): void {
  try {
    window.sessionStorage.setItem(SEAT_SELECTION_KEY, JSON.stringify(store))
  } catch {
    // 存储不可用（隐私模式限额等）时静默降级：本次结算不带座位，下单走数量型
  }
}

/** 记录某场次的选座结果（整体替换该场次，不累加）；SSR 环境为空操作 */
export function saveSeatSelection(sessionId: string, selection: SeatSelection): void {
  if (typeof window === 'undefined' || !sessionId) return
  writeStore({ ...readStore(), [sessionId]: selection })
}

/** 读取某场次的选座结果；无记录 / 脏数据一律返回空对象 */
export function loadSeatSelection(sessionId: string): SeatSelection {
  if (typeof window === 'undefined' || !sessionId) return {}
  const entry = readStore()[sessionId]
  if (!entry || typeof entry !== 'object') return {}
  const result: SeatSelection = {}
  for (const [productCode, seatNos] of Object.entries(entry)) {
    if (Array.isArray(seatNos)) result[productCode] = seatNos.filter((n) => typeof n === 'string')
  }
  return result
}

/** 结算完成后清掉本场次选区，避免下次进入该场次时读到旧选座 */
export function clearSeatSelection(sessionId: string): void {
  if (typeof window === 'undefined' || !sessionId) return
  const store = readStore()
  delete store[sessionId]
  writeStore(store)
}
