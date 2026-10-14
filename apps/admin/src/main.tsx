import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { allThemesCss, applyTheme, readStoredTheme } from '@awn/ui'
import App from './App'
import './index.css'

// 主题令牌（含 --awn-* 桥接）：渲染前同步注入，避免首帧无色或闪变；
// 记忆主题同步应用，切换只改 data-theme 属性（index.css 注释的单一来源即此处）。
applyTheme(readStoredTheme())
const themeStyle = document.createElement('style')
themeStyle.textContent = allThemesCss()
document.head.appendChild(themeStyle)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
