---
layout: home

hero:
  name: 麦芒网 AWN
  text: 票务交易与情报平台
  tagline: Java + Spring Boot 模块化单体 · 专场页面可以千差万别，购买链路只有一套
  image:
    src: /logo.svg
    alt: AWN
  actions:
    - theme: brand
      text: 架构总览
      link: /architecture/system-overview
    - theme: alt
      text: 实施计划
      link: /implementation-plan
    - theme: alt
      text: API 契约
      link: /api/contracts

features:
  - icon: 🎯
    title: 统一交易核心
    details: 专场只负责展示与营销，所有专场共用统一商品 API 与统一订单链路，不允许旁路下单。
  - icon: 🛡️
    title: PostgreSQL 防超卖
    details: PostgreSQL 是交易库存的最终权威，预留必须使用事务与原子条件更新，配合约束做最后一道防线。
  - icon: 🔁
    title: 支付幂等
    details: 支付回调、订单创建、库存释放、退款处理全部幂等；支付与库存释放的竞态由状态机和事务解决。
  - icon: 🧩
    title: 专场可配置
    details: 专场基于不可变发布版本与受控模块白名单渲染，运营可自由组合页面，但无法触碰交易语义。
  - icon: 📊
    title: 情报隔离
    details: 票务情报只采集、标准化、分析公开数据，严格禁止写入交易库存、订单、支付与退款数据。
  - icon: 📱
    title: 多端一体
    details: monorepo 统一管理网页端（Next.js）、小程序（Taro）、客户端（Flutter）与运营后台（React）。

---

## 界面原型

基于 [AWN 设计规范](/design/prototypes)（`ui-ux-pro-max` 生成）产出的三端高保真原型，点击查看完整说明。

<div class="prototype-gallery">
  <figure class="prototype-card">
    <a href="/design/prototypes" target="_blank">
      <img src="/prototypes/web-campaign.svg" alt="消费端 Web 专场页原型" loading="lazy" />
    </a>
    <figcaption>
      <span><strong>消费端 Web</strong>（Next.js）· F1 专场页 · 高级黑 + 麦芒金</span>
      <a href="/design/prototypes">查看说明 →</a>
    </figcaption>
  </figure>
  <figure class="prototype-card">
    <a href="/design/prototypes" target="_blank">
      <img src="/prototypes/admin-dashboard.svg" alt="运营管理后台原型" loading="lazy" />
    </a>
    <figcaption>
      <span><strong>运营管理后台</strong> · 数据密集 Dashboard · KPI / 对账 / 异常订单</span>
      <a href="/design/prototypes">查看说明 →</a>
    </figcaption>
  </figure>
  <figure class="prototype-card">
    <a href="/design/prototypes" target="_blank">
      <img src="/prototypes/mobile-app.svg" alt="移动客户端原型" loading="lazy" />
    </a>
    <figcaption>
      <span><strong>移动客户端</strong>（Flutter）· 首页 / 票档选择 / 幂等支付</span>
      <a href="/design/prototypes">查看说明 →</a>
    </figcaption>
  </figure>
</div>

