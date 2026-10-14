'use client'

import { useEffect, useState } from 'react'
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
 * SSR 阶段不渲染（水合前用服务端固定 data-theme="default"，避免面板状态闪变）。
 */
export function ThemeSwitcher() {
  const [theme, setTheme] = useState<AWNThemeName | null>(null)

  useEffect(() => {
    const stored = readStoredTheme()
    applyTheme(stored)
    setTheme(stored)
  }, [])

  if (theme === null) return null

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
