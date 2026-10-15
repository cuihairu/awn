# 场馆座位图（Seat Map）开源方案调研

- 调研日期：2026-10-13
- 目标：为 AWN（麦芒网）找到可复用的「场馆座位数据 + 2D 渲染 + 3D 渲染 + 选座交互」方案，覆盖 Next.js（Web）与 Flutter（移动端）。
- 方法：GitHub API / npm registry / pub.dev / taginfo 实际核验仓库与页面（下方每行均给出来源 URL）。搜索方向含 stadium/arena seat map SVG、seatpick/seatsmith/venuejs、react-seat-map/seat-picker 类 npm 包、OSM 场馆数据、three.js 3D 场馆、中文选座项目、中国场馆开源数据集等。

---

## 1. 结论（单个推荐）

**推荐：自建独立项目 `venue-seats`（MIT 或 Apache-2.0），scope = 场馆座位数据模型 + 数据格式规范 + 2D 渲染器 + 3D 渲染器（three.js 程序化几何）+ 选座交互，由 AWN 作为依赖消费。**

**但按用户 2026-10-12 拍板：先不立项 `venue-seats`。** 第一阶段先做小规模验证（见 §8 第 3 条三问验证），验证成立后再决定是否把场馆数据管理、导入工具和编辑器抽成独立项目。第一阶段范围也已定为：优先覆盖中国大型体育馆 / 演唱会场馆，先实现选区、选排（L1/L2），不强求逐座精确（L3）。

理由：

1. **没有任何单一开源项目同时覆盖「座位数据 + 2D + 3D + Flutter」**。2D 引擎类项目（seatmap-canvas、seatchart.js、react-seat-picker 等）均不含真实场馆数据；3D 侧只有 Fi-Stadium 这类 demo 级项目（MIT，但只有 3 个 commit、无真实场馆）；中国场馆座位数据侧开源项目为零（OSM 只有建筑轮廓）。
2. **先分清两个问题**：座位图引擎（怎么画/选座位——可自行实现，不依赖全国场馆数据）vs 座位图数据（真实场馆布局、每场舞台朝向、哪些座位可卖、票价分区——最难收集的资产）。不要因为「数据难」而放弃自建引擎，也不要把「自动采集全国演唱会座位图」当成系统前提。
3. **最佳开源参考均为 MIT**（seatmap-canvas 的 2D 交互与 JSON seat model；Fi-Stadium 的 three.js InstancedMesh + Raycaster 程序化 3D 思路），可吸收其设计思想但无需整体采用；pretix 的 seating schema（AGPL）只宜参考思路、不可复制代码。
4. **许可风险排除了几条路**：SeatLayer（MIT 仅壳，渲染器/3D/库存为专有闭源运行时）、SeatMap-JS（GPL-3.0 传染）、pretix（AGPL-3.0 传染）、seats.io / seatmap.pro / tickets.dev（商业授权、数据归属平台）。一个独立项目不宜以这些为依赖。
5. `venue-seats` 需交付：README、数据格式规范（JSON Schema）、3-5 个中国示例场馆（从公开信息重建，无法保证准确时明确标注「示意」）、Web 端 2D/3D 渲染组件、Flutter 端渲染组件。集成路径：AWN 后端发布场馆/演出配置数据，Next.js 引入 web 组件，Flutter 引入 Dart 包，三者共用同一份数据格式。

---

## 2. 调研清单

