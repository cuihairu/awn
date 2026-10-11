export default function Home() {
  return (
    <main className="page">
      <header className="hero">
        <div className="logo-mark" aria-hidden />
        <h1>麦芒网</h1>
        <p>票务交易与情报平台 · 网页端（Next.js）</p>
      </header>
      <section className="placeholder">
        <p>
          本应用为 monorepo 骨架，实施计划见 <code>docs/implementation-plan</code>（VitePress
          文档站），Phase 02 将实现活动、商品与专场渲染。
        </p>
        <ul>
          <li>专场模板渲染器：packages/campaign-renderer</li>
          <li>统一商品 / 票档组件：packages/ticket-components</li>
          <li>API 客户端：packages/api-client（OpenAPI 契约驱动）</li>
        </ul>
      </section>
    </main>
  )
}
