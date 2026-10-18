import { describe, expect, it } from 'vitest'
import {
  SEAT_STATUSES,
  SEAT_STATUS_LABEL_ZH,
  groupSeatSections,
  isSeatSelectable,
  seatOccupied,
  seatsOfProduct,
  seatProductCodes,
  type SeatInput,
} from './seatmap'

/**
 * 座位图数据层契约（原型 seatmap-2d.html + V17__platform_seats.sql）：
 * 座位图 = 空间与编号，库存 = 可售状态；图上只有 IN_STOCK 可选，
 * 其余四态同为置灰不可选，悬停文案区分五种状态。
 */

const seat = (overrides: Partial<SeatInput> = {}): SeatInput => ({
  seatNo: 'SEAT-1',
  section: 'A',
  row: '1',
  seat: '1',
  status: 'IN_STOCK',
  ...overrides,
})

describe('seat status semantics', () => {
  it('covers every backend seat status without inventing new ones', () => {
    expect(SEAT_STATUSES).toEqual(['IN_STOCK', 'RESERVED', 'SOLD', 'DELIVERED', 'INVALID'])
    expect(Object.keys(SEAT_STATUS_LABEL_ZH).sort()).toEqual([...SEAT_STATUSES].sort())
  })

  it('treats only IN_STOCK as selectable', () => {
    expect(isSeatSelectable('IN_STOCK')).toBe(true)
    for (const status of ['RESERVED', 'SOLD', 'DELIVERED', 'INVALID'] as const) {
      expect(isSeatSelectable(status)).toBe(false)
      expect(seatOccupied(status)).toBe(true)
    }
    expect(seatOccupied('IN_STOCK')).toBe(false)
  })

  it('labels every status in Chinese', () => {
    expect(SEAT_STATUS_LABEL_ZH.IN_STOCK).toBe('可选')
    expect(SEAT_STATUS_LABEL_ZH.RESERVED).toBe('已预留')
    expect(SEAT_STATUS_LABEL_ZH.SOLD).toBe('已售')
    expect(SEAT_STATUS_LABEL_ZH.DELIVERED).toBe('已出票')
    expect(SEAT_STATUS_LABEL_ZH.INVALID).toBe('不可用')
  })
})

describe('groupSeatSections', () => {
  it('groups flat seat rows into sections, rows and cells', () => {
    const sections = groupSeatSections([
      seat({ seatNo: 'S1', section: 'B', row: '2', seat: '1' }),
      seat({ seatNo: 'S2', section: 'A', row: '1', seat: '2' }),
      seat({ seatNo: 'S3', section: 'A', row: '1', seat: '1' }),
    ])
    expect(sections.map((section) => section.name)).toEqual(['A', 'B'])
    expect(sections[0].rows.map((row) => row.label)).toEqual(['1'])
    expect(sections[0].rows[0].seats.map((cell) => cell.seatNo)).toEqual(['S3', 'S2'])
  })

  it('sorts labels numerically, not lexicographically', () => {
    const rows = groupSeatSections([
      seat({ seatNo: 'S10', row: '10', seat: '10' }),
      seat({ seatNo: 'S2', row: '2', seat: '2' }),
    ])[0].rows
    expect(rows.map((row) => row.label)).toEqual(['2', '10'])
    const seats = groupSeatSections([
      seat({ seatNo: 'S10', section: 'C', row: '1', seat: '10' }),
      seat({ seatNo: 'S2', section: 'C', row: '1', seat: '2' }),
    ])[0].rows[0].seats
    expect(seats.map((cell) => cell.label)).toEqual(['2', '10'])
  })

  it('renders the identical grid when coordinates are null or absent (S2 fallback)', () => {
    const plain = [
      seat({ seatNo: 'SEAT-1', section: 'A', row: '1', seat: '1' }),
      seat({ seatNo: 'SEAT-2', section: 'A', row: '1', seat: '2' }),
      seat({ seatNo: 'SEAT-3', section: 'B', row: '2', seat: '5' }),
    ]
    const withNullCoords = plain.map((s) => ({ ...s, x: null, y: null }))

    expect(groupSeatSections(withNullCoords)).toEqual(groupSeatSections(plain))
  })

  it('ignores coordinate values until rendering consumes them (S3 pending)', () => {
    const plain = [
      seat({ seatNo: 'SEAT-1', section: 'A', row: '1', seat: '1' }),
      seat({ seatNo: 'SEAT-2', section: 'A', row: '1', seat: '2' }),
    ]
    const withCoords = plain.map((s, i) => ({ ...s, x: 12.5 * (i + 1), y: 3.25 * (i + 1) }))

    expect(groupSeatSections(withCoords)).toEqual(groupSeatSections(plain))
  })

  it('returns an empty list for empty input', () => {
    expect(groupSeatSections([])).toEqual([])
  })
})

describe('seat products', () => {
  it('derives seat-type product codes from the seatmap payload, deduplicated', () => {
    const codes = seatProductCodes([
      seat({ productCode: 'p-a' }),
      seat({ productCode: 'p-a' }),
      seat({ productCode: 'p-b' }),
    ])
    expect([...codes].sort()).toEqual(['p-a', 'p-b'])
  })

  it('treats an empty seatmap as no seat products', () => {
    expect(seatProductCodes([]).size).toBe(0)
  })

  it('filters a product seatmap by product code', () => {
    const seats: SeatInput[] = [
      seat({ seatNo: 'S1', productCode: 'p-a' }),
      seat({ seatNo: 'S2', productCode: 'p-b' }),
      seat({ seatNo: 'S3', productCode: 'p-a' }),
    ]
    expect(seatsOfProduct(seats, 'p-a').map((s) => s.seatNo)).toEqual(['S1', 'S3'])
  })
})
