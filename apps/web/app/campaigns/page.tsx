'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

/**
 * 专场入口 /campaigns。
 * 专场由运营在后台创建并发布后才有公开地址（/campaigns/{code}），
 * 当前没有公开专场列表接口——此处手输编码跳转，不造假专场数据。
 */
export default function CampaignsIndexPage() {
  const router = useRouter()
  const [code, setCode] = useState('')

  return (
    <main className="page">
      <h1>专场</h1>
      <p className="muted">
        专场内容由运营在后台配置并发布，发布后按专场编码访问。
      </p>
      <div className="cols-2">
        <label className="field">
          <span>专场编码</span>
          <input
            value={code}
            placeholder="如 CAMP-F1-2026"
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && code.trim()) {
                router.push(`/campaigns/${encodeURIComponent(code.trim())}`)
              }
            }}
          />
        </label>
        <button
          className="btn"
          disabled={!code.trim()}
          onClick={() => router.push(`/campaigns/${encodeURIComponent(code.trim())}`)}
        >
          查看专场
        </button>
      </div>
    </main>
  )
}