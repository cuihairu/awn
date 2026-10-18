# 场馆布局数据模型：缺口分析与设计方案

- 日期：2026-10-16
- 触发：执行单「上海场馆优先实现」（用户 2026-10-16）——虹口足球场做透 + 其它上海场馆 + 异形边界样本
- 样本：`docs/data/venues/`（4 份，全部过 `validate.mjs` 校验）
- 原型：`docs/design/prototypes/web/venue-layout.html`（可点、可缩放）
- 关联：`docs/research/venue-seatmaps.md` §4.1 三层模型、§8 已代决事项；`docs/design/seatmap-3d-plan.md` S2 坐标契约

---

## 1. 结论：现有 schema 表达不了场馆布局

`validate.mjs` 把这条结论固化成可复跑断言：4 份样本共 17 个字段，**14 个在当前数据库 schema 里无处落库**。

| 样本字段 | 当前承载位置 | 说明 |
|---|---|---|
| `id` | `venue.venue_code` | ✓ |
| `name.zh` | `venue.name` | ✓ |
| `city.zh` | `venue.city` | ✓ |
| `name.en` / `city.en` | ✗ 无处落库 | 双语名 |
| `type` | ✗ 无处落库 | 足球场 / 室内馆 |
| `priority` / `boundaryCase` / `aliases` | ✗ 无处落库 | 采集元数据 |
| `capacity` | ✗ 无处落库 | 容量（含多口径） |
| `stands` / `boxes` | ✗ 无处落库 | 看台数 / 包厢数 |
| `field` / `floor` | ✗ 无处落库 | 场地与内场形状尺寸 |
| `areas` | ✗ 无处落库 | 占地 / 建筑面积 |
| `years` | ✗ 无处落库 | 建成 / 改建年份 |
| `notPublic` | ✗ 无处落库 | 缺口清单（必须入库才有审计价值） |

`venue` 表（V4 + V19 经纬度 + V26 地图图）只有名称 / 城市 / 地址 / 坐标 / 地图图；`platform_seats`（V17）是**场次级**座位行，只有 `section_name / row_label / seat_label` 文本，**没有几何坐标**，也没有场馆级布局。

## 2. 数据可得性结论（三问①的答案）

对 4 个上海场馆逐一取证（来源见 `docs/data/venues/README.md` 台账）：

| 场馆 | 公开来源能给 | 公开来源给不了 |
|---|---|---|
| 虹口足球场 | 座位数（两个口径）、看台 22、包厢 47、场地 105×70、面积、年份 | 看台编号与命名、层数、各区座位数、排座编号、朝向、包厢位置 |
| 浦东足球场 | 座位数 37,000、建成日期 | 看台数量、分区结构、场地尺寸（条目未写） |
| 上海体育场 | 座位数 72,000 / 改建前 56,842、建成与改建年份 | 看台数量、分区结构、场地尺寸 |
| 上海体育馆（万体馆） | 体育 12,000 / 文艺 10,000、内场表面、年份、别名 | 看台数量与层数、分区结构、内场面积与形状 |

**结论：公开渠道只能给到场馆级事实，给不到分区 / 排 / 座级别。** 这与 `venue-seatmaps.md` §6.1 一致——完整布局需要场馆方 / 主办方的 CAD、SVG、PDF 或座位表（来源分级第 5 档）。因此：

- 三问①「能否找到 5 个目标场馆可用、来源明确的基础座位图」——**公开渠道不成立**，需场馆方供给或采购（采购属资金与法务卡点，见 §8 #7）。
- 本批次**不编造**分区编号与座席编号：样本里没有 `sections` / `rows` / `seats` 字段，`validate.mjs` 对出现这些字段的样本直接判 FAIL。
- 原型里的看台排布、内场轮廓是**重建示意**（虚线标注），不是官方图纸。

## 3. 设计提案

沿用 `venue-seatmaps.md` §4.1 的三层模型，把「场馆级事实」和「布局几何」分开落库。

### 3.1 场馆级事实（本批次可落，V27 起）

```sql
-- V27__venue_profile.sql
ALTER TABLE venue ADD COLUMN type VARCHAR(32);            -- FOOTBALL_STADIUM / INDOOR_ARENA
ALTER TABLE venue ADD COLUMN name_en VARCHAR(128);
ALTER TABLE venue ADD COLUMN city_en VARCHAR(64);
ALTER TABLE venue ADD COLUMN aliases TEXT[];              -- 万体馆 / 万人体育馆 / 上海大舞台
ALTER TABLE venue ADD COLUMN opened_year INT;            -- 建成年份（可空）
ALTER TABLE venue ADD COLUMN renovated_years INT[];      -- 改建年份（可空）
ALTER TABLE venue ADD COLUMN site_area_m2 NUMERIC(12,2);
ALTER TABLE venue ADD COLUMN building_area_m2 NUMERIC(12,2);

CREATE TABLE venue_capacity (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id    BIGINT NOT NULL REFERENCES venue (id),
    value       INT NOT NULL CHECK (value > 0),
    scope       VARCHAR(16) NOT NULL CHECK (scope IN ('OVERALL', 'SPORT_EVENT', 'ART_EVENT')),
    note        TEXT NOT NULL,                 -- 口径说明，冲突时必须写明差异
    source      VARCHAR(512) NOT NULL,         -- 公开 URL
    accessed_on DATE NOT NULL,
    UNIQUE (venue_id, scope, value, source)    -- 同口径多来源可并存，不静默择一
);
```

