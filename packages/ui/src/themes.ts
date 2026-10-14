/**
 * AWN 多主题体系（docs/design/ui-system.md §1.3 §2 + prototypes/theme.css）。
 *
 * 边界与口径：
 * - 切换 = `<html data-theme="…">` 一个属性；布局、间距、字号、组件结构全部来自
 *   结构令牌 → 换主题不改布局（验收口径）。
 * - 每套主题只覆盖 `--t-*` 语义令牌（色板与场景说明取自 prototypes/theme.css，
 *   不在此发明新色值）；本包另提供 `--awn-*` → `--t-*` 的语义桥接，
 *   让已按 `--awn-*` 取色的既有组件跟随主题，不必全量改名。
 * - 主题数据是设计稿的机读形态：`default` 的桥接值必须与 AWN_COLORS 推出的
 *   AWN_CSS_VARS 一致（默认主题 ≡ 现行品牌外观）。
 */

export const AWN_THEME_NAMES = [
  'default',
  'dark',
  'cn-spring-festival',
  'cn-lantern',
  'cn-dragon-boat',
  'cn-mid-autumn',
  'cn-national-day',
  'season-spring',
  'season-summer',
  'season-autumn',
  'season-winter',
] as const

export type AWNThemeName = (typeof AWN_THEME_NAMES)[number]

/** 每套主题必须给出的语义令牌（prototypes/theme.css 的 18 个基础 --t-* 变量）。 */
export const AWN_THEME_TOKENS = [
  '--t-bg',
  '--t-sunken',
  '--t-card',
  '--t-fg',
  '--t-fg-muted',
  '--t-fg-subtle',
  '--t-brand',
  '--t-brand-strong',
  '--t-brand-soft',
  '--t-on-brand',
  '--t-hero-grad',
  '--t-border',
  '--t-border-strong',
  '--t-success',
  '--t-warning',
  '--t-danger',
  '--t-info',
  '--t-shadow',
] as const

export type AWNThemeVars = Record<(typeof AWN_THEME_TOKENS)[number], string> & {
  /** 节日 / 季节主题的辅助色（默认与暗色主题没有）。 */
  '--t-accent-2'?: string
}

/** 主题 → 适用场景（ui-system.md §2 主题清单）。 */
export const AWN_THEME_LABELS: Record<AWNThemeName, string> = {
  default: '全站默认（品牌黑金）',
  dark: '夜间模式 / 赛事',
  'cn-spring-festival': '春节档演出 / 贺岁',
  'cn-lantern': '元宵灯会 / 夜游',
  'cn-dragon-boat': '端午档 / 国风演出',
  'cn-mid-autumn': '中秋晚会 / 夜场',
  'cn-national-day': '国庆档',
  'season-spring': '春季音乐节 / 踏青档',
  'season-summer': '夏季音乐节 / 户外演出',
  'season-autumn': '秋季巡演 / 丰收档',
  'season-winter': '跨年演出 / 冬季档',
}

/** 主题 → 切换器短标签（prototypes/theme.js；完整场景说明见 AWN_THEME_LABELS）。 */
export const AWN_THEME_SHORT_LABELS: Record<AWNThemeName, string> = {
  default: '默认·品牌金',
  dark: '暗色',
  'cn-spring-festival': '春节',
  'cn-lantern': '元宵',
  'cn-dragon-boat': '端午',
  'cn-mid-autumn': '中秋',
  'cn-national-day': '国庆',
  'season-spring': '春',
  'season-summer': '夏',
  'season-autumn': '秋',
  'season-winter': '冬',
}

