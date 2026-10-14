// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AWN_THEME_STORAGE_KEY, AWN_THEMES } from '@awn/ui'
import { ThemeSwitcher } from './ThemeSwitcher'

/**
 * 切换器契约（原型 components.css .theme-picker + theme.js）：
 * 11 套主题全部可点，点击 = 改 data-theme + 写 localStorage 记忆，
 * 当前主题 aria-pressed 唯一为真。
 */

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let container: HTMLDivElement | null = null
let root: Root | null = null

beforeEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  container = null
  root = null
})

function render() {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(<ThemeSwitcher />))
  return container
}

describe('ThemeSwitcher', () => {
  it('renders one pressed button per catalogue theme', () => {
    const node = render()
    const buttons = [...node.querySelectorAll('button')]
    expect(buttons).toHaveLength(Object.keys(AWN_THEMES).length)
    expect(buttons.filter((b) => b.getAttribute('aria-pressed') === 'true')).toHaveLength(1)
    expect(buttons.find((b) => b.textContent === '默认·品牌金')?.getAttribute('aria-pressed')).toBe(
      'true',
    )
  })

  it('applies the data-theme attribute and remembers the choice', () => {
    const node = render()
    const autumn = [...node.querySelectorAll('button')].find(
      (b) => b.textContent === '秋',
    ) as HTMLButtonElement
    act(() => autumn.dispatchEvent(new MouseEvent('click', { bubbles: true })))
    expect(document.documentElement.dataset['theme']).toBe('season-autumn')
    expect(localStorage.getItem(AWN_THEME_STORAGE_KEY)).toBe('season-autumn')
    expect(autumn.getAttribute('aria-pressed')).toBe('true')
  })

  it('restores the stored theme on mount', () => {
    localStorage.setItem(AWN_THEME_STORAGE_KEY, 'dark')
    render()
    expect(document.documentElement.dataset['theme']).toBe('dark')
  })
})
