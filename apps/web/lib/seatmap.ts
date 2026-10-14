/**
 * 2D 座位图数据层（原型 docs/design/prototypes/web/seatmap-2d.html + V17__platform_seats.sql）。
 *
 * 边界（原型已定）：
 * - 座位图 = 空间与编号，库存 = 可售状态，两者不是一个模型；
 *   「可选」仅表示座位数据标记为可售，实际可售以下单时后端原子预留为准。
 * - platform_seats 只有 分区/排/座 编号，没有坐标字段——渲染用程序化网格排布，
 *   不臆造场馆几何（弧形/朝向等需要空间数据源，挂起项调研覆盖）。
 * - 图例与原型一致三态：可选 / 已售不可售（置灰）/ 本次选中；
 *   RESERVED / SOLD / DELIVERED / INVALID 在图上同为不可选，悬停文案区分。
 */

export const SEAT_STATUSES = ['IN_STOCK', 'RESERVED', 'SOLD', 'DELIVERED', 'INVALID'] as const

export type SeatStatus = (typeof SEAT_STATUSES)[number]

/** 后端 GET /sessions/{sessionId}/seatmap 计划载荷（对齐 inventory.api.SeatView 字段）。 */
export interface SeatInput {
  seatNo: string
  section: string
  row: string
  seat: string
  status: SeatStatus
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
