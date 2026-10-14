import { describe, expect, it } from 'vitest'
import { parseSeatPositions } from './seat-positions'

/**
 * 铺座表单解析契约：运营粘贴「分区,排,座」逐行文本，
 * 容错（空格 / 空行 / 中文逗号）优先，坏行要能指出第几行，不静默丢弃。
 */

describe('parseSeatPositions', () => {
  it('parses one seat per line, trimming whitespace', () => {
    const result = parseSeatPositions('A, 1, 1\nB\t2\t7')
    expect(result).toEqual({
      positions: [
        { section: 'A', row: '1', seat: '1' },
        { section: 'B', row: '2', seat: '7' },
      ],
      error: null,
    })
  })

  it('skips blank lines', () => {
    const result = parseSeatPositions('\nA,1,1\n\nA,1,2\n')
    expect(result.positions).toHaveLength(2)
    expect(result.error).toBeNull()
  })

  it('accepts full-width commas as the separator', () => {
    expect(parseSeatPositions('A，1，1')).toEqual({
      positions: [{ section: 'A', row: '1', seat: '1' }],
      error: null,
    })
  })

  it('reports the first malformed line with its 1-based line number', () => {
    const result = parseSeatPositions('A,1,1\nA,1')
    expect(result.positions).toEqual([])
    expect(result.error).toContain('第 2 行')
    expect(result.error).toContain('A,1')
  })

  it('rejects extra fields as malformed', () => {
    const result = parseSeatPositions('A,1,1,extra')
    expect(result.positions).toEqual([])
    expect(result.error).toContain('第 1 行')
  })

  it('returns an empty result for blank input', () => {
    expect(parseSeatPositions('   ')).toEqual({ positions: [], error: null })
  })
})
