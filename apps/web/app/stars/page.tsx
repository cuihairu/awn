'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import {
  listPersons,
  type PersonView,
  type ProfessionType,
} from '@awn/api-client'
import { StarPhoto } from '../../components/StarPhoto'
import { useDebouncedValue } from '../../lib/use-debounced-value'
import {
  PROFESSION_LABEL_ZH,
  PROFESSION_ORDER,
  groupPersonsByProfession,
} from '../../lib/star-labels'

/**
 * 人物列表 /stars（docs/design/star-catalog.md §2.1）。
 * 检索范围：姓名 / 别名 / 原名 + 职业类型 + 国籍；不做全文与拼音模糊。
 * 列表按职业类型分组，chips 顺序固定（分组顺序即 chips 顺序）。
 */
export default function StarsPage() {
  const [persons, setPersons] = useState<PersonView[]>([])
  const [profession, setProfession] = useState<ProfessionType | ''>('')
  const [nationality, setNationality] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const debouncedKeyword = useDebouncedValue(keyword)

  useEffect(() => {
    let alive = true
    setLoading(true)
    listPersons({
      profession: profession || undefined,
      nationality: nationality || undefined,
      q: debouncedKeyword || undefined,
    })
      .then((data) => {
        if (!alive) return
        setPersons(data)
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
  }, [profession, nationality, debouncedKeyword])

  // 国籍下拉只列当前结果里出现过的国籍，避免给出空选项
  const nationalities = useMemo(
    () => [...new Set(persons.map((p) => p.nationality))].sort(),
    [persons],
  )
  const groups = useMemo(() => groupPersonsByProfession(persons), [persons])

  return (
    <main className="page">
      <nav className="crumbs">
        <Link href="/">首页</Link>
        <span aria-hidden>/</span>
        <span>明星图鉴</span>
      </nav>

      <header className="section-head">
        <h1>明星图鉴</h1>
        <p className="muted">按职业类型浏览人物，或用姓名 / 别名搜索。</p>
      </header>

      <section className="filters" aria-label="人物筛选">
        <div className="chips" role="group" aria-label="职业类型">
          <button
            type="button"
            className={profession === '' ? 'chip chip--on' : 'chip'}
            aria-pressed={profession === ''}
            onClick={() => setProfession('')}
          >
            全部
          </button>
          {PROFESSION_ORDER.map((code) => (
            <button
              key={code}
              type="button"
              className={profession === code ? 'chip chip--on' : 'chip'}
              aria-pressed={profession === code}
              onClick={() => setProfession(code)}
            >
              {PROFESSION_LABEL_ZH[code]}
            </button>
          ))}
        </div>

        <div className="filters__row">
          <label className="field">
            <span>国籍</span>
            <select
              value={nationality}
              onChange={(e) => setNationality(e.target.value)}
            >
              <option value="">全部</option>
              {nationalities.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>

          <label className="field field--grow">
            <span>搜索</span>
            <input
              type="search"
              value={keyword}
              placeholder="姓名 / 别名 / 原名"
              onChange={(e) => setKeyword(e.target.value)}
            />
          </label>
        </div>
      </section>

      {loading && <p className="state">加载中…</p>}
      {failed && (
        <p className="state state--error">
          图鉴数据加载失败，请确认接口已启动后重试。
        </p>
      )}

      {!loading && !failed && persons.length === 0 && (
        <p className="state">没有匹配的人物，换个筛选条件试试。</p>
      )}

      {groups.map((group) => (
        <section key={group.key} className="group">
          <h2>
            {group.label}
            <span className="count">{group.items.length}</span>
          </h2>
          <ul className="card-grid">
            {group.items.map((person) => (
              <li key={`${group.key}-${person.id}`}>
                <Link className="person-card" href={`/stars/${person.id}`}>
                  <StarPhoto src={person.photoUrl} name={person.name} />
                  <div className="person-card__body">
                    <strong>{person.name}</strong>
                    {person.originalName && (
                      <span className="muted">{person.originalName}</span>
                    )}
                    <span className="tags">
                      {person.professionTypes.map((code) => (
                        <span key={code} className="tag">
                          {PROFESSION_LABEL_ZH[code]}
                        </span>
                      ))}
                      <span className="tag tag--quiet">{person.nationality}</span>
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  )
}