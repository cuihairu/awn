import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid(
  defineConfig({
    lang: 'zh-CN',
    base: '/awn/',
    title: '麦芒网 AWN',
    description: '票务交易与情报平台 · 架构规范与实施文档',
    head: [['link', { rel: 'icon', type: 'image/svg+xml', href: '/awn/logo.svg' }]],

    themeConfig: {
      logo: '/logo.svg',
      siteTitle: '麦芒网 AWN',

      nav: [
        { text: '首页', link: '/' },
        { text: '架构', link: '/architecture/system-overview' },
        { text: 'API 契约', link: '/api/contracts' },
        { text: '设计', link: '/design/prototypes' },
        { text: '实施', link: '/implementation-plan' },
        { text: 'ADR', link: '/adr/0001-modular-monolith' },
      ],

      sidebar: [
        {
          text: '架构',
          items: [
            { text: '系统总览', link: '/architecture/system-overview' },
            { text: '模块边界', link: '/architecture/module-boundaries' },
            { text: '领域模型', link: '/architecture/domain-model' },
            { text: '交易全链模型', link: '/architecture/trading-model' },
            { text: '专场渲染', link: '/architecture/campaign-rendering' },
            { text: '库存一致性', link: '/architecture/inventory-consistency' },
            { text: '订单与支付状态机', link: '/architecture/payment-state-machine' },
            { text: '异步与 Outbox', link: '/architecture/async-outbox' },
            { text: '情报数据隔离', link: '/architecture/intelligence-isolation' },
          ],
        },
        {
          text: '接口',
          items: [{ text: 'API 契约', link: '/api/contracts' }],
        },
        {
          text: '设计',
          items: [
            { text: '界面原型图', link: '/design/prototypes' },
            { text: 'UI 设计系统', link: '/design/ui-system' },
            { text: '3D 座位图方案', link: '/design/seatmap-3d-plan' },
            { text: 'Logo 候选方案', link: '/design/logo-candidates' },
          ],
        },
        {
          text: '实施',
          items: [
            { text: '实施计划', link: '/implementation-plan' },
            { text: '测试清单', link: '/testing/checklist' },
            { text: '压测报告', link: '/testing/load-test-report' },
            { text: '部署结构', link: '/deployment/structure' },
            { text: '备份与恢复', link: '/deployment/backup-restore' },
            { text: '生产部署手册', link: '/deployment/production' },
            { text: '文档一致性审计', link: '/审计-文档一致性' },
          ],
        },
        {
          text: '架构决策记录',
          items: [
            { text: 'ADR-0001 模块化单体优先', link: '/adr/0001-modular-monolith' },
            { text: 'ADR-0002 PostgreSQL 库存权威', link: '/adr/0002-postgresql-inventory-authority' },
            { text: 'ADR-0003 Outbox + Worker 异步', link: '/adr/0003-outbox-worker-async' },
            { text: 'ADR-0004 情报数据隔离', link: '/adr/0004-intelligence-isolation' },
            { text: 'ADR-0005 专场版本化发布', link: '/adr/0005-campaign-revision-publish' },
            { text: 'ADR-0006 多端 monorepo', link: '/adr/0006-multi-client-monorepo' },
            { text: 'ADR-0007 JPA + 关键路径原生 SQL', link: '/adr/0007-jpa-with-native-sql' },
            { text: 'ADR-0008 构建工具 Maven', link: '/adr/0008-maven-build' },
          ],
        },
      ],

      search: {
        provider: 'local',
        options: {
          translations: {
            button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
            modal: {
              noResultsText: '没有找到结果',
              resetButtonTitle: '清除查询',
              footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
            },
          },
        },
      },

      outline: { level: [2, 3], label: '本页目录' },
      docFooter: { prev: '上一页', next: '下一页' },
      lastUpdated: { text: '最后更新', formatOptions: { dateStyle: 'short' } },
      returnToTopLabel: '回到顶部',
      sidebarMenuLabel: '菜单',

      footer: {
        message: 'AWN · 票务交易与情报平台',
        copyright: 'Copyright © 2026 麦芒网',
      },
    },

    mermaid: {},
    lastUpdated: true,
  })
)
