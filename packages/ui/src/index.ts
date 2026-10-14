/**
 * AWN 基础 UI 组件包。
 *
 * 品牌令牌来源：design-system/awn/MASTER.md（高级黑 + 麦芒金）。
 * 组件在 Phase 02 起补齐：按钮、票档卡、价格标签、倒计时、状态胶囊等。
 */

export const AWN_COLORS = {
  ink: '#1C1917',
  ink2: '#57534E',
  gold: '#D59819',
  goldDeep: '#A16207',
  goldSoft: 'rgba(213, 152, 25, 0.14)',
  bg: '#FAFAF9',
  card: '#FFFFFF',
  border: '#E7E5E4',
} as const

/** 语义状态色（Dashboard 与交易状态胶囊共用） */
export const AWN_STATUS_COLORS = {
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
  info: '#2563EB',
} as const

export const AWN_FONT_STACK =
  "'PingFang SC','Hiragino Sans GB','Microsoft YaHei','Noto Sans SC',system-ui,sans-serif"

/** 品牌令牌 → CSS 自定义属性：网页端与管理后台在 :root 注入同一份映射 */
export const AWN_CSS_VARS: Record<string, string> = {
  '--awn-ink': AWN_COLORS.ink,
  '--awn-ink-2': AWN_COLORS.ink2,
  '--awn-gold': AWN_COLORS.gold,
  '--awn-gold-deep': AWN_COLORS.goldDeep,
  '--awn-gold-soft': AWN_COLORS.goldSoft,
  '--awn-bg': AWN_COLORS.bg,
  '--awn-card': AWN_COLORS.card,
  '--awn-border': AWN_COLORS.border,
}

export * from './themes'
