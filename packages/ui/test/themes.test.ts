// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import {
  AWN_COLORS,
  AWN_CSS_VARS,
  AWN_THEMES,
  AWN_THEME_LABELS,
  AWN_THEME_NAMES,
  AWN_THEME_TOKENS,
  allThemesCss,
  applyTheme,
  readStoredTheme,
  storeTheme,
  themeCss,
} from '../src/index'

/**
 * 多主题契约（docs/design/ui-system.md §1.3 §2 + prototypes/theme.css）：
 * 切换 = data-theme 一个属性；每套主题只覆盖 --t-* 语义令牌；
 * 换主题不改布局（结构令牌主题无关）；default 桥接值 ≡ 现行品牌令牌。
 */

describe('AWN_THEME_NAMES', () => {
  it('covers the 11 themes from the ui-system catalogue', () => {
    expect([...AWN_THEME_NAMES].sort()).toEqual(
      [
        'cn-dragon-boat',
        'cn-lantern',
        'cn-mid-autumn',
        'cn-national-day',
        'cn-spring-festival',
        'dark',
        'default',
        'season-autumn',
        'season-spring',
        'season-summer',
        'season-winter',
      ].sort(),
    )
  })

  it('labels every theme with its intended scenario', () => {
    for (const name of AWN_THEME_NAMES) {
      expect(AWN_THEME_LABELS[name]).toBeTruthy()
    }
  })
})

describe('AWN_THEMES', () => {
  it('gives every theme the full semantic token set', () => {
    for (const name of AWN_THEME_NAMES) {
      const theme = AWN_THEMES[name]
      for (const token of AWN_THEME_TOKENS) {
        expect(theme[token], `${name} missing ${token}`).toBeTruthy()
      }
    }
  })

  it('keeps the default theme identical to the brand tokens it bridges', () => {
    const expected: Record<string, string> = {
      '--awn-bg': AWN_COLORS.bg,
      '--awn-card': AWN_COLORS.card,
      '--awn-ink': AWN_COLORS.ink,
      '--awn-ink-2': AWN_COLORS.ink2,
      '--awn-gold': AWN_COLORS.gold,
      '--awn-gold-deep': AWN_COLORS.goldDeep,
      '--awn-border': AWN_COLORS.border,
    }
    const lowered = (value: string | undefined) => (value ?? '').toLowerCase()
    for (const [name, value] of Object.entries(expected)) {
      expect(lowered(AWN_CSS_VARS[name])).toBe(lowered(value))
      // 桥接把 --awn-* 指到同一语义的 --t-*，default 主题即现行品牌外观
      const bridgeSource = {
        '--awn-bg': '--t-bg',
        '--awn-card': '--t-card',
        '--awn-ink': '--t-fg',
        '--awn-ink-2': '--t-fg-muted',
        '--awn-gold': '--t-brand',
        '--awn-gold-deep': '--t-brand-strong',
        '--awn-border': '--t-border',
      }[name]
      expect(lowered(AWN_THEMES.default[bridgeSource as keyof typeof AWN_THEMES.default])).toBe(
        lowered(value),
      )
    }
  })

  it('keeps dark theme readable with light foreground on a near-black ground', () => {
    expect(AWN_THEMES.dark['--t-bg']).toBe('#121110')
    expect(AWN_THEMES.dark['--t-fg']).toBe('#f5f5f4')
  })
})

describe('themeCss', () => {
  it('emits a data-theme selector with tokens and the awn bridge', () => {
    const css = themeCss('dark')
    expect(css).toContain('[data-theme="dark"]')
    expect(css).toContain('--t-bg: #121110;')
    expect(css).toContain('--awn-gold: var(--t-brand);')
  })

  it('emits one block per theme', () => {
    const css = allThemesCss()
    for (const name of AWN_THEME_NAMES) {
      expect(css).toContain(`[data-theme="${name}"]`)
    }
    expect(css.match(/\[data-theme=/g)).toHaveLength(AWN_THEME_NAMES.length)
  })
})

describe('theme persistence helpers', () => {
  it('falls back to default for missing or unknown stored values', () => {
    expect(readStoredTheme({ getItem: () => null })).toBe('default')
    expect(readStoredTheme({ getItem: () => 'neon-rave' })).toBe('default')
    expect(readStoredTheme({ getItem: () => 'dark' })).toBe('dark')
  })

  it('survives a throwing storage', () => {
    expect(
      readStoredTheme({
        getItem: () => {
          throw new Error('storage blocked')
        },
      }),
    ).toBe('default')
    expect(() =>
      storeTheme('dark', {
        setItem: () => {
          throw new Error('storage blocked')
        },
      }),
    ).not.toThrow()
  })

  it('applies the data-theme attribute in a DOM and ignores unknown names', () => {
    applyTheme('cn-mid-autumn')
    expect(document.documentElement.dataset['theme']).toBe('cn-mid-autumn')
    applyTheme('neon-rave')
    expect(document.documentElement.dataset['theme']).toBe('cn-mid-autumn')
    applyTheme('default')
    expect(document.documentElement.dataset['theme']).toBe('default')
  })
})
