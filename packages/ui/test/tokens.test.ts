import { describe, expect, it } from 'vitest'
import { AWN_COLORS, AWN_CSS_VARS, AWN_FONT_STACK, AWN_STATUS_COLORS } from '../src/index'

/**
 * 品牌令牌契约：与 design-system/awn/MASTER.md（高级黑 + 麦芒金）及
 * docs/design/ui-system.md 保持一致；网页端与管理后台共用。
 */
describe('AWN_COLORS', () => {
  it('uses the brand gold from the design system', () => {
    expect(AWN_COLORS.gold).toBe('#D59819')
  })

  it('provides the ink scale and surface tokens', () => {
    expect(AWN_COLORS.ink).toBe('#1C1917')
    expect(AWN_COLORS.bg).toBe('#FAFAF9')
    expect(AWN_COLORS.card).toBe('#FFFFFF')
    expect(AWN_COLORS.border).toBe('#E7E5E4')
  })

  it('keeps the deep gold variant darker than the base gold', () => {
    expect(AWN_COLORS.goldDeep).toBe('#A16207')
  })
})

describe('AWN_STATUS_COLORS', () => {
  it('covers the four semantic statuses', () => {
    expect(Object.keys(AWN_STATUS_COLORS).sort()).toEqual(
      ['danger', 'info', 'success', 'warning'].sort(),
    )
    expect(AWN_STATUS_COLORS.success).toBe('#16A34A')
    expect(AWN_STATUS_COLORS.danger).toBe('#DC2626')
  })
})

describe('AWN_FONT_STACK', () => {
  it('leads with CJK system fonts before the generic fallback', () => {
    expect(AWN_FONT_STACK.startsWith("'PingFang SC'")).toBe(true)
    expect(AWN_FONT_STACK).toContain('Microsoft YaHei')
    expect(AWN_FONT_STACK.endsWith('system-ui,sans-serif')).toBe(true)
  })
})

describe('AWN_CSS_VARS', () => {
  it('maps every brand color to its :root custom property', () => {
    expect(AWN_CSS_VARS).toEqual({
      '--awn-ink': AWN_COLORS.ink,
      '--awn-ink-2': AWN_COLORS.ink2,
      '--awn-gold': AWN_COLORS.gold,
      '--awn-gold-deep': AWN_COLORS.goldDeep,
      '--awn-gold-soft': AWN_COLORS.goldSoft,
      '--awn-bg': AWN_COLORS.bg,
      '--awn-card': AWN_COLORS.card,
      '--awn-border': AWN_COLORS.border,
    })
  })

  it('keeps the shared mapping as the single source for the color tokens', () => {
    expect(Object.keys(AWN_CSS_VARS)).toHaveLength(Object.keys(AWN_COLORS).length)
  })
})
