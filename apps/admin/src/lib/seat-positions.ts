/**
 * 铺座表单文本解析（座位运营入口）：一次铺一个商品在同一场次下的座位，
 * 运营按「分区,排,座」逐行粘贴；解析不吞错——坏行带 1 基行号返回给表单提示，
 * 由运营修正后重贴，不做静默丢弃（铺座是整体成功或整体失败的批次操作）。
 */

export interface ParsedSeatPosition {
  section: string
  row: string
  seat: string
}

export interface SeatPositionsParseResult {
  positions: ParsedSeatPosition[]
  /** 首个无法解析的行（含 1 基行号）；输入全部合法时为 null */
  error: string | null
}

/** 解析铺座文本：每行「分区,排,座」，分隔符兼容逗号（含中文全角）与制表符（表格粘贴） */
export function parseSeatPositions(text: string): SeatPositionsParseResult {
  const positions: ParsedSeatPosition[] = []
  const lines = text.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].trim()
    if (raw === '') continue
    const fields = raw.split(/[,，\t]+/).map((f) => f.trim())
    if (fields.length !== 3 || fields.some((f) => f === '')) {
      return {
        positions: [],
        error: `第 ${i + 1} 行无法解析（应为「分区,排,座」三段）：${raw}`,
      }
    }
    positions.push({ section: fields[0], row: fields[1], seat: fields[2] })
  }
  return { positions, error: null }
}
