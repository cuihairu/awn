'use client'

import { useState } from 'react'

/**
 * 人物照片位（素材红线：docs/design/star-catalog.md §3）。
 *
 * 三条硬约束：
 * 1. src 为空 → 渲染中性剪影占位，标「未收录图片」，绝不生成人脸顶替；
 * 2. src 有值但加载失败 → 同样落到占位，文案改为「图片加载失败」，
 *    与「未收录」区分开（前者是链路问题，后者是资料问题）；
 * 3. 占位一律带「示意图」角标，避免被当成真实照片。
 */
export type StarPhotoState = 'unavailable' | 'error'

export function StarPhoto({
  src,
  name,
  variant = 'card',
}: {
  src: string | null | undefined
  name: string
  variant?: 'card' | 'hero'
}) {
  const [failure, setFailure] = useState<StarPhotoState | null>(null)

  if (src && !failure) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        className={`star-photo star-photo--${variant}`}
        src={src}
        alt={`${name}的照片`}
        onError={() => setFailure('error')}
      />
    )
  }

  const reason = failure === 'error' ? '图片加载失败' : '未收录图片'

  return (
    <div
      className={`star-photo star-photo--${variant} star-photo--placeholder`}
      role="img"
      aria-label={`${name}：${reason}`}
      data-state={failure ?? 'unavailable'}
    >
      {/* 中性剪影：几何色块，不含人脸细节 */}
      <svg viewBox="0 0 64 64" aria-hidden focusable="false">
        <circle cx="32" cy="22" r="11" />
        <path d="M10 60a22 22 0 0 1 44 0z" />
      </svg>
      <span className="star-photo__reason">{reason}</span>
      <span className="star-photo__badge">示意图</span>
    </div>
  )
}