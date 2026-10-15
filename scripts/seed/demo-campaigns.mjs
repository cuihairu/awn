/**
 * 三套专场实例种子内容（todo 141 余留：F1 / 周杰伦 / 周传雄）。
 *
 * 内容逐字取自已批准原型（原型过目已代决 2026-10-14 巡检授权，原型 demo-note
 * 标注「数据为示例」），不改写、不补充原型之外的事实：
 *   - docs/design/prototypes/web/activity-f1.html
 *   - docs/design/prototypes/web/activity-jay.html
 *   - docs/design/prototypes/web/activity-zcx.html
 *
 * 素材红线：不生成明星肖像 / 照片 / logo 代餐——实例不含 imageGallery / videoEmbed
 * 区块与任何媒体引用键（url / src / poster / assetId），视觉以文字占位。
 *
 * 场次列表（eventList）与票档（ticketGrid）区块引用占位活动 id '0'（不存在）：
 * 公开解析层（apps/web/lib/campaign-resolve.ts）查不到引用按待接入渲染并说明原因，
 * 不造假数据；运营在目录创建真实活动后，回填真实 eventId 再走 draft → validate → publish。
 *
 * 结构约束见后端 CampaignConfigValidator（modules/awn-campaign）：schemaVersion 1、
 * 区块类型白名单、id 唯一、无 script / 内嵌 HTML。
 */

/** F1 实例（原型 activity-f1.html，区块 2 赛程与车队为文字占位） */
const f1 = {
  campaignCode: 'f1-2026-china',
  title: '2026 F1 中国大奖赛',
  subtitle: '上海国际赛车场 · 10 月 30 日 — 11 月 1 日',
  source: 'docs/design/prototypes/web/activity-f1.html',
  config: {
    schemaVersion: 1,
    template: 'motorsport',
    seo: {
      title: '2026 F1 中国大奖赛',
      description: '周末赛 · 资格赛 · 正赛三档观赛票 · 同一票池统一结算',
    },
    sections: [
      {
        id: 'hero',
        type: 'heroBanner',
        props: {
          title: '2026 F1 世界锦标赛 中国大奖赛 · 上海国际赛车场',
          subtitle: '周末赛 · 资格赛 · 正赛三档观赛票 · 同一票池统一结算',
        },
      },
      { id: 'event-list', type: 'eventList', props: { eventIds: ['0'] } },
      { id: 'tickets', type: 'ticketGrid', props: { eventIds: ['0'] } },
      {
        id: 'schedule',
        type: 'richText',
        props: {
          title: '本周赛程',
          paragraphs: [
            '10 月 30 日 周五：自由练习 + 排位赛（练习 13:00–14:00 · 排位赛 14:00–15:00）',
            '10 月 31 日 周六：冲刺赛（发车仪式 11:30 · 正赛 14:00）',
            '11 月 1 日 周日：正赛 56 圈（发车 15:00 · 预计 1 小时 40 分钟）',
            '赛季积分前列（文字占位）：维斯塔潘 412 分 / 汉密尔顿 318 分 / 皮亚斯特里 245 分 / 诺里斯 241 分（示例）',
          ],
        },
      },
      {
        id: 'venue',
        type: 'richText',
        props: {
          title: '场馆与入场',
          paragraphs: [
            '上海国际赛车场 · 上海市嘉定区银都西路 2080 号 · 坐标 31.3332, 121.2670',
            '地铁 11 号线「赛车场」站 2 号口步行 8 分钟',
            '自驾：北门停车场 P1/P2，正赛日建议 13:00 前到达',
            '大巴接驳：虹桥枢纽、上海火车站定点发车',
            '散场时间：正赛结束约 1.5 小时后',
          ],
        },
      },
      {
        id: 'rules-purchase',
        type: 'rulePanel',
        props: {
          title: '购票须知',
          rules: [
            '一人限购 6 张，实名观演人信息不可修改',
            '儿童 1.2 米以下免票但需成人陪同（示例）',
            '凭身份证原件入场，人证票一致',
            '场馆内禁止带玻璃瓶、自拍杆与专业摄影器材',
          ],
        },
      },
      {
        id: 'rules-refund',
        type: 'rulePanel',
        props: {
          title: '退换规则',
          rules: [
            '开票后 24 小时内可申请全额退款（示例）',
            '演出开始前 7 天起不接受退款与改签',
            '因不可抗力延期或取消，票务方负责全额退款',
            '退换手续费与退改次数以订单页面为准',
          ],
        },
      },
      {
        id: 'seat-entry',
        type: 'noticeBar',
        props: {
          message: '选座购票（正赛日选座票）· 2D / 3D 可切换座位图 · 共享同一批座位数据',
        },
      },
    ],
  },
}

/** 周杰伦实例（原型 activity-jay.html，演出信息为文字占位，不含艺人肖像） */
const jay = {
  campaignCode: 'jay-carnival-2026',
  title: '周杰伦「嘉年华」世界巡回演唱会',
  subtitle: '上海站 · 11 月 6 日 — 11 月 8 日',
  source: 'docs/design/prototypes/web/activity-jay.html',
  config: {
    schemaVersion: 1,
    template: 'concert',
    seo: {
      title: '周杰伦「嘉年华」世界巡回演唱会',
      description: '上海站 · 三场连开 · 全场大合唱 · 实名一票一证',
    },
    sections: [
      {
        id: 'hero',
        type: 'heroBanner',
        props: {
          title: '周杰伦「嘉年华」世界巡回演唱会 · 上海站',
          subtitle: '三场连开 · 全场大合唱 · 实名一票一证',
        },
      },
      { id: 'event-list', type: 'eventList', props: { eventIds: ['0'] } },
      { id: 'tickets', type: 'ticketGrid', props: { eventIds: ['0'] } },
      {
        id: 'show-info',
        type: 'richText',
        props: {
          title: '演出信息',
          paragraphs: [
            '时长约 150 分钟 · 全程约 18 首曲目（示例）',
            '开演 19:30 · 18:30 开始入场 · 散场 22:30 后',
            '现场为文字与几何视觉呈现，不含艺人肖像与品牌素材（素材红线）',
          ],
        },
      },
      {
        id: 'venue',
        type: 'richText',
        props: {
          title: '场馆与入场',
          paragraphs: [
            '虹口足球场 · 上海市虹口区花园石桥路 155 号 · 坐标 31.2640, 121.4890',
            '地铁 3/8 号线「虹口足球场」站 2 号口',
            '散场：演出结束后 90 分钟内，东、西门均开',
            '实名观演人信息与证件必须一致，人证票一致入场',
            '安检后不可离场，离场视为主动放弃',
            '禁止携带液体、玻璃容器、专业摄录设备',
          ],
        },
      },
      {
        id: 'rules-purchase',
        type: 'rulePanel',
        props: {
          title: '购票须知',
          rules: [
            '每张订单限购 4 张，同一证件每场限 1 张',
            '内场与看台低区座位区为选座，需进入座位图选择',
            '儿童一律凭票入场，无儿童票',
            '电子票入场，支持二维码与身份证刷证入场',
          ],
        },
      },
      {
        id: 'rules-refund',
        type: 'rulePanel',
        props: {
          title: '退换规则',
          rules: [
            '开票后 48 小时内可全额退款（示例）',
            '演出开始前 3 天起不接受退款与改签',
            '因演出延期或取消，票务方负责全额退款',
            '因本人原因无法观演，可尝试官方转赠（单次免费）',
          ],
        },
      },
      {
        id: 'seat-entry',
        type: 'noticeBar',
        props: {
          message: '选座购票（内场 / 看台选座区）· 2D / 3D 可切换座位图 · 共享同一批座位数据',
        },
      },
    ],
  },
}

/** 周传雄实例（原型 activity-zcx.html，演出信息为文字占位，不含艺人肖像） */
const zcx = {
  campaignCode: 'zcx-shibuzhigui-2026',
  title: '周传雄「时不知归」巡回演唱会',
  subtitle: '杭州站 · 11 月 14 日 — 11 月 15 日',
  source: 'docs/design/prototypes/web/activity-zcx.html',
  config: {
    schemaVersion: 1,
    template: 'concert',
    seo: {
      title: '周传雄「时不知归」巡回演唱会',
      description: '杭州站 · 两晚连唱 · 体育馆场 · 实名一票一证',
    },
    sections: [
      {
        id: 'hero',
        type: 'heroBanner',
        props: {
          title: '周传雄「时不知归」巡回演唱会 · 杭州站',
          subtitle: '两晚连唱 · 体育馆场 · 实名一票一证',
        },
      },
      { id: 'event-list', type: 'eventList', props: { eventIds: ['0'] } },
      { id: 'tickets', type: 'ticketGrid', props: { eventIds: ['0'] } },
      {
        id: 'show-info',
        type: 'richText',
        props: {
          title: '演出信息',
          paragraphs: [
            '演出时长约 120 分钟 · 入场时间 18:30 开始 · 场次形式为体育馆场两晚连唱',
            '舞台为环绕式布置，A 区存在遮挡区（示例，具体以演出配置版本为准）',
          ],
        },
      },
      {
        id: 'venue',
        type: 'richText',
        props: {
          title: '场馆与入场',
          paragraphs: [
            '黄龙体育中心体育馆 · 浙江省杭州市西湖区黄龙路 1 号 · 坐标 30.2745, 120.1307',
            '地铁 2/4 号线「黄龙体育中心」站 1 号口',
            '场馆容量约 1.8 万（示例）· 散场后 60 分钟内离场',
            '实名观演人信息与证件必须一致',
            '入场后请勿中途离场，离场后凭原票重新排队安检',
            '禁止携带自拍杆、专业摄录设备、气球',
          ],
        },
      },
      {
        id: 'rules-purchase',
        type: 'rulePanel',
        props: {
          title: '购票须知',
          rules: [
            '每张订单限购 4 张，同一证件每场限 1 张',
            '内场与看台 1 层东区、西区为选座',
            '不选座票档由系统自动配座，出票后告知座位',
            '电子票支持二维码与刷证入场',
          ],
        },
      },
      {
        id: 'rules-refund',
        type: 'rulePanel',
        props: {
          title: '退换规则',
          rules: [
            '开票后 24 小时内可全额退款（示例）',
            '演出开始前 5 天起不接受退款与改签',
            '演出延期或取消由票务方全额退款',
            '官方转赠：每场每人每单限 1 次（示例）',
          ],
        },
      },
      {
        id: 'seat-entry',
        type: 'noticeBar',
        props: {
          message: '选座购票（内场 / 看台 1 层东西区）· 2D / 3D 可切换座位图',
        },
      },
    ],
  },
}

export const demoCampaigns = [f1, jay, zcx]
