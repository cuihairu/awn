import { useState } from 'react'
import { ApiError } from '@awn/api-client'
import { adminApi, type AdminSessionRow } from '../lib/admin-client'
import {
  formatAdminTime,
  parseBounded,
  validateVenueForm,
  type VenueFormInput,
} from '../lib/admin-format'
import { useAdminData } from '../lib/use-admin-data'

const EMPTY_VENUE_FORM: VenueFormInput = {
  name: '',
  city: '',
  address: '',
  latitude: '',
  longitude: '',
  mapImageUrl: '',
  mapMarkerX: '',
  mapMarkerY: '',
}

/** 解析后的 geo 行：场馆名 / 城市（无任何场馆显示「—」，不造假地点）；示意图存在则标注 */
function geoText(session: AdminSessionRow): string {
  if (!session.geo) return '—'
  const parts = [session.geo.venueName, session.geo.city]
  if (session.geo.mapImageUrl) parts.push('含位置示意图')
  return parts.filter(Boolean).join(' · ')
}

/**
 * 场次配置视图（地理配置面）：选活动 → 场次列表（含解析 geo）→ 挂 / 摘场馆；
 * 场馆配置表单（V26）：地理字段 + 位置示意图（方案 B 首方静态图，明示非精确地图），
 * PUT /admin/venues/{venueCode} 全量覆盖——坐标与标记成对校验（半截必然指错位置）。
 * 未知场次 / 场馆 → 404（SESSION_NOT_FOUND / VENUE_NOT_FOUND）。
 */
