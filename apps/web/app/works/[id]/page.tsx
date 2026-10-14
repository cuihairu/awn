'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { ApiError, getWork, type WorkDetail } from '@awn/api-client'
import {
  WORK_TYPE_LABEL_ZH,
  formatYear,
  groupCastByRole,
} from '../../../lib/star-labels'

/**
 * 作品详情 /works/{id}（docs/design/star-catalog.md §2.1）。
 * 人员表按身份分组（groupCastByRole），每行跳人物详情——与人物详情互跳。
 * 公开面只返回 PUBLISHED：草稿与不存在一律 404 信封。
 */
export default function WorkDetailPage() {
  const params = useParams<{ id: string }>()
  const workId = params.id

  const [detail, setDetail] = useState<WorkDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    getWork(workId)
      .then((data) => {
        if (alive) setDetail(data)
      })
      .catch((err) => {
        if (!alive) return
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
        } else {
          setFailed(true)
        }
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [workId])

  const groups = useMemo(
    () => (detail ? groupCastByRole(detail.cast) : []),
    [detail],
  )

  if (loading) {
    return (
      <main className="page">
        <p className="state">加载中…</p>
      </main>
    )
  }

  if (notFound) {
    return (
      <main className="page">
        <nav className="crumbs">
          <Link href="/">首页</Link>
          <span aria-hidden>/</span>
          <Link href="/stars">明星图鉴</Link>
          <span aria-hidden>/</span>
          <span>作品</span>
        </nav>
        <p className="state state--error">
          作品不存在或未发布。<Link href="/stars">返回图鉴列表</Link>
        </p>
      </main>
    )
  }

  if (failed || !detail) {
    return (
      <main className="page">
        <p className="state state--error">作品数据加载失败，请稍后重试。</p>
      </main>
    )
  }

  const { work } = detail

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">首页</Link>
        <span aria-hidden>/</span>
        <Link href="/stars">明星图鉴</Link>
        <span aria-hidden>/</span>
        <Link href="/works">作品</Link>
        <span aria-hidden>/</span>
        <span>{work.title}</span>
      </nav>

      <header className="detail-head detail-head--work">
        <div className="detail-head__body">
          <h1>{work.title}</h1>
          <p className="tags">
            <span className="tag">{WORK_TYPE_LABEL_ZH[work.type]}</span>
            <span className="tag tag--quiet">{formatYear(work.year)}</span>
          </p>
        </div>
      </header>

      {work.description && (
        <section className="detail-section">
          <h2>简介</h2>
          <p>{work.description}</p>
        </section>
      )}

      <section className="detail-section">
        <h2>
          参与人员
          <span className="count">{detail.cast.length}</span>
        </h2>
        {groups.length === 0 && <p className="muted">暂无已发布的人员记录。</p>}
        {groups.map((group) => (
          <section key={group.key} className="group">
            <h3>{group.label}</h3>
            <ul className="entry-list">
              {group.items.map((entry) => (
                <li key={`${entry.personId}-${entry.roleType}-${entry.roleName}`}>
                  <Link
                    className="entry"
                    href={`/stars/${entry.personId}`}
                  >
                    <span className="entry__title">{entry.personName}</span>
                    {entry.roleName && (
                      <span className="entry__meta">{entry.roleName}</span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </section>
    </main>
  )
}