| 项目 | 许可 | 数据格式 | 含中国常见场馆 | 可复用 web | 可复用移动端(Flutter) | 维护活跃度 | 出处链接 |
|---|---|---|---|---|---|---|---|
| alisaitteke/seatmap-canvas | MIT | JSON（Block/Seat model：id/title/x/y/salable） | 无（仅示例数据） | 是（React wrapper 处于 Testing） | 否（需自写） | 活跃（最后 push 2026-10-05；v2.7.6 发布于 2026-04-26；163 stars） | https://github.com/alisaitteke/seatmap-canvas |
| seatlayer/seatlayer-sdk | MIT（仅框架包装）；渲染器/3D/库存运行时专有 | 平台 API（自有平台） | 无 | 是（React/Vue/Angular 包装） | 未核实 | 新且早期（仓库建于 2026-08；最后 push 2026-10-04；1 star） | https://github.com/seatlayer/seatlayer-sdk |
| roggervalf/react-seat-picker | MIT | 组件 props（网格 alpha 排号） | 无 | 是 | 否 | 活跃（npm v2.0.0 发布于 2026-01-11；仓库 push 2026-01-11；35 stars） | https://github.com/roggervalf/react-seat-picker |
| gcedo/react-seatmap | MIT | 组件 props（极简网格） | 无 | 是 | 否 | 停更（最后 push 2020-09-04；46 stars） | https://github.com/gcedo/react-seatmap |
| omahili/seatchart.js | MIT | JSON 网格（车厢/影院式） | 无 | 是 | 否 | 低活跃（最后 push 2023-10-01；198 stars） | https://github.com/omahili/seatchart.js |
| mateuszmarkowski/jQuery-Seat-Charts | MIT | 网格 map 字符串 | 无 | 是（jQuery 时代） | 否 | 停更（最后 push 2020-02-06；607 stars） | https://github.com/mateuszmarkowski/jQuery-Seat-Charts |
| arturocastillo95/SeatMap-JS | GPL-3.0 | 编辑器产出（PixiJS 渲染） | 无 | 理论可（GPL 传染，不建议） | 否 | 低活跃（最后 push 2026-09-11；5 stars） | https://github.com/arturocastillo95/SeatMap-JS |
| pretix（seating 功能） | AGPL-3.0 | JSON Schema（zones/categories/rows/areas/seat GUID，有正式 schema 文件） | 无（欧式场馆，无中国场馆数据） | 否（Django 全家桶，AGPL 传染） | 否 | 活跃（最后 push 2026-10-12；2541 stars） | https://github.com/pretix/pretix（schema：src/pretix/static/seating/seating-plan.schema.json） |
| amarjeetsahoo/Fi-Stadium | MIT | 程序化生成；FAQ 称可接外部 JSON（seat ID/票价/可售状态） | 无 | 参考（three.js 代码思路） | 否 | demo 级（仅 3 commits；最后 push 2026-07-23；0 stars） | https://github.com/amarjeetsahoo/Fi-Stadium |
| doronsha13/headless-stadium | MIT | 程序化生成 + Wix 数据 | 无 | 参考 | 否 | demo 级（最后 push 2026-07-28；1 star） | https://github.com/doronsha13/headless-stadium |
| book_my_seat（Flutter 包） | MIT | 网格 rows/cols + SeatState 枚举 | 无 | 否（Flutter 专用） | 是 | 低活跃（v1.0.1 约 2023 年发布，约 3 年未更新；unverified uploader） | https://pub.dev/packages/book_my_seat |
| zenghao0219/seat-select-api | Apache-2.0 | JSON（微信选座风格，淘票票/猫眼式） | 部分（仅数据结构风格，无真实场馆数据） | 参考数据结构 | 参考数据结构 | 停更（最后 push 2022-06-17；19 stars） | https://github.com/zenghao0219/seat-select-api |
| MichaelEric/seat-map | 无许可证 | canvas 手绘网格 | 无 | 参考 | 否 | 停更（最后 push 2018-09-17；4 stars） | https://github.com/MichaelEric/seat-map |
| WSUVCapstoneTeam7/SeatMapMaker + SeatMapViewer | MIT | JSON（编辑器产出） | 无 | 参考（课程项目） | 否 | 停更（最后 push 2018-11-25；19 stars） | https://github.com/WSUVCapstoneTeam7/SeatMapMaker |
| Seats.io | 商业 SaaS（渲染器/服务非开源） | 平台专有（chart 不开放给平台外） | 无（海外场馆为主） | 是（嵌入 SDK） | 未核实（有 iOS/Android SDK 印象，未核实） | 商业活跃 | https://www.seats.io |
| seatmap.pro | 商业 | 平台 REST（venue/schema/event/pricing）+ WebGL 渲染 SDK | 无（海外为主） | 是（SDK 嵌入） | 未核实 | 商业活跃 | https://seatmap.pro/developers |
| tickets.dev | 商业数据 API | 场馆 SVG + section/row/seat JSON | 部分（海外平台为主） | 数据源可复用 | 数据源可复用 | 商业活跃（$50/月起） | https://tickets.dev |
| rebrowser/seatgeek-dataset | 未标注（rebrowser 商业/学术计划数据） | Parquet/CSV（12K+ 场馆容量+坐标；事件含 seat map availability 字段） | 无（北美为主） | 数据源可复用 | 数据源可复用 | 日更（仓库 push 2026-10-12；3 stars） | https://github.com/rebrowser/seatgeek-dataset |
| OpenStreetMap / indoorequal | ODbL（OSM 数据）/ BSD-3-Clause（indoorequal 代码） | OSM tags（leisure=stadium 等） | 部分（有场馆轮廓/名称/地址，无座位级数据） | 间接 | 间接 | 活跃（indoor=stadium 全网 51,977 个对象，taginfo 2026-10-13 核验） | https://wiki.openstreetmap.org/wiki/Tag:leisure%3Dstadium ；https://github.com/indoorequal/indoorequal |

**未找到的方向**：`venuejs`（座位图库不存在；同名仓库是 O2O 场馆预订平台项目 AlecGiraffa/Venuejs）、`seatsmith`（GitHub 无相关结果）、`seatpick` 作为开源座位图库（搜索只返回无关练手 demo）、中国常见场馆（奥体中心/体育中心/体育馆）的开源座位数据集——**未找到任何公开数据集**，多轮中英文检索均无结果。

---

## 3. 逐项要点

### seatmap-canvas（最接近需求的 2D 引擎）
- 是什么：TypeScript 写的 d3.js 座位图/选座框架，明确支持 stadium/theater/venue、多 block（分区/看台）布局，MIT。
- 能给：完整的 2D 选座交互（缩放/拖拽/点击/售罄置灰/分区色）、JSON seat model（`{id, title, x, y, salable}` 挂在 block 下）可作为我们 schema 的参照、React wrapper（`@alisaitteke/seatmap-canvas/react`，标注 Testing）。
- 不能：无 3D；无 Flutter 版；无真实场馆数据（assets 仅示例）；核心逻辑仍是「网格坐标式」座位图，对弧形看台/环形体育场（奥体/工体型）要靠 block 内 seat 的 x/y 硬摆。
- 定位：2D 交互细节的最佳学习对象；若嫌自建成本高，可在 AWN 侧以「自己维护分支」方式用其 MIT 代码做二次开发（可行，MIT 允许），但 3D 和 Flutter 仍要自写。

### Fi-Stadium（最接近需求的 3D 程序化思路）
- 是什么：MIT、零素材（zero-asset）、纯 three.js 程序化生成的 3D 球场座位预览 demo。
- 能给：验证了「程序化几何 + `THREE.InstancedMesh`（上千座位单次 draw call）+ `THREE.Raycaster`（instanceId 命中选座）+ GSAP 相机飞行/第一人称视角」这条技术路线完全可行，且声称可通过外部 JSON 接入真实 seat ID/票价/可售状态。
- 不能：只有 demo 球场（合成数据），无真实场馆、无演出配置概念、无移动端、代码量小（3 commits），没有工程化。
- 定位：3D 路线的可运行验证样板；思路可整体吸收进 venue-seats，代码不建议直接引用（无测试、仅 3 commits，但 MIT 允许）。

### pretix seating（数据 schema 的权威参照）
- 是什么：AGPL-3.0 的售票系统，含完整 seating plan 功能：JSON Schema（zones/categories/rows/areas/seat，全局 UUID）、编辑器、presale SVG 渲染、API 文档。
- 能给：经过实战检验的「分层 + GUID + 座位几何（坐标/朝向/尺寸）」数据模型；schema 文件可直接研读。
- 不能：AGPL 传染（不可复制进 AWN/venue-seats 代码）；无 3D；无中国场馆；web 端不能脱离 Django 系统使用。

### 商业方案（seats.io / seatmap.pro / SeatLayer）
- 共同点：渲染/数据/库存全部在对方平台；渲染器多为闭源或专有运行时；按 seat/事件收费或需签约。
- 风险：座位图数据被锁定在平台（迁移成本高）；作为「独立项目」的依赖不可行（SeatLayer 明确核心专有）；数据合规（平台 ToS 对数据用途的限制）需法务评估。seatmap.pro 号称 WebGL 渲染 10,000+ 座位 60fps，可作为性能指标参照。

### tickets.dev / rebrowser-seatgeek-dataset（数据侧现实来源）
- tickets.dev：商业 API（$50/月起），把 Ticketmaster/StubHub 等 15+ 平台页面解析为「场馆 SVG + section/row/seat JSON」。证明「票务页面→结构化座位图」技术上可行，但数据许可不明（抓取第三方平台），不能默认可商用；且以海外平台为主。
- rebrowser/seatgeek-dataset：SeatGeek 市场数据的每日样本（113.5M 记录），12K+ 场馆有容量与坐标、事件级「seat map availability」标记——有「场馆级目录」价值，无座位级几何，许可未标注。

### Flutter 现状（book_my_seat 等）
- pub.dev 上座位相关包基本是影院/巴士网格（book_my_seat：MIT，rows/cols 网格 + zoom/pan + SeatState 五态，约 2023 年 1.0.1 后停更，依赖 flutter_svg 1.x 偏旧）。没有任何 Flutter 侧的「场馆级（环形看台 + 分区 + 排座）」开源渲染器。结论：Flutter 端必须与 Web 端共用数据格式、独立实现渲染。

### OSM 现状
- 场馆级数据可用：`leisure=stadium` 等标签（全网 51,977 个对象），中国建筑轮廓/名称/地址/坐标可从 OSM（ODbL）取得，用于场馆档案与地图定位。
- 座位级数据没有：看台多为 `building:part`，逐座 `amenity=seat` 仅零星存在于小剧场/影院；中国场馆座位级数据基本不存在。

---

## 4. 数据格式与许可合规建议

### 4.1 核心原则：两个模型分开（版本化数据模型）

```
Venue（场馆档案：名称/别名/城市/地址/经纬度/容量/来源）
  └─ VenueLayout（固定布局：看台/分区/排/座位几何与编号规则，可复用、带版本）
       └─ EventSeatingConfiguration（演出配置：舞台方向/封闭区域/临时座席/票价分区映射，每场一版）
            └─ AWN Product + InventoryPool（售卖：可售状态/库存/占座/订单/支付）
```

- 座位图表示**空间与编号**，库存表示**当前可售状态**，两者不能混成一个数据模型。
- 边界红线：座位图里有某座位 ≠ 该演出开放销售；前端显示「可选」≠ 后台真有库存。展示层以 EventSeatingConfiguration + InventoryPool 渲染。

### 4.2 建议 schema 形状（草案）

```jsonc
// VenueLayout 片段（参考 seatmap-canvas 的 Block/Seat 与 pretix 的分层思路）
{
  "id": "layout-5327", "venueId": "venue-cn-bj-gt", "version": "2026-10-13",
  "source": "场馆官网公开信息", "accuracy": "示意",       // 示意 | 已审核
  "blocks": [{
    "id": "N1", "name": "北看台 N1", "tierColor": "#f5a623",
    "arc": { "center": [400, 300], "r0": 180, "r1": 260, "a0": 30, "a1": 60 }, // 弧形看台参数（程序化渲染用）
    "rows": [{ "id": "N1-R01", "label": "1", "seats": [
      { "id": "N1-R01-S012", "label": "12", "x": 412.5, "y": 187.2,
        "status": "saleable" }                                    // saleable | closed | accessibility
    ]}]
  }]
}
```

- 座位几何两种存法并存：直接存绝对坐标（seatmap-canvas 式，直观）或存「弧形参数 + 排内序号」（程序化生成，文件小、3D 直接可用）——推荐后者为主、前者为兼容视图。
- 座位 ID 全局稳定（学 pretix 的 GUID 思路），演出配置只引用 ID，不复制座位。

### 4.3 许可合规

- 采用路线只允许 **MIT / BSD / Apache-2.0**：seatmap-canvas、Fi-Stadium、three.js（MIT，push 2026-10-12）、react-seat-picker、book_my_seat、zenghao0219/seat-select-api（Apache-2.0）均可合规吸收/二次开发（保留版权声明与许可文件）。
- **排除**：GPL-3.0（SeatMap-JS）与 AGPL-3.0（pretix）——传染条款会污染 AWN 与 venue-seats；商业 SDK（seats.io/seatmap.pro/SeatLayer）核心闭源，不可作为独立项目依赖。
- 若使用 OSM 数据：ODbL 要求衍生数据库共享（share-alike）——建议场馆档案仅「引用 OSM 的 ID/坐标」，座位布局数据保持独立，避免整库被传染；实操前需法务确认。
- **版权红线（必须遵守）**：官方场馆座位图（大麦/猫眼/场馆运营方/票务平台的图片与矢量文件）是版权作品，venue-seats 不得复制这些文件。示例场馆数据必须从公开信息（官网文字描述、公开 PDF、OSM 轮廓）重建，无法保证准确时明确标注「示意」。tickets.dev / rebrowser 类抓取数据的许可不明，不可默认商用。

---

## 5. 精度分级（L1/L2/L3）

| 级别 | 内容 | 用途 | AWN 含义 |
|---|---|---|---|
| L1 | 场馆轮廓/看台/分区（形状、名称、相对位置） | 让用户理解场馆位置与区域；数据不全时也能合理展示 | 场馆档案页、列表页地图 |
| L2 | 分区/排号/座位编号与相对位置 | 选区、选排、展示具体座位 | 选座 MVP 可达；「按区选购」类票档 |
| L3 | 逐座精确几何 + 座位状态与实际库存映射 | 真正需要单座选择与占座的演出 | 对「买具体座位」的演出是硬要求：L3 需座位 ID ↔ 库存单元一一对应，座位图数据本身不含可售状态 |

---

## 6. 数据来源分级与半自动采集流水线

### 6.1 来源分级

| 来源 | 能给什么 | 对 AWN 的价值 |
|---|---|---|
| 场馆官网/官方座位图 PDF | 分区/看台形状/入口/部分排号 | 基础场馆档案（注意版权：只提取事实信息，不复制图） |
| 票务平台公开页面（大麦/猫眼等） | 舞台方向/分区图，有时具体座位 | 最接近真实演出配置，但每场不一定公开；抓取合规性未核实 |
| 二级票务市场挂牌 | 分区/排号/座号，辅助校验 | 校验用，≠完整座位图（SeatGeek 数据集即此类，见 3 节） |
| 开源场馆数据集（OSM、rebrowser 样本等） | 名称/地址/容量/坐标目录，通常无座位级几何 | 建场馆档案（Venue 层） |
| 用户/场馆方/主办方提供的 CAD/SVG/PDF/座位表 | 授权与质量可控的完整布局 | 长期最有价值，需签授权 |

### 6.2 半自动采集流水线

1. **发现数据源**：记录来源 URL、采集时间、授权状态、对应演出——形成可审计的来源台账。
2. **导入原始资料**：图片/PDF/SVG/后续 CAD 原样保留（不只存处理结果），建立原始文件库。
3. **自动解析 + 人工校验**：图像/SVG 识别结果不得未经校验就作为售票依据；无法识别的部分进后台编辑器人工补录。
4. **版本化与发布**：布局版本、演出配置版本、来源与审核状态；新演出继承基础布局，再单独修改舞台与开放区域（对应 4.1 的三层模型）。

---

## 7. 2D/3D 渲染技术选型

- **2D（~8000 座位）**：纯 SVG 在 8000 个 `<path>` 节点下交互重绘有压力（缩放 hover 均触发全量更新）；Canvas 一次绘制、命中检测自做（网格/四叉索引）更稳。折中方案：Canvas 渲染 + 视口裁剪 + 缩放阈值（低缩放只画分区多边形，高缩放才画座位）；seatmap-canvas 的 d3 交互层可直接借鉴。滚动/缩放手势（含触屏双指）建议统一抽象。
- **3D**：three.js（MIT）。座位用 `THREE.InstancedMesh` 按座位状态做实例颜色（可售/售罄/选中/分区色），单次 draw call 覆盖上万座位；选座用 `Raycaster` 命中 instanceId；「跳转到分区」用相机 tween（GSAP 或手写）飞行到该区中心视角；OrbitControls 提供旋转/俯仰；程序化几何（弧形看台 = 圆环分段挤出/柱面）保证无模型素材。Fi-Stadium 已验证该路线。
- **移动端降级**：Flutter 与 Web 共用数据格式，渲染独立实现（Flutter 用 CustomPainter/Canvas 画 2D，或嵌入 WebView 复用 Web 渲染器）；低端机/性能不达标时 3D 降级为 2D。
- **FPS 目标**：Web 端 60fps 为中端目标、30fps 为下限（Fi-Stadium 的 adaptive pixel ratio 0.75x–2.0x 思路可借鉴：动态降分辨率保帧率）。3D 仅对单座选择演出开启；L1/L2 展示默认 2D。

---

## 8. 待用户拍板事项

1. **vendor vs 自建**：直接 fork/内嵌 seatmap-canvas（MIT，省 2D 交互，但受其模型限制）还是自建 venue-seats（推荐，模型自由）。——**已代决（2026-10-16，巡检单 FW1016D 授权，用户可推翻）：自建 venue-seats**。理由：座位图数据是售票依据，必须装下 §4.1 三层模型（基础布局 / 演出配置 / 舞台）+ 版本化 + 来源台账 + 审核状态；seatmap-canvas 的画布交互模型承载不了这些数据约束，fork 后仍要重写数据层，等于双份维护。
2. **venue-seats 的开源许可**：建议 MIT 或 Apache-2.0（决定能否吸收 Zenghao0219 的 Apache 代码及三方依赖）。——**已代决（2026-10-16，FW1016D）：Apache-2.0**。理由：保留吸收 Apache 代码与三方依赖的合法路径（MIT 与 Apache 代码不能互吸）；若验证批次后确认零吸收，可降级 MIT，届时再议不迟。
3. **首个验证批次（用户 2026-10-12 已定方向，先于立项执行）**：先验证三个问题——①能否找到 5 个目标场馆可用、来源明确的基础座位图；②能否把它们整理成统一的分区数据，并在 AWN 前端正确展示；③对同一个场馆的两场不同演出，能否复用基础布局、只修改舞台和开放区域配置。三个问题成立，再决定是否抽独立项目 venue-seats。验收口径：每张图须能转成可复用、可校验、可版本化的数据；首批收集到的图数量不作为验收项。
4. **首批 20–50 个高频大型场馆试点名单**（用户 2026-10-12 已定规模；具体名单如 国家体育场/北京工人体育场/上海体育场/深圳湾体育中心 等，需用户定）。
5. **MVP 精度目标：已拍板（用户 2026-10-12）**——L1/L2 起步，先实现选区、选排，不强求逐座精确；L3（逐座+库存映射）为后续等级。数据流水线深度按 L2 设计。
6. **3D 是否进 MVP**：已定 P2——原型先出 2D，3D 给线框示意，等 2D 过目后再做 3D 原型；一期只做 2D + 数据模型。
7. **是否采购商业数据**（tickets.dev 等）：成本、合规与「数据锁定」风险评估需用户/法务拍板；不建议作为唯一来源。
8. **后台编辑器排期**：3.3 步「人工校验」需要场馆数据编辑器，是 venue-seats scope 内还是 AWN 自建。——**已代决（2026-10-16，FW1016D）：AWN 自建**。理由：编辑器天然依赖 AWN 运营鉴权（X-Admin-Token）、场次 / 商品模型与既有 admin 视图体系（铺座视图同构），放进 venue-seats 会让数据项目反向依赖宿主应用；venue-seats 保持纯数据零宿主依赖，编辑器随 AWN 排期。

FW1016D 注：#4 场馆试点名单属**用户点名清单**、#7 商业数据采购涉**资金与法务**，均超出代决授权范围，照实维持卡点，不代决。
