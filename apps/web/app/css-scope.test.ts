// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * CSS 分层契约（LAY-001）：
 * - 结构层：reset / 元素基础 / 页面骨架 / 字号令牌，只留在 apps/web/app/globals.css；
 * - 组件层与页面域：按域拆到 apps/web/app/styles/ 下的文件，globals.css 不再集中承载。
 *
 * 本测试四道门：
 * 1. 声明级全量契约——每个 .css 的全部规则（选择器 + 归一化声明串，按源码顺序；
 *    @media/@supports 内层以「<条件> ▸ <选择器>」编码，旧版选择器集合断言对它们是盲区）
 *    逐条等于快照 css-scope.snapshot.json。旧版只比选择器集合，规则体与层叠顺序
 *    漂移检测不到（2026-11-12 深审 296 条）。
 *    更新真值：UPDATE_SNAPSHOT=1 pnpm vitest run apps/web/app/css-scope.test.ts
 * 2. 跨文件层叠契约——layout.tsx 样式导入顺序固定
 *    globals → chrome → catalog → events → checkout（后文件层叠覆盖前文件）。
 * 3. 分层不回流——globals.css 只允许出现结构层选择器；
 *    组件/页面域选择器必须已迁出。拆分前 globals.css 承载全部 → 此断言为红（红先行）。
 * 4. styles/ 目录存在且每个文件非空（拆出去的规则有归宿）。
 */

// 结构层专属：拆分后只允许留在 globals.css 的选择器。
const BASE_ONLY = [
  '*', 'body', 'h1', 'p', 'li', ':root',
  '.page', '.hero', '.logo-mark', '.logo-mark::before', '.logo-mark::after',
  '.logo-mark::before, .logo-mark::after', '.placeholder', '.placeholder a',
]

type Rule = { s: string; d: string }
type FileSnapshot = { file: string; rules: Rule[] }

const APP_DIR = __dirname
const SNAPSHOT_PATH = join(APP_DIR, 'css-scope.snapshot.json')

/** apps/web/app 下所有 .css 文件（递归、跳 node_modules/隐藏目录，绝对路径有序）。 */
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

/** 去注释。 */
function stripComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, '')
}

/** 归一化声明块：分号切分、逐条压空白去首尾、保留源码顺序重组（简写覆盖语义不丢序）。 */
function normalizeDecls(body: string): string {
  return body
    .split(';')
    .map((d) => d.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
    .join('; ')
}

/**
 * 解析一段 css 为有序规则表。花括号配对读块（@media 等嵌套块不误切）；
 * @media/@supports 条件块递归展开、内层选择器带条件前缀；其余 @ 规则
 * （如 @keyframes）整块以内文归一化记录。输出顺序 = 源码层叠顺序。
 */
function parseRules(css: string, context = ''): Rule[] {
  const rules: Rule[] = []
  const text = stripComments(css)
  let i = 0
  while (i < text.length) {
    const open = text.indexOf('{', i)
    if (open === -1) break
    const prelude = text.slice(i, open).replace(/\s+/g, ' ').trim()
    let depth = 1
    let j = open + 1
    while (j < text.length && depth > 0) {
      if (text[j] === '{') depth++
      else if (text[j] === '}') depth--
      j++
    }
    const body = text.slice(open + 1, j - 1)
    if (prelude.startsWith('@media') || prelude.startsWith('@supports')) {
      rules.push(...parseRules(body, context ? `${context} ▸ ${prelude}` : prelude))
    } else if (prelude) {
      const sel = context ? `${context} ▸ ${prelude}` : prelude
      rules.push({ s: sel, d: normalizeDecls(body) })
    }
    i = j
  }
  return rules
}

function currentSnapshots(): FileSnapshot[] {
  return cssFiles(APP_DIR).map((abs) => ({
    file: relative(APP_DIR, abs),
    rules: parseRules(readFileSync(abs, 'utf8')),
  }))
}

describe('CSS 分层契约（LAY-001）', () => {
  it('声明级全量契约：规则体与层叠顺序不漂移', () => {
    const current = currentSnapshots()
    expect(current.length).toBe(5)
    if (process.env.UPDATE_SNAPSHOT) {
      writeFileSync(SNAPSHOT_PATH, JSON.stringify(current, null, 2) + '\n')
      return
    }
    const expected = JSON.parse(readFileSync(SNAPSHOT_PATH, 'utf8')) as FileSnapshot[]
    expect(current).toEqual(expected)
  })

  it('layout.tsx 样式导入顺序固定（跨文件层叠契约）', () => {
    const layout = readFileSync(join(APP_DIR, 'layout.tsx'), 'utf8')
    const order = [...layout.matchAll(/import\s+['"]([^'"]+\.css)['"]/g)].map((m) => m[1])
    expect(order).toEqual([
      './globals.css',
      './styles/chrome.css',
      './styles/catalog.css',
      './styles/events.css',
      './styles/checkout.css',
    ])
  })

  it('globals.css 只保留结构层（组件/页面域已迁出）', () => {
    const globals = parseRules(readFileSync(join(APP_DIR, 'globals.css'), 'utf8')).map(
      (r) => r.s,
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
