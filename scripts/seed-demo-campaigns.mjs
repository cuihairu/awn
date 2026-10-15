#!/usr/bin/env node
/**
 * 三套专场实例种子（todo 141 余留：F1 / 周杰伦 / 周传雄）。
 *
 * 走专场管理写接口把 scripts/seed/demo-campaigns.mjs 的三份原型占位内容
 * 创建为已发布专场：create → draft → validate → publish
 * （契约见 docs/api/contracts.md「专场列表与内容接口」，写操作需 X-Admin-Token）。
 *
 * 用法：
 *   AWN_API_BASE=http://localhost:8080 AWN_ADMIN_TOKEN=awn-admin-dev-token \
 *     node scripts/seed-demo-campaigns.mjs
 * 选项：
 *   --dry-run   只打印各实例将发送的载荷，不调用接口
 *
 * 环境变量：
 *   AWN_API_BASE     后端地址，默认 http://localhost:8080
 *   AWN_ADMIN_TOKEN  运营令牌，默认 awn-admin-dev-token（开发默认值）
 *
 * 幂等：专场已存在时复用（重新 draft → validate → publish 覆盖草稿状态）。
 * 内容出处：docs/design/prototypes/web/activity-{f1,jay,zcx}.html（原型过目已代决，数据为示例）。
 */

import { demoCampaigns } from './seed/demo-campaigns.mjs'

const baseUrl = process.env.AWN_API_BASE ?? 'http://localhost:8080'
const adminToken = process.env.AWN_ADMIN_TOKEN ?? 'awn-admin-dev-token'
const dryRun = process.argv.includes('--dry-run')

async function call(method, path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'X-Admin-Token': adminToken,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${method} ${path} → ${response.status} ${text.slice(0, 400)}`)
  }
  return text ? JSON.parse(text) : null
}

async function seedInstance(instance) {
  const { campaignCode, title, subtitle, config, source } = instance

  let status = 'created'
  try {
    const existing = await call('GET', `/api/v1/campaigns/${campaignCode}`)
    status = `已存在（${existing.status ?? '未知状态'}），复用`
  } catch {
    await call('POST', '/api/v1/campaigns', { campaignCode, title, subtitle })
  }

  await call('POST', `/api/v1/campaigns/${campaignCode}/draft`, { title, subtitle, config })
  await call('POST', `/api/v1/campaigns/${campaignCode}/validate`)
  const published = await call('POST', `/api/v1/campaigns/${campaignCode}/publish`)

  return { code: campaignCode, source, status, revision: published?.revisionId ?? null }
}

if (dryRun) {
  for (const instance of demoCampaigns) {
    console.log(
      `${instance.campaignCode}: ${instance.title}\n  内容出处: ${instance.source}\n` +
        `  将创建草稿配置: ${instance.config.sections.length} 个区块（${instance.config.template}）`,
    )
  }
  process.exit(0)
}

const results = []
for (const instance of demoCampaigns) {
  try {
    results.push(await seedInstance(instance))
    console.log(`✓ ${results.at(-1).code} 已发布（${results.at(-1).status}）`)
  } catch (error) {
    console.error(`✗ ${instance.campaignCode} 失败: ${error.message}`)
    process.exitCode = 1
  }
}
console.log(
  results.length > 0
    ? `完成 ${results.length}/${demoCampaigns.length} 套：${results.map((r) => `${r.code}#${r.revision}`).join(' ')}`
    : '无成功实例',
)