export const AWN_THEMES: Record<AWNThemeName, AWNThemeVars> = {
  default: {
    '--t-bg': '#fafaf9',
    '--t-sunken': '#f0efed',
    '--t-card': '#ffffff',
    '--t-fg': '#1c1917',
    '--t-fg-muted': '#57534e',
    '--t-fg-subtle': '#a8a29e',
    '--t-brand': '#d59819',
    '--t-brand-strong': '#a16207',
    '--t-brand-soft': '#f7edd4',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #1c1917 0%, #292524 55%, #453410 100%)',
    '--t-border': '#e7e5e4',
    '--t-border-strong': '#d6d3d1',
    '--t-success': '#16a34a',
    '--t-warning': '#d97706',
    '--t-danger': '#dc2626',
    '--t-info': '#2563eb',
    '--t-shadow': 'rgb(28 25 23 / 10%)',
  },
  dark: {
    '--t-bg': '#121110',
    '--t-sunken': '#1c1a19',
    '--t-card': '#201e1c',
    '--t-fg': '#f5f5f4',
    '--t-fg-muted': '#a8a29e',
    '--t-fg-subtle': '#78716c',
    '--t-brand': '#e3b341',
    '--t-brand-strong': '#d59819',
    '--t-brand-soft': '#3a2f14',
    '--t-on-brand': '#1c1917',
    '--t-hero-grad': 'linear-gradient(135deg, #0c0a09 0%, #1c1917 55%, #3a2f14 100%)',
    '--t-border': '#35302c',
    '--t-border-strong': '#4b443f',
    '--t-success': '#4ade80',
    '--t-warning': '#fbbf24',
    '--t-danger': '#f87171',
    '--t-info': '#60a5fa',
    '--t-shadow': 'rgb(0 0 0 / 45%)',
  },
  'cn-spring-festival': {
    '--t-bg': '#fff7f2',
    '--t-sunken': '#fdeee4',
    '--t-card': '#ffffff',
    '--t-fg': '#3d0c0c',
    '--t-fg-muted': '#8c4a3c',
    '--t-fg-subtle': '#c09a8e',
    '--t-brand': '#c8102e',
    '--t-brand-strong': '#9e0c24',
    '--t-brand-soft': '#fde3da',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #7f0d1e 0%, #c8102e 60%, #e8842c 100%)',
    '--t-border': '#f3d9cd',
    '--t-border-strong': '#e5b8a6',
    '--t-success': '#16a34a',
    '--t-warning': '#e8842c',
    '--t-danger': '#b91c1c',
    '--t-info': '#9333ea',
    '--t-accent-2': '#e8b33c',
    '--t-shadow': 'rgb(127 13 30 / 12%)',
  },
  'cn-lantern': {
    '--t-bg': '#fdf3ec',
    '--t-sunken': '#f7e4d8',
    '--t-card': '#fffaf6',
    '--t-fg': '#401434',
    '--t-fg-muted': '#7c4460',
    '--t-fg-subtle': '#b08a9c',
    '--t-brand': '#b3407a',
    '--t-brand-strong': '#8f2d5f',
    '--t-brand-soft': '#fadbe8',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #2b0b22 0%, #6b1f47 55%, #d4691f 100%)',
    '--t-border': '#f0d5c8',
    '--t-border-strong': '#dfb4a0',
    '--t-success': '#16a34a',
    '--t-warning': '#d4691f',
    '--t-danger': '#c0392b',
    '--t-info': '#7c3aed',
    '--t-accent-2': '#ffb347',
    '--t-shadow': 'rgb(64 20 52 / 12%)',
  },
  'cn-dragon-boat': {
    '--t-bg': '#f4faf4',
    '--t-sunken': '#e6f2e4',
    '--t-card': '#ffffff',
    '--t-fg': '#14311c',
    '--t-fg-muted': '#3f6b4a',
    '--t-fg-subtle': '#8fa896',
    '--t-brand': '#1f7a45',
    '--t-brand-strong': '#155c33',
    '--t-brand-soft': '#ddefdf',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #0e2f1b 0%, #1f7a45 60%, #86a12d 100%)',
    '--t-border': '#d5e8d6',
    '--t-border-strong': '#b3d4b6',
    '--t-success': '#15803d',
    '--t-warning': '#b8860b',
    '--t-danger': '#c2410c',
    '--t-info': '#0e7490',
    '--t-accent-2': '#d9b23c',
    '--t-shadow': 'rgb(20 49 28 / 12%)',
  },
  'cn-mid-autumn': {
    '--t-bg': '#f5f7fc',
    '--t-sunken': '#e8edf8',
    '--t-card': '#ffffff',
    '--t-fg': '#14203c',
    '--t-fg-muted': '#45537a',
    '--t-fg-subtle': '#8e97b5',
    '--t-brand': '#2c4a8a',
    '--t-brand-strong': '#1f3768',
    '--t-brand-soft': '#dde6f7',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #0a1128 0%, #1c2c56 55%, #8c7853 100%)',
    '--t-border': '#d7deee',
    '--t-border-strong': '#b4c1de',
    '--t-success': '#15803d',
    '--t-warning': '#b45309',
    '--t-danger': '#b91c1c',
    '--t-info': '#1d4ed8',
    '--t-accent-2': '#d9c58c',
    '--t-shadow': 'rgb(20 32 60 / 12%)',
  },
  'cn-national-day': {
    '--t-bg': '#fff8f0',
    '--t-sunken': '#fdeed9',
    '--t-card': '#ffffff',
    '--t-fg': '#4a1d05',
    '--t-fg-muted': '#8a4a1c',
    '--t-fg-subtle': '#c29670',
    '--t-brand': '#de2910',
    '--t-brand-strong': '#b3210c',
    '--t-brand-soft': '#fde1d7',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #8f1d06 0%, #de2910 55%, #f7b32b 100%)',
    '--t-border': '#f6ddc4',
    '--t-border-strong': '#e8c29c',
    '--t-success': '#15803d',
    '--t-warning': '#f7b32b',
    '--t-danger': '#b91c1c',
    '--t-info': '#1d4ed8',
    '--t-accent-2': '#ffde00',
    '--t-shadow': 'rgb(143 29 6 / 12%)',
  },
  'season-spring': {
    '--t-bg': '#f7fbf5',
    '--t-sunken': '#eaf5e8',
    '--t-card': '#ffffff',
    '--t-fg': '#243327',
    '--t-fg-muted': '#55705a',
    '--t-fg-subtle': '#9db4a3',
    '--t-brand': '#4f9d69',
    '--t-brand-strong': '#3a7d51',
    '--t-brand-soft': '#e0f1e2',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #24352a 0%, #4f9d69 70%, #f2b8c6 100%)',
    '--t-border': '#dceadb',
    '--t-border-strong': '#bcd4bf',
    '--t-success': '#16a34a',
    '--t-warning': '#d97706',
    '--t-danger': '#dc2626',
    '--t-info': '#0891b2',
    '--t-accent-2': '#f2b8c6',
    '--t-shadow': 'rgb(36 51 39 / 10%)',
  },
  'season-summer': {
    '--t-bg': '#f3fafd',
    '--t-sunken': '#e3f2fa',
    '--t-card': '#ffffff',
    '--t-fg': '#0e2a36',
    '--t-fg-muted': '#33606f',
    '--t-fg-subtle': '#7f9faa',
    '--t-brand': '#0e7490',
    '--t-brand-strong': '#0b5a70',
    '--t-brand-soft': '#d7eef6',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #062430 0%, #0e7490 65%, #f7c948 100%)',
    '--t-border': '#d3e8f0',
    '--t-border-strong': '#a8cfdd',
    '--t-success': '#16a34a',
    '--t-warning': '#f7c948',
    '--t-danger': '#dc2626',
    '--t-info': '#2563eb',
    '--t-accent-2': '#f7c948',
    '--t-shadow': 'rgb(14 42 54 / 10%)',
  },
  'season-autumn': {
    '--t-bg': '#fbf7f2',
    '--t-sunken': '#f3e9dd',
    '--t-card': '#ffffff',
    '--t-fg': '#362718',
    '--t-fg-muted': '#6e563c',
    '--t-fg-subtle': '#ab9070',
    '--t-brand': '#c2661d',
    '--t-brand-strong': '#9c4f14',
    '--t-brand-soft': '#f8e5cf',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #2e1c0c 0%, #c2661d 65%, #e8b33c 100%)',
    '--t-border': '#ecdbc6',
    '--t-border-strong': '#d8bd9e',
    '--t-success': '#15803d',
    '--t-warning': '#d97706',
    '--t-danger': '#b91c1c',
    '--t-info': '#0369a1',
    '--t-accent-2': '#e8b33c',
    '--t-shadow': 'rgb(54 39 24 / 10%)',
  },
  'season-winter': {
    '--t-bg': '#f5f8fb',
    '--t-sunken': '#e9eff5',
    '--t-card': '#ffffff',
    '--t-fg': '#1a2530',
    '--t-fg-muted': '#4b5f70',
    '--t-fg-subtle': '#90a3b3',
    '--t-brand': '#38708f',
    '--t-brand-strong': '#2a566f',
    '--t-brand-soft': '#ddebf3',
    '--t-on-brand': '#ffffff',
    '--t-hero-grad': 'linear-gradient(135deg, #0d1b26 0%, #38708f 70%, #cfe3ee 100%)',
    '--t-border': '#dbe5ee',
    '--t-border-strong': '#b8cbd9',
    '--t-success': '#15803d',
    '--t-warning': '#b45309',
    '--t-danger': '#b91c1c',
    '--t-info': '#0369a1',
    '--t-accent-2': '#a5d8e6',
    '--t-shadow': 'rgb(26 37 48 / 10%)',
  },
}

