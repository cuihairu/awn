import type { Metadata } from 'next'
import type { CSSProperties } from 'react'
import Link from 'next/link'
import { AWN_CSS_VARS } from '@awn/ui'
import './globals.css'

export const metadata: Metadata = {
  title: '麦芒网 AWN · 票务交易与情报平台',
  description: '演唱会、音乐节、体育赛事票务与票务情报',
}

const awnVars = Object.fromEntries(
  Object.entries(AWN_CSS_VARS).map(([name, value]) => [name, value]),
) as CSSProperties

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" style={awnVars}>
      <body>
        <nav className="topnav" aria-label="主导航">
          <Link href="/">首页</Link>
          <Link href="/stars">明星图鉴</Link>
        </nav>
        {children}
      </body>
    </html>
  )
}
