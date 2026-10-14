// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearSeatSelection,
  loadSeatSelection,
  saveSeatSelection,
} from './seatmap'

/**
 * 选区携带契约（座位选区 2D 余项）：/sessions/{id} 的选座结果必须能进 /checkout，
 * 但后端契约里没有座位购物车字段，也不新增登录态——
 * 用 sessionStorage 按 sessionId 暂存，跨会话隔离、关页即失、不写 URL。
 */

beforeEach(() => {
  sessionStorage.clear()
})

describe('seat selection transfer', () => {
  it('round-trips a selection per session', () => {
    saveSeatSelection('sess-1', { 'p-seat': ['SEAT-1', 'SEAT-2'] })
    expect(loadSeatSelection('sess-1')).toEqual({ 'p-seat': ['SEAT-1', 'SEAT-2'] })
  })

  it('keeps selections of different sessions apart', () => {
    saveSeatSelection('sess-1', { 'p-seat': ['SEAT-1'] })
    saveSeatSelection('sess-2', { 'p-seat': ['SEAT-9'] })
    expect(loadSeatSelection('sess-1')).toEqual({ 'p-seat': ['SEAT-1'] })
    expect(loadSeatSelection('sess-2')).toEqual({ 'p-seat': ['SEAT-9'] })
  })

  it('replaces that session selection instead of appending', () => {
    saveSeatSelection('sess-1', { 'p-seat': ['SEAT-1', 'SEAT-2'] })
    saveSeatSelection('sess-1', { 'p-seat': ['SEAT-3'] })
    expect(loadSeatSelection('sess-1')).toEqual({ 'p-seat': ['SEAT-3'] })
  })

  it('returns an empty selection for a session that never selected seats', () => {
    expect(loadSeatSelection('sess-none')).toEqual({})
  })

  it('clears only that session selection', () => {
    saveSeatSelection('sess-1', { 'p-seat': ['SEAT-1'] })
    saveSeatSelection('sess-2', { 'p-seat': ['SEAT-2'] })
    clearSeatSelection('sess-1')
    expect(loadSeatSelection('sess-1')).toEqual({})
    expect(loadSeatSelection('sess-2')).toEqual({ 'p-seat': ['SEAT-2'] })
  })

  it('ignores unreadable storage instead of throwing into the page', () => {
    sessionStorage.setItem('awn-seat-selection', '{not json')
    expect(loadSeatSelection('sess-1')).toEqual({})
  })

  it('ignores other tab state that is not keyed by product', () => {
    sessionStorage.setItem('awn-seat-selection', JSON.stringify({ 'sess-1': 'not-an-object' }))
    expect(loadSeatSelection('sess-1')).toEqual({})
  })
})
