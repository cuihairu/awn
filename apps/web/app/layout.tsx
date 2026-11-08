import type { Metadata } from 'next'
import { allThemesCss } from '@awn/ui'
import Link from 'next/link'
import { ThemeSwitcher } from '@/components/ThemeSwitcher'
// LAY-001 分层导入：cascade 顺序即覆盖顺序，同名选择器靠后导入者胜。
// 结构层(globals) → 全局界面骨架(chrome) → 图鉴/详情域(catalog) → 活动/场次/票档域(events) → 购买/订单/座位图域(checkout)。
import './globals.css'
import './styles/chrome.css'
import './styles/catalog.css'
import './styles/events.css'
import './styles/checkout.css'

export const metadata: Metadata = {
  title: '麦芒网 AWN · 票务交易与情报平台',
  description: '演唱会、音乐节、体育赛事票务与票务情报',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" data-theme="default">
      <body>
        {/* 主题令牌（含 --awn-* 桥接）全量注入；切换只改 data-theme 属性 */}
        <style dangerouslySetInnerHTML={{ __html: allThemesCss() }} />
        <nav className="topnav" aria-label="主导航">
          <Link href="/">发现</Link>
          <Link href="/stars">明星图鉴</Link>
          <Link href="/campaigns">专场</Link>
        </nav>
        {children}
        <ThemeSwitcher />
      </body>
    </html>
  )
}
