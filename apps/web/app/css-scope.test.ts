// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * CSS 分层契约（LAY-001）：
 * - 结构层：reset / 元素基础 / 页面骨架 / 字号令牌，只留在 apps/web/app/globals.css；
 * - 组件层与页面域：按域拆到 apps/web/app/styles/ 下的文件，globals.css 不再集中承载。
 *
 * 本测试两道门：
 * 1. 全量选择器不丢失——把 apps/web/app 下所有 .css 的顶层选择器求并集，
 *    必须等于拆分前的固定清单（154 组）。纯移动重构一旦误删/误改任何规则即红。
 * 2. 分层不回流——globals.css 只允许出现结构层选择器；
 *    组件/页面域选择器必须已迁出。拆分前 globals.css 承载全部 → 此断言为红（红先行）。
 */

// 拆分前（LAY-001 前置）globals.css 全部顶层选择器，顺序无关的集合。
const GOLDEN = [
  '*', 'body', 'h1', 'p', 'li', ':root',
  '.page', '.hero', '.logo-mark', '.logo-mark::before', '.logo-mark::after',
  '.logo-mark::before, .logo-mark::after', '.placeholder', '.placeholder a',
  '.topnav', '.topnav a', '.topnav a:hover',
  '.crumbs', '.crumbs a',
  '.section-head', '.section-head h1', '.section-head h2', '.muted',
  '.state', '.state--error', '.state--error a',
  '.filters', '.filters__row', '.chips', '.chip', '.chip--on',
  '.field', '.field--grow', '.field input, .field select',
  '.group', '.group h2, .group h3', '.group h3', '.count',
  '.card-grid', '.person-card', '.person-card:hover',
  '.person-card__body', '.person-card__body span',
  '.tags', '.tag', '.tag--quiet',
  '.star-photo', '.star-photo--hero', '.star-photo--placeholder',
  '.star-photo--placeholder svg', '.star-photo__reason', '.star-photo__badge',
  '.detail-head', '.detail-head__body', '.detail-head__body h1',
  '.detail-section', '.detail-section h2',
  '.entry-list', '.entry', '.entry:hover .entry__title',
  '.entry__title', '.entry__meta',
  '.row', '.row-between', '.badge', '.badge--success', '.badge--warning',
  '.badge--danger', '.num', '.price', '.price::before', '.price-yuan',
  '.title', '.subtitle', '.cols-2',
  '.event-card', '.event-card:hover', '.event-card__cover',
  '.event-card__cover span', '.event-card__body',
  '.session-card', '.session-card:hover',
  '.tier-row', '.tier-row:hover', '.tier-row[aria-disabled=\'true\']',
  '.tier-dot', '.tier-dot-1', '.tier-dot-2', '.tier-dot-3', '.tier-dot-4',
  '.tier-dot-5', '.tier-list',
  '.geo-row', '.geo-row svg', '.geo-row-block', '.geo-map', '.geo-map-frame',
  '.geo-map-image', '.geo-map-marker', '.geo-map-caption',
  '.page-head', '.page-head h1',
  '.btn', '.btn:disabled', '.btn--ghost', '.card', '.sunken', '.notice-bar',
  '.steps', '.step', '.step + .step::before', '.step .n',
  ".step[data-done='true']", ".step[data-done='true'] .n",
  '.purchase-grid', '.cart-summary',
  '.stepper', '.stepper button', '.stepper button:disabled', '.stepper span',
  '.hint', '.divider', '.table', '.table th, .table td', '.table th',
  '.table th.num, .table td.num',
  '.seatmap-svg', '.seatmap-stage', '.seatmap-stage-title',
  '.seatmap-stage-note', '.seatmap-section-title', '.seatmap-row-label',
  '.seat rect', '.seat rect.seat--free', '.seat rect.seat--occupied',
  '.seat rect.seat--selected', '.seat:focus-visible', '.seat-legend',
  '.seat-legend li', '.seat-dot', '.seat-dot--free', '.seat-dot--occupied',
  '.seat-dot--selected',
  '.theme-picker', '.theme-picker__head', '.theme-picker button',
  '.theme-picker button:hover', '.theme-picker button[aria-pressed="true"]',
  '.seatmap-view', '.seatmap-view button', '.seatmap-view button:hover',
  '.seatmap-view button[aria-pressed="true"]', '.seatmap-viewport',
  '.seatmap-viewport--tilted .seatmap-svg',
]

// 结构层专属：拆分后只允许留在 globals.css 的选择器。
const BASE_ONLY = [
  '*', 'body', 'h1', 'p', 'li', ':root',
  '.page', '.hero', '.logo-mark', '.logo-mark::before', '.logo-mark::after',
  '.logo-mark::before, .logo-mark::after', '.placeholder', '.placeholder a',
]

/** 提取单个 css 文件的顶层选择器（去注释、去媒体查询包装、压空白、排序去重）。 */
function selectorsOf(css: string): string[] {
  const noComments = css.replace(/\/\*[\s\S]*?\*\//g, '')
  const out = new Set<string>()
  for (const m of noComments.matchAll(/([^{}]+)\{/g)) {
    const raw = m[1].replace(/\s+/g, ' ').trim()
    if (!raw || raw.startsWith('@')) continue
    out.add(raw)
  }
  return [...out].sort()
}

/** apps/web/app 下所有 .css 文件（递归）。 */
function cssFiles(dir: string): string[] {
  const found: string[] = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue
      found.push(...cssFiles(p))
    } else if (entry.name.endsWith('.css')) {
      found.push(p)
    }
  }
  return found.sort()
}

// 测试文件与 globals.css 同目录（apps/web/app/），__dirname 即 app 目录。
const APP_DIR = __dirname

describe('CSS 分层契约（LAY-001）', () => {
  it('全量顶层选择器不因拆分丢失', () => {
    const files = cssFiles(APP_DIR)
    expect(files.length).toBeGreaterThan(0)
    const union = new Set<string>()
    for (const f of files) {
      for (const s of selectorsOf(readFileSync(f, 'utf8'))) union.add(s)
    }
    expect([...union].sort()).toEqual([...GOLDEN].sort())
  })

  it('globals.css 只保留结构层（组件/页面域已迁出）', () => {
    const globals = selectorsOf(
      readFileSync(join(APP_DIR, 'globals.css'), 'utf8'),
    )
    const baseSet = new Set(BASE_ONLY)
    const leaked = globals.filter((s) => !baseSet.has(s))
    // 红先行：拆分前 globals.css 承载全部 → leaked 非空 → 此断言失败。
    expect(leaked).toEqual([])
    // 结构层自身不得被拆空。
    for (const s of BASE_ONLY) {
      expect(globals).toContain(s)
    }
  })

  it('styles/ 目录存在且每个文件非空（拆出去的规则有归宿）', () => {
    const stylesDir = join(APP_DIR, 'styles')
    const files = cssFiles(stylesDir)
    expect(files.length).toBeGreaterThan(0)
    for (const f of files) {
      expect(readFileSync(f, 'utf8').replace(/\s/g, '')).not.toBe('')
    }
  })
})
