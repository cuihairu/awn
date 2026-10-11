export default function App() {
  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="logo">麦芒运营台</div>
        <nav>
          <span className="active">运营总览</span>
          <span>活动管理</span>
          <span>专场编辑</span>
          <span>商品票档</span>
          <span>库存中心</span>
          <span>订单管理</span>
          <span>支付对账</span>
          <span>卖家与挂牌</span>
          <span>票务情报</span>
          <span>审计日志</span>
        </nav>
      </aside>
      <section className="content">
        <h1>运营总览</h1>
        <p>monorepo 骨架。原型见 docs/design/prototypes（admin-dashboard.svg）。</p>
        <p>
          Phase 02-06 依次实现专场编辑、库存调整、订单运营、对账与审计；数据一律通过
          /api/v1/admin 接口获取，接口契约见 docs/api/contracts.md。
        </p>
      </section>
    </main>
  )
}
