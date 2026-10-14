# 界面原型图

> 2026-10-12 起，四端原型重做为令牌化 HTML 版（多主题、可切换），位于仓库
> [`docs/design/prototypes/`](https://github.com/cuihairu/awn/tree/main/docs/design/prototypes)（`index.html` 为总览），
> 设计体系见 [`ui-system.md`](ui-system.md)。本页保留为早期 SVG 原型存档。

以下为 2026-10-12 前的 SVG 版原型存档，当前原型见上方 [`docs/design/prototypes/`](https://github.com/cuihairu/awn/tree/main/docs/design/prototypes)。设计令牌见 `design-system/awn/MASTER.md`。

## 消费端 Web（Next.js）· F1 专场页

高级黑 + 麦芒金的演出质感；专场页面展示、统一商品 API 展示可售状态、票务情报入口。

![AWN Web 专场页原型](/prototypes/web-campaign.svg)

## 运营管理后台 · 数据总览

数据密集型 Dashboard：KPI 卡、GMV 趋势、支付渠道分布、订单表（含异常订单人工复核入口）。

![AWN 运营后台原型](/prototypes/admin-dashboard.svg)

## 移动客户端（Flutter）· 购票流程

首页发现 → 活动详情与票档选择（统一库存、情报参考）→ 订单支付（幂等键、支付超时释放提示）。

![AWN 客户端原型](/prototypes/mobile-app.svg)

## 设计规范摘要

| 令牌 | 值 | 说明 |
|--------|--------|--------|
| 主文本 | `#1C1917` | 高级黑（stone-900） |
| 品牌金 | `#D59819` / `#A16207` | 主操作、选中态、图表主色 |
| 背景 | `#FAFAF9` / 卡片 `#FFFFFF` | 浅色模式 |
| 语义色 | `#16A34A` `#D97706` `#DC2626` `#2563EB` | 成功 / 警告 / 危险 / 进行中 |
| 字体 | Noto Sans SC / PingFang SC | 数字使用 tabular-nums |

完整规范见仓库 `design-system/awn/MASTER.md` 与 `design-system/awn/pages/dashboard.md`。