export function SessionsView({ token }: { token: string }) {
  const { data: events, loading: eventsLoading, failed: eventsFailed } = useAdminData(
    () => adminApi.events(token),
    [token],
  )
  const { data: venues, reload: reloadVenues } = useAdminData(() => adminApi.venues(token), [token])
  const [eventCode, setEventCode] = useState('')
  const activeEvent = eventCode || events?.[0]?.eventCode || ''
  const { data: sessions, loading, failed, reload } = useAdminData(
    () => (activeEvent ? adminApi.eventSessions(token, activeEvent) : Promise.resolve([])),
    [token, activeEvent],
  )
  const [choices, setChoices] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState('')
  /** 场馆配置表单：选中场馆后按现值预填，PUT 全量覆盖（null 即清空） */
  const [venueCode, setVenueCode] = useState('')
  const [venueForm, setVenueForm] = useState<VenueFormInput>(EMPTY_VENUE_FORM)

  if (eventsLoading) return <p className="state">加载中…</p>
  if (eventsFailed)
    return (
      <p className="state state--error">
        活动加载失败（令牌或网络问题）。
        <button className="btn btn-sm" type="button" onClick={() => window.location.reload()}>
          刷新
        </button>
      </p>
    )

  const patchVenueForm = (patch: Partial<VenueFormInput>) =>
    setVenueForm((prev) => ({ ...prev, ...patch }))

  const selectVenue = (code: string) => {
    setVenueCode(code)
    setNotice('')
    const venue = (venues ?? []).find((item) => item.venueCode === code)
    if (!venue) {
      setVenueForm(EMPTY_VENUE_FORM)
      return
    }
    setVenueForm({
      name: venue.name ?? '',
      city: venue.city ?? '',
      address: venue.address ?? '',
      latitude: venue.latitude == null ? '' : String(venue.latitude),
      longitude: venue.longitude == null ? '' : String(venue.longitude),
      mapImageUrl: venue.mapImageUrl ?? '',
      mapMarkerX: venue.mapMarkerX == null ? '' : String(venue.mapMarkerX),
      mapMarkerY: venue.mapMarkerY == null ? '' : String(venue.mapMarkerY),
    })
  }

  /** 场馆配置保存：自检通过后全量覆盖提交（空串按缺省下发 null） */
  const saveVenue = async () => {
    if (!venueCode) return setNotice('请先选择场馆。')
    const problem = validateVenueForm(venueForm)
    if (problem) return setNotice(problem)
    setBusy(`venue:${venueCode}`)
    setNotice('')
    try {
      await adminApi.updateVenue(token, venueCode, {
        name: venueForm.name.trim(),
        city: venueForm.city.trim() || null,
        address: venueForm.address.trim() || null,
        latitude: parseBounded(venueForm.latitude, -90, 90),
        longitude: parseBounded(venueForm.longitude, -180, 180),
        mapImageUrl: venueForm.mapImageUrl.trim() || null,
        mapMarkerX: parseBounded(venueForm.mapMarkerX, 0, 100),
        mapMarkerY: parseBounded(venueForm.mapMarkerY, 0, 100),
      })
      setNotice('场馆配置已保存（示意图明示非精确地图，坐标与标记成对校验）。')
      reloadVenues()
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `保存失败：${err.status} ${err.code}`
          : '保存失败：网络或服务异常。',
      )
    } finally {
      setBusy('')
    }
  }

  const run = async (sessionCode: string, action: 'attach' | 'detach') => {
    setBusy(sessionCode)
    setNotice('')
    try {
      if (action === 'attach') {
        const venueCode = choices[sessionCode]
        if (!venueCode) return
        const geo = await adminApi.attachSessionVenue(token, sessionCode, venueCode)
        setNotice(`已挂场馆：${geo ? [geo.venueName, geo.city].filter(Boolean).join(' · ') : '（空）'}`)
      } else {
        await adminApi.detachSessionVenue(token, sessionCode)
        setNotice('已摘除场次场馆覆盖，回退父活动场馆继承。')
      }
      reload()
    } catch (err) {
      setNotice(
        err instanceof ApiError
          ? `操作失败：${err.status} ${err.code}`
          : '操作失败：网络或服务异常。',
      )
    } finally {
      setBusy('')
    }
  }

  return (
    <>
      {notice && <p className="state" role="status">{notice}</p>}
      <div className="toolbar">
        <label>
          活动：
          <select value={activeEvent} onChange={(e) => setEventCode(e.target.value)}>
            {(events ?? []).map((event) => (
              <option key={event.eventCode} value={event.eventCode}>
                {event.name}（{event.eventCode}）
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">场次</th>
              <th scope="col">开始时间</th>
              <th scope="col">当前地点（geo）</th>
              <th scope="col">挂场馆</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {(sessions ?? []).map((session) => (
              <tr key={session.sessionCode}>
                <td>{session.name}</td>
                <td className="num">{formatAdminTime(session.startTime)}</td>
                <td>{geoText(session)}</td>
                <td>
                  <select
                    aria-label={`为 ${session.name} 选择场馆`}
                    value={choices[session.sessionCode] ?? ''}
                    onChange={(e) =>
                      setChoices((prev) => ({ ...prev, [session.sessionCode]: e.target.value }))
                    }
                  >
                    <option value="">选择场馆…</option>
                    {(venues ?? []).map((venue) => (
                      <option key={venue.venueCode} value={venue.venueCode}>
                        {venue.name}（{venue.city ?? '城市未录'}）
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <button
                    className="btn btn-sm"
                    type="button"
                    disabled={busy === session.sessionCode || !choices[session.sessionCode]}
                    onClick={() => run(session.sessionCode, 'attach')}
                  >
                    挂场馆
                  </button>{' '}
                  <button
                    className="btn btn-sm"
                    type="button"
                    disabled={busy === session.sessionCode}
                    onClick={() => run(session.sessionCode, 'detach')}
                  >
                    摘除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(sessions ?? []).length === 0 && <p className="state">该活动暂无场次。</p>}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h3>场馆配置（地理 + 位置示意图）</h3>
        <div className="toolbar">
          <label>
            场馆：
            <select value={venueCode} onChange={(e) => selectVenue(e.target.value)}>
              <option value="">选择场馆…</option>
              {(venues ?? []).map((venue) => (
                <option key={venue.venueCode} value={venue.venueCode}>
                  {venue.name}（{venue.city ?? '城市未录'}）
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="toolbar">
          <label>
            场馆名称
            <input
              className="input"
              value={venueForm.name}
              onChange={(e) => patchVenueForm({ name: e.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            城市
            <input
              className="input"
              value={venueForm.city}
              onChange={(e) => patchVenueForm({ city: e.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            地址
            <input
              className="input"
              value={venueForm.address}
              onChange={(e) => patchVenueForm({ address: e.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            纬度（-90 ~ 90，成对填写）
            <input
              className="input"
              value={venueForm.latitude}
              onChange={(e) => patchVenueForm({ latitude: e.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            经度（-180 ~ 180）
            <input
              className="input"
              value={venueForm.longitude}
              onChange={(e) => patchVenueForm({ longitude: e.target.value })}
              autoComplete="off"
            />
          </label>
        </div>
        <div className="toolbar">
          <label>
            位置示意图 URL（受控存储域）
            <input
              className="input"
              value={venueForm.mapImageUrl}
              onChange={(e) => patchVenueForm({ mapImageUrl: e.target.value })}
              autoComplete="off"
              placeholder="https://assets.awn.site/venues/…"
            />
          </label>
          <label>
            标记横向位置（0-100，成对填写）
            <input
              className="input"
              value={venueForm.mapMarkerX}
              onChange={(e) => patchVenueForm({ mapMarkerX: e.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            标记纵向位置（0-100）
            <input
              className="input"
              value={venueForm.mapMarkerY}
              onChange={(e) => patchVenueForm({ mapMarkerY: e.target.value })}
              autoComplete="off"
            />
          </label>
          <button
            className="btn btn-sm"
            type="button"
            disabled={!venueCode || busy === `venue:${venueCode}`}
            onClick={saveVenue}
          >
            {busy === `venue:${venueCode}` ? '保存中…' : '保存场馆配置'}
          </button>
        </div>
        <p className="hint">
          示意图由运营从公开来源取用（首方静态图，不接第三方地图），web 端明示「非精确地图」；
          图上标记位置由你随图给定（相对图片左上角的百分比）。保存为全量覆盖：留空的字段
          会被清空，换场馆前请先核对全部字段。
        </p>
      </div>

      <p className="hint">
        geo 为后端解析结果（场次场馆覆盖 → 父活动场馆 → 空，不造假地点）；摘除只清场次覆盖，
        不动父活动场馆。接口见 GET /api/v1/admin/venues、PUT /api/v1/admin/venues/&#123;venueCode&#125;
        与 POST/DELETE /api/v1/admin/sessions/&#123;sessionCode&#125;/venue。
      </p>
    </>
  )
}
