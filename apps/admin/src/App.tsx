import { useEffect, useState } from 'react'
import { AnomaliesView } from './views/AnomaliesView'
import { EventsView } from './views/EventsView'
import { InventoryView } from './views/InventoryView'
import { OrdersView } from './views/OrdersView'
import { ThemeSwitcher } from './components/ThemeSwitcher'

/**
 * 运营台骨架：侧边导航（hash 路由）+ 令牌输入（X-Admin-Token，localStorage 记忆）。
 * 四视图全部走 /api/v1/admin（契约 docs/api/contracts.md「运营接口」）；
 * 令牌由 AdminAuthFilter 校验，默认值见 application.yml（AWN_ADMIN_TOKEN）。
 */

const VIEWS = [
  { key: 'orders', label: '订单' },
  { key: 'inventory', label: '库存' },
  { key: 'events', label: '活动发布' },
  { key: 'anomalies', label: '异常' },
] as const

type ViewKey = (typeof VIEWS)[number]['key']
const TOKEN_STORAGE_KEY = 'awn-admin-token'

function currentHash(): ViewKey {
  const hash = window.location.hash.replace(/^#/, '')
  return (VIEWS.some((v) => v.key === hash) ? hash : 'orders') as ViewKey
}

export default function App() {
  const [view, setView] = useState<ViewKey>(currentHash)
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? '')

  useEffect(() => {
    const onHash = () => setView(currentHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const updateToken = (value: string) => {
    setToken(value)
    if (value) localStorage.setItem(TOKEN_STORAGE_KEY, value)
    else localStorage.removeItem(TOKEN_STORAGE_KEY)
  }

  const select = (key: ViewKey) => {
    window.location.hash = key
    setView(key)
  }

  const label = VIEWS.find((v) => v.key === view)?.label ?? ''
  const body = !token ? (
    <p className="state state--error">请先在右上角填入 X-Admin-Token（开发默认 awn-admin-dev-token）。</p>
  ) : view === 'orders' ? (
    <OrdersView token={token} />
  ) : view === 'inventory' ? (
    <InventoryView token={token} />
  ) : view === 'events' ? (
    <EventsView token={token} />
  ) : (
    <AnomaliesView token={token} />
  )

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="logo">麦芒运营台</div>
        <nav aria-label="后台导航">
          {VIEWS.map((v) => (
            <span
              key={v.key}
              className={view === v.key ? 'active' : ''}
              role="button"
              tabIndex={0}
              onClick={() => select(v.key)}
              onKeyDown={(e) => e.key === 'Enter' && select(v.key)}
            >
              {v.label}
            </span>
          ))}
        </nav>
        <p className="sidebar__note">
          原型 docs/design/prototypes/admin；场次配置视图待后端地理字段落地后接入。
        </p>
      </aside>
      <section className="content">
        <div className="content-head">
          <h1>{label}</h1>
          <label className="token-field">
            <span>X-Admin-Token</span>
            <input
              type="password"
              value={token}
              placeholder="awn-admin-dev-token"
              onChange={(e) => updateToken(e.target.value)}
              autoComplete="off"
            />
          </label>
        </div>
        {body}
      </section>
      <ThemeSwitcher />
    </main>
  )
}
