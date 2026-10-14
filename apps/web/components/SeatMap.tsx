'use client'

import { useState } from 'react'
import { SEAT_LAYOUT, SEAT_STATUS_LABEL_ZH, isSeatSelectable, type SeatSection } from '../lib/seatmap'

/**
 * 2D 座位图（原型 web/seatmap-2d.html）+ 视角增强（docs/design/seatmap-3d-plan.md S1）。
 *
 * 只有分区/排/座编号、没有坐标数据，所以按网格程序化排布；舞台位置与朝向
 * 以后端座位配置为准（未落地），此处只标方向而不声称几何正确。
 * 交互只做选择态收集，提交时后端重新校验（显示可选 ≠ 后台有库存）。
 * 透视切换只做 CSS 变换（S1 视角增强）：不改节点结构，选座/键盘/读屏仍在同一层。
 */
interface SeatMapProps {
  sections: SeatSection[]
  selected: string[]
  onToggleSeat?: (seatNo: string) => void
}

interface SectionLayout {
  section: SeatSection
  x: number
  width: number
  height: number
}

const { seatSize, seatGap, rowGap, sectionGap, labelWidth } = SEAT_LAYOUT
const STAGE_HEIGHT = 40

const VIEW_MODES = [
  { key: 'flat', label: '俯视' },
  { key: 'tilted', label: '透视' },
] as const

type ViewMode = (typeof VIEW_MODES)[number]['key']

export function SeatMap({ sections, selected, onToggleSeat }: SeatMapProps) {
  const [view, setView] = useState<ViewMode>('flat')
  const selectedSeats = new Set(selected)
  const layouts = layoutSections(sections)
  const totalWidth = Math.max(
    layouts.reduce((width, layout) => width + layout.width, 0) +
      Math.max(0, layouts.length - 1) * sectionGap +
      24,
    240,
  )
  const totalHeight = STAGE_HEIGHT + 12 + Math.max(...layouts.map((layout) => layout.height), 0) + 12

  return (
    <div className="seatmap">
      <div className="seatmap-view" role="group" aria-label="视角切换">
        {VIEW_MODES.map((mode) => (
          <button
            key={mode.key}
            type="button"
            aria-pressed={view === mode.key}
            onClick={() => setView(mode.key)}
          >
            {mode.label}
          </button>
        ))}
      </div>
      <div className={view === 'tilted' ? 'seatmap-viewport seatmap-viewport--tilted' : 'seatmap-viewport'}>
        <svg
            viewBox={`0 0 ${totalWidth} ${totalHeight}`}
            className="seatmap-svg"
            role="group"
            aria-label="场馆分区座位图"
            style={{ width: '100%', height: 'auto', display: 'block' }}
          >
          <g transform="translate(0 8)">
            <rect
              x={0}
              y={0}
              width={totalWidth}
              height={STAGE_HEIGHT}
              rx={6}
              className="seatmap-stage"
            />
            <text x={totalWidth / 2} y={20} textAnchor="middle" className="seatmap-stage-title">
              舞台
            </text>
            <text x={totalWidth / 2} y={34} textAnchor="middle" className="seatmap-stage-note">
              位置与朝向以场次座位配置为准
            </text>
          </g>

          {layouts.map((layout) => (
            <g
              key={layout.section.name}
              transform={`translate(${layout.x} ${STAGE_HEIGHT + 12})`}
            >
              <text x={layout.width / 2} y={-8} textAnchor="middle" className="seatmap-section-title">
                {layout.section.name}
              </text>
              {layout.section.rows.map((row, rowIndex) => (
                <g key={row.label} transform={`translate(0 ${rowIndex * (seatSize + rowGap)})`}>
                  <text
                    x={labelWidth - 4}
                    y={seatSize - 4}
                    textAnchor="end"
                    className="seatmap-row-label"
                  >
                    {row.label}
                  </text>
                  {row.seats.map((seat, seatIndex) => {
                    const selectable = isSeatSelectable(seat.status)
                    const isSelected = selectedSeats.has(seat.seatNo)
                    const state = isSelected
                      ? 'seat--selected'
                      : selectable
                        ? 'seat--free'
                        : 'seat--occupied'
                    const label = `${layout.section.name} ${row.label} 排 ${seat.label} 座 ${SEAT_STATUS_LABEL_ZH[seat.status]}`
                    return (
                      <g
                        key={seat.seatNo}
                        role="button"
                        tabIndex={selectable ? 0 : -1}
                        aria-label={label}
                        aria-pressed={isSelected}
                        className="seat"
                        transform={`translate(${labelWidth + seatIndex * (seatSize + seatGap)} 0)`}
                        onClick={
                          selectable && onToggleSeat
                            ? () => onToggleSeat(seat.seatNo)
                            : undefined
                        }
                        onKeyDown={
                          selectable && onToggleSeat
                            ? (event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                  event.preventDefault()
                                  onToggleSeat(seat.seatNo)
                                }
                              }
                            : undefined
                        }
                      >
                        <title>{label}</title>
                        <rect width={seatSize} height={seatSize} rx={4} className={state} />
                      </g>
                    )
                  })}
                </g>
              ))}
            </g>
          ))}
      </svg>
      </div>

      <ul className="seat-legend" aria-label="座位图例">
        <li>
          <i className="seat-dot seat-dot--free" aria-hidden />
          可选
        </li>
        <li>
          <i className="seat-dot seat-dot--occupied" aria-hidden />
          已售 / 不可售
        </li>
        <li>
          <i className="seat-dot seat-dot--selected" aria-hidden />
          本次选中
        </li>
      </ul>
      <p className="hint">
        「可选」仅表示座位数据标记为可售；实际可售以下单时后端原子预留为准——显示可选 ≠ 后台有库存。
      </p>
    </div>
  )
}

function layoutSections(sections: SeatSection[]): SectionLayout[] {
  let x = 12
  return sections.map((section) => {
    const width = Math.max(
      ...section.rows.map((row) => labelWidth + row.seats.length * (seatSize + seatGap)),
      labelWidth + seatSize,
    )
    const height = section.rows.length * (seatSize + rowGap)
    const layout: SectionLayout = { section, x, width, height }
    x += width + sectionGap
    return layout
  })
}