要点：**容量是多口径不是一值**。虹口 33,060 / 35,000 并存，万体馆 12,000 / 10,000 按 scope 分列——单一 `capacity` 字段表达不了，必须独立成表并强制 `note` + `source`。

### 3.2 布局几何（需场馆方数据，本批次只建表不填）

```sql
CREATE TABLE venue_layout (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id    BIGINT NOT NULL REFERENCES venue (id),
    version     VARCHAR(32) NOT NULL,          -- 布局版本号
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'REVIEWED', 'PUBLISHED')),
    source      VARCHAR(512),                  -- 原始资料出处（授权后填）
    accuracy    VARCHAR(16) NOT NULL DEFAULT 'SCHEMATIC'  -- SCHEMATIC | VERIFIED
                CHECK (accuracy IN ('SCHEMATIC', 'VERIFIED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (venue_id, version)
);

CREATE TABLE layout_stand (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    layout_id     BIGINT NOT NULL REFERENCES venue_layout (id),
    stand_no      VARCHAR(32) NOT NULL,        -- 官方编号；未公开则 NULL
    name          VARCHAR(128),
    layer         INT,                          -- 层数；未公开则 NULL
    orientation   VARCHAR(16),                  -- NORTH / SOUTH / EAST / WEST；未公开则 NULL
    seat_count    INT,                          -- 该区座位数；未公开则 NULL
    -- 几何：弧形参数（程序化渲染用，见 venue-seatmaps.md §4.2）
    geom_cx       DOUBLE PRECISION, geom_cy DOUBLE PRECISION,
    geom_r0       DOUBLE PRECISION, geom_r1 DOUBLE PRECISION,
    geom_a0       DOUBLE PRECISION, geom_a1 DOUBLE PRECISION,
    UNIQUE (layout_id, stand_no)
);

CREATE TABLE layout_box (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    layout_id  BIGINT NOT NULL REFERENCES venue_layout (id),
    box_no     VARCHAR(32),
    seat_count INT,
    geom       JSONB                           -- 位置未公开则 NULL
);
```

要点：**未公开就是 NULL，不用占位值冒充**。`stand_no` / `seat_count` / `geom` 全部可空，`accuracy` 区分「示意」与「已审核」。

### 3.3 演出配置层（每场一版，复用基础布局）

```sql
CREATE TABLE event_seating_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES event (id),
    layout_id       BIGINT NOT NULL REFERENCES venue_layout (id),
    stage_geom      JSONB,                     -- 舞台位置 / 朝向
    open_stands     TEXT[],                    -- 本场开放看台
    closed_stands   TEXT[],                    -- 本场封闭看台
    temp_seats      JSONB,                     -- 内场临时座席
    version         VARCHAR(32) NOT NULL,
    UNIQUE (event_id, version)
);
```

### 3.4 与 `platform_seats`（V17）的关系

- `platform_seats` **保持场次级不变**：它是「平台的座位」交易模型（`seat_no` 唯一、状态机、挂 `product_code` / `session_code`），不承担场馆布局。
- 布局表是**空间与编号**，`platform_seats` 是**可售状态**——沿用 §4.1 边界：座位图里有某座位 ≠ 该演出开放销售。
- 逐座精确（L3）落地时，`platform_seats` 增 `layout_stand_id` 外键或坐标字段（二选一，见 §4）。

### 3.5 与 `seatmap-3d-plan.md` S2 坐标契约的衔接

S2 说「座位坐标可选化：`platform_seats` 增坐标字段或独立坐标表；`null` = 坐标缺失，前端回退 S1 网格」。本方案的 `layout_stand.geom_*` 弧形参数正是 S2 需要的坐标来源：

- 2D 渲染器读 `layout_stand` 的弧形参数生成看台多边形（替代现在 `SeatMap.tsx` 的程序化网格）。
- 3D 渲染器读同一份参数做圆环分段挤出（`venue-seatmaps.md` §7 的 `InstancedMesh` 路线）。
- 坐标缺失（`geom_*` 全 NULL）时前端回退现有网格，不破坏 2D 契约。

## 4. 待拍板（不阻塞本批次）

1. **逐座坐标存法**：`platform_seats` 加 `x`/`y` 列，还是独立坐标表？S2 留的两条路，需 L3 启动前定。——补记（2026-10-18，S2 契约先行已代决落地）：V27 已为 `platform_seats` 加可空 `pos_x`/`pos_y` 作为**契约载体**（`SeatView`/公开 `SeatMapItem` 透出可选 `x`/`y`，null 回退网格）；本条「最终存法」仍开放——若 L3 选独立坐标表，V27 两列保持 NULL，公开契约不变。
2. **布局数据导入路径**：场馆方给 CAD/SVG/PDF 后的解析 + 人工校验流程（`venue-seatmaps.md` §6.2 第 3 步），随后台编辑器排期（§8 #8 已代决 AWN 自建）。
3. **采购商业数据**（tickets.dev 等）：资金与法务卡点，维持挂起。

## 5. 验收

- 4 份样本过 `node docs/data/venues/validate.mjs`（已 PASS，含 14 字段无处落库断言）。
- 原型 `venue-layout.html` 真实渲染：虹口 22 看台可点、万体馆内场可点、滚轮与按钮缩放、拖拽平移。
- 三问① 有明确答案：公开渠道不成立，需场馆方供给；本批次不编造座席级数据。