/** 语义桥接：既有组件按 --awn-* 取色，主题切换后随 --t-* 联动（不改组件命名）。 */
export const AWN_TOKEN_BRIDGE: Record<string, string> = {
  '--awn-bg': 'var(--t-bg)',
  '--awn-card': 'var(--t-card)',
  '--awn-ink': 'var(--t-fg)',
  '--awn-ink-2': 'var(--t-fg-muted)',
  '--awn-gold': 'var(--t-brand)',
  '--awn-gold-deep': 'var(--t-brand-strong)',
  '--awn-gold-soft': 'var(--t-brand-soft)',
  '--awn-border': 'var(--t-border)',
}

function declarations(themeName: AWNThemeName): string {
  const lines = Object.entries(AWN_THEMES[themeName]).map(
    ([name, value]) => `  ${name}: ${value};`,
  )
  const bridge = Object.entries(AWN_TOKEN_BRIDGE).map(
    ([name, value]) => `  ${name}: ${value};`,
  )
  return [...lines, ...bridge].join('\n')
}

/** 单套主题的 CSS 规则（`[data-theme="…"]` 块，含 --t-* 全量与 --awn-* 桥接）。 */
export function themeCss(themeName: AWNThemeName): string {
  return `[data-theme="${themeName}"] {\n${declarations(themeName)}\n}`
}

