import { useState } from 'react'
import {
  AWN_THEME_LABELS,
  AWN_THEME_NAMES,
  AWN_THEME_SHORT_LABELS,
  applyTheme,
  readStoredTheme,
  storeTheme,
  type AWNThemeName,
} from '@awn/ui'

/**
 * 主题切换器（原型 components.css .theme-picker：右下角悬浮按钮面板）。
 *
 * 只改 `<html data-theme>` 令牌，不动布局；选择写入 localStorage 记忆。
 * admin 为纯 CSR（无 SSR），main.tsx 已在渲染前应用记忆主题，这里直接读。
 */
export function ThemeSwitcher() {
  const [theme, setTheme] = useState<AWNThemeName>(() => readStoredTheme())

  return (
    <div className="theme-picker" role="group" aria-label="主题切换">
      <div className="theme-picker__head">主题 / theme</div>
      {AWN_THEME_NAMES.map((name) => (
        <button
          key={name}
          type="button"
          title={AWN_THEME_LABELS[name]}
          aria-pressed={name === theme}
          onClick={() => {
            applyTheme(name)
            storeTheme(name)
            setTheme(name)
          }}
        >
          {AWN_THEME_SHORT_LABELS[name]}
        </button>
      ))}
    </div>
  )
}
