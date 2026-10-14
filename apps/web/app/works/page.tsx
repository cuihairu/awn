'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { listWorks, type WorkType } from '@awn/api-client'
import {
  WORK_TYPE_LABEL_ZH,
  WORK_TYPE_ORDER,
  formatYear,
  workTypeLabel,
} from '../../lib/star-labels'

/**
 * 作品列表 /works（docs/design/star-catalog.md §2.1）。
 * 检索范围：标题 + 类型过滤；不做全文与拼音模糊。
 * 入口：主导航「明星图鉴」→ 作品列表，或人物详情的作品行直达。
 */
export default function WorksPage() {
  const [works, setWorks] = useState<Awaited<ReturnType<typeof listWorks>>>([])
  const [type, setType] = useState<WorkType | ''>('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    listWorks({ type: type || undefined, q: keyword || undefined })
      .then((data) => {
        if (!alive) return
        setWorks(data)
        setFailed(false)
      })
      .catch(() => {
        if (alive) setFailed(true)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [type, keyword])

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">首页</Link>
        <span aria-hidden>/</span>
        <Link href="/stars">明星图鉴</Link>
        <span aria-hidden>/</span>
        <span>作品</span>
      </nav>

      <header className="section-head">
        <h1>作品</h1>
        <p className="muted">按类型浏览作品，或搜索作品名。</p>
      </header>

      <section className="filters" aria-label="作品筛选">
        <div className="chips" role="group" aria-label="作品类型">
          <button
            type="button"
            className={type === '' ? 'chip chip--on' : 'chip'}
            aria-pressed={type === ''}
            onClick={() => setType('')}
          >
            全部
          </button>
          {WORK_TYPE_ORDER.map((code) => (
            <button
              key={code}
              className={type === code ? 'chip chip--on' : 'chip'}
              aria-pressed={type === code}
              onClick={() => setType(code)}
            >
              {WORK_TYPE_LABEL_ZH[code]}
            </button>
          ))}
        </div>

        <div className="filters__row">
          <label className="field field--grow">
            <span>搜索</span>
            <input
              type="search"
              value={keyword}
              placeholder="作品名"
              onChange={(e) => setKeyword(e.target.value)}
            />
          </label>
        </div>
      </section>

      {loading && <p className="state">加载中…</p>}
      {failed && (
        <p className="state state--error">作品数据加载失败，请稍后重试。</p>
      )}

      {!loading && !failed && works.length === 0 && (
        <p className="state">没有匹配的作品，换个筛选条件试试。</p>
      )}

      {!loading && !failed && works.length > 0 && (
        <ul className="entry-list">
          {works.map((work) => (
            <li key={work.id}>
              <Link className="entry" href={`/works/${work.id}`}>
                <span className="entry__title">{work.title}</span>
                <span className="entry__meta">
                  {workTypeLabel(work.type)} · {formatYear(work.year)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