/** 全部主题的 CSS（layout 注入一次，切换只改 data-theme 属性）。 */
export function allThemesCss(): string {
  return AWN_THEME_NAMES.map(themeCss).join('\n\n')
}

/** 切换器的 localStorage 键（与原型的记忆口径一致）。 */
export const AWN_THEME_STORAGE_KEY = 'awn-theme'

function isThemeName(value: string): value is AWNThemeName {
  return (AWN_THEME_NAMES as readonly string[]).includes(value)
}

/** 设置 `<html data-theme>`；SSR / 无 DOM 环境为 no-op。未知主题直接忽略。 */
export function applyTheme(themeName: string): void {
  if (typeof document === 'undefined' || !isThemeName(themeName)) {
    return
  }
  document.documentElement.dataset['theme'] = themeName
}

/** 读记忆的主题；缺失或非法一律回退 default（不信任本地存储内容）。 */
export function readStoredTheme(storage?: {
  getItem(key: string): string | null
}): AWNThemeName {
  try {
    const stored = (storage ?? globalThis.localStorage)?.getItem(AWN_THEME_STORAGE_KEY) ?? null
    return stored !== null && isThemeName(stored) ? stored : 'default'
  } catch {
    return 'default'
  }
}

/** 记忆主题；SSR / 无存储环境为 no-op。 */
export function storeTheme(themeName: string, storage?: {
  setItem(key: string, value: string): void
}): void {
  if (!isThemeName(themeName)) {
    return
  }
  try {
    (storage ?? globalThis.localStorage)?.setItem(AWN_THEME_STORAGE_KEY, themeName)
  } catch {
    // 存储不可用（隐私模式等）：只影响记忆，不影响本次切换
  }
}
