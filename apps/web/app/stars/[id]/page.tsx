'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { getPerson, type PersonDetail } from '@awn/api-client'
import { StarPhoto } from '../../../components/StarPhoto'
import {
  PROFESSION_LABEL_ZH,
  formatRole,
  formatYear,
  groupWorksByType,
} from '../../../lib/star-labels'

/**
 * 人物详情 /stars/{id}（docs/design/star-catalog.md §2.1）。
 * 公开面只返回 PUBLISHED：草稿与不存在一律 404 信封，页面按「不存在或未发布」呈现。
 * 作品列表按作品类型分组，每行带身份（formatRole），点击跳作品详情——与作品详情互跳。
 */
export default function PersonDetailPage() {
  const params = useParams<{ id: string }>()
  const personId = params.id

  const [detail, setDetail] = useState<PersonDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    getPerson(personId)
      .then((data) => {
        if (!alive) return
        if ('person' in data) {
          setDetail(data)
        } else {
          setNotFound(true)
        }
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
  }, [personId])

  const groups = useMemo(
    () => (detail ? groupWorksByType(detail.works) : []),
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
          <span>人物</span>
        </nav>
        <p className="state state--error">
          人物不存在或未发布。<Link href="/stars">返回图鉴列表</Link>
        </p>
      </main>
    )
  }

  if (failed || !detail) {
    return (
      <main className="page">
        <p className="state state--error">图鉴数据加载失败，请稍后重试。</p>
      </main>
    )
  }

  const { person } = detail

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">首页</Link>
        <span aria-hidden>/</span>
        <Link href="/stars">明星图鉴</Link>
        <span aria-hidden>/</span>
        <span>{person.name}</span>
      </nav>

      <header className="detail-head">
        <StarPhoto src={person.photoUrl} name={person.name} variant="hero" />
        <div className="detail-head__body">
          <h1>{person.name}</h1>
          {person.originalName && <p className="muted">{person.originalName}</p>}
          <p className="tags">
            {person.professionTypes.map((code) => (
              <span key={code} className="tag">
                {PROFESSION_LABEL_ZH[code]}
              </span>
            ))}
            <span className="tag tag--quiet">{person.nationality}</span>
          </p>
          {person.aliases.length > 0 && (
            <p className="muted">别名：{person.aliases.join('、')}</p>
          )}
        </div>
      </header>

      {person.bio && (
        <section className="detail-section">
          <h2>简介</h2>
          <p>{person.bio}</p>
        </section>
      )}

      <section className="detail-section">
        <h2>
          参与作品
          <span className="count">{detail.works.length}</span>
        </h2>
        {groups.length === 0 && <p className="muted">暂无已发布的作品记录。</p>}
        {groups.map((group) => (
          <section key={group.key} className="group">
            <h3>{group.label}</h3>
            <ul className="entry-list">
              {group.items.map((entry) => (
                <li key={`${entry.workId}-${entry.roleType}-${entry.roleName}`}>
                  <Link
                    className="entry"
                    href={`/works/${entry.workId}`}
                  >
                    <span className="entry__title">{entry.workTitle}</span>
                    <span className="entry__meta">
                      {formatYear(entry.workYear)} · {formatRole(entry)}
                    </span>
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
