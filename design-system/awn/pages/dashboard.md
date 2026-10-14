# AWN Admin Dashboard 设计规范（页面级覆盖）

> 依据设计系统 Master 的数据密集型后台样式（Density 8/10）调整为本品牌。

## 定位

AWN 运营管理后台：活动管理、专场编辑、订单运营、库存、支付对账、审计。

## 设计基调

- **数据密集优先**：8–32px 间距刻度，最大化数据可见性。
- **品牌一致性覆盖默认蓝色**：主操作与高亮一律使用麦芒金，语义色仅用于状态。

## 色彩令牌

| 令牌 | 值 | 用途 |
|--------|--------|--------|
| `--color-primary` | `#1C1917` | 主文本、导航选中 |
| `--color-accent` | `#D59819` | 主按钮、选中态、KPI 高亮 |
| `--color-accent-deep` | `#A16207` | Accent 按压态、图表主色 |
| `--color-background` | `#F8FAFC` | 页面背景 |
| `--color-card` | `#FFFFFF` | 卡片、表格容器 |
| `--color-muted` | `#F5F5F4` | 分隔、次级背景 |
| `--color-muted-foreground` | `#57534E` | 次级文本 |
| `--color-success` | `#16A34A` | 已支付 / 已完成 |
| `--color-warning` | `#D97706` | 待处理 / 预警 |
| `--color-danger` | `#DC2626` | 异常 / 对账差异 |
| `--color-info` | `#2563EB` | 履约中 / 进行中 |

## 字体

- 界面：`'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif`
- 数字/金额：等宽数字 `font-variant-numeric: tabular-nums`（数据表格必须）

## 组件规则

- KPI 卡片：数值大号 tabular-nums，涨跌用语义色箭头，不带装饰性插图。
- 表格：行高 ≤ 44px；行悬停 `#FAFAF9`；状态用 12px 圆角胶囊标签。
- 图表： tooltips 常开；图例可点击过滤；对比色需区分色盲（不以颜色为唯一编码）。
- 过滤器：列表页必须常驻筛选区（避免"ornate design + no filtering"反模式）。
- 密度：表格/列表 8px 基线；页面区块间 24px；侧边栏分组 16px。

## 反模式（避免）

- 装饰性插图、大圆角卡片堆砌。
- 无过滤、无排序的数据表。
- 金额用非等宽字体。
