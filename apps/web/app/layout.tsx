import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '麦芒网 AWN · 票务交易与情报平台',
  description: '演唱会、音乐节、体育赛事票务与票务情报',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  )
}
