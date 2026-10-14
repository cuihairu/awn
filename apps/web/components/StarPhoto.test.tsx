// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { StarPhoto } from './StarPhoto'

/**
 * 素材红线（docs/design/star-catalog.md §3）前端契约：
 * 无图 → 「未收录图片」占位；加载失败 → 「图片加载失败」，两者必须区分；
 * 占位一律带「示意图」角标；任何状态下都不渲染生成人脸。
 */

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let container: HTMLDivElement | null = null
let root: Root | null = null

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  container = null
  root = null
})

function render(element: React.ReactNode) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(element))
  return container
}

describe('StarPhoto', () => {
  it('renders the unavailable placeholder when there is no photo', () => {
    const node = render(<StarPhoto src={null} name="沈流舒" />)
    expect(node.querySelector('img')).toBeNull()
    expect(node.querySelector('.star-photo__reason')?.textContent).toBe('未收录图片')
    expect(node.querySelector('.star-photo__badge')?.textContent).toBe('示意图')
    expect(node.querySelector('.star-photo')?.getAttribute('data-state')).toBe('unavailable')
  })

  it('treats undefined src the same as null', () => {
    const node = render(<StarPhoto src={undefined} name="沈流舒" />)
    expect(node.querySelector('.star-photo__reason')?.textContent).toBe('未收录图片')
  })

  it('renders the image when a licensed photo url is present', () => {
    const node = render(
      <StarPhoto src="https://example.com/licensed.jpg" name="沈流舒" />,
    )
    const img = node.querySelector('img')
    expect(img?.getAttribute('src')).toBe('https://example.com/licensed.jpg')
    expect(img?.getAttribute('alt')).toBe('沈流舒的照片')
  })

  it('switches to the error placeholder when the image fails to load', () => {
    const node = render(
      <StarPhoto src="https://example.com/broken.jpg" name="沈流舒" />,
    )
    const img = node.querySelector('img')
    expect(img).not.toBeNull()
    act(() => {
      img?.dispatchEvent(new Event('error'))
    })
    expect(node.querySelector('img')).toBeNull()
    expect(node.querySelector('.star-photo__reason')?.textContent).toBe('图片加载失败')
    expect(node.querySelector('.star-photo')?.getAttribute('data-state')).toBe('error')
  })

  it('marks placeholders as 示意图 so they are never taken for real photos', () => {
    const node = render(<StarPhoto src={null} name="沈流舒" variant="hero" />)
    expect(node.querySelector('.star-photo--hero')).not.toBeNull()
    expect(node.querySelector('.star-photo__badge')?.textContent).toBe('示意图')
  })
})
