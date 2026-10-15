#!/usr/bin/env node
/**
 * 场馆样本校验（docs/data/venues/*.json）。
 *
 * 两段校验，零第三方依赖（不引入 jsonschema 包，避免动 package.json）：
 * 1. 结构校验：按同目录 schema.json 声明的规则递归检查，报错带字段路径。
 * 2. DB 覆盖度校验：把样本字段逐条对照当前数据库 schema
 *    （V4 venue 表 + V19/V26 venue 列 + V17 platform_seats），
 *    输出「无处落库」清单——本仓现有 schema 表达不了场馆布局，这是本次执行单的
 *    结论之一，脚本把它固化成可复跑的断言（缺口必须 > 0，否则说明 schema 已变）。
 *
 * 用法：node docs/data/venues/validate.mjs   （非零退出 = 样本不合格或断言不符）
 */

import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const schema = JSON.parse(readFileSync(join(here, 'schema.json'), 'utf8'))

/** 极小 schema 子集解释器：覆盖本目录 schema.json 实际用到的关键字。 */
/** JS 无 integer 类型，typeof 1 === 'number'；integer 需 Number.isInteger 判定。 */
function matchesType(value, type) {
  if (type === 'integer') return typeof value === 'number' && Number.isInteger(value)
  if (type === 'object') return typeof value === 'object' && value !== null && !Array.isArray(value)
  if (type === 'array') return Array.isArray(value)
  if (type === 'null') return value === null
  if (type === 'boolean') return typeof value === 'boolean'
  const actual = typeof value
  return actual === type
}

function check(value, node, path, errors) {
  const at = (k) => `${path}.${k}`
  if (node.type) {
    if (!matchesType(value, node.type)) {
      return errors.push(`${path}: 期望 ${node.type}，实际 ${Array.isArray(value) ? 'array' : value === null ? 'null' : typeof value}`)
    }
  }
  if (node.oneOf) {
    if (!node.oneOf.some((alt) => {
      const tmp = []
      check(value, alt, path, tmp)
      return tmp.length === 0
    })) return errors.push(`${path}: 不匹配任一 oneOf 分支`)
  }
  if (node.enum && !node.enum.includes(value)) {
    return errors.push(`${path}: 取值 ${JSON.stringify(value)} 不在枚举 ${node.enum.join('/')}`)
  }
  if (node.pattern && typeof value === 'string' && !new RegExp(node.pattern).test(value)) {
    return errors.push(`${path}: ${value} 不匹配模式 ${node.pattern}`)
  }
  if (typeof value === 'number') {
    if (node.minimum !== undefined && value < node.minimum) errors.push(`${path}: ${value} < minimum ${node.minimum}`)
    if (node.exclusiveMinimum !== undefined && value <= node.exclusiveMinimum) {
      errors.push(`${path}: ${value} 未大于 exclusiveMinimum ${node.exclusiveMinimum}`)
    }
  }
  if (typeof value === 'string' && node.minLength !== undefined && value.length < node.minLength) {
    errors.push(`${path}: 长度不足 ${node.minLength}`)
  }
  if (Array.isArray(value)) {
    if (node.minItems !== undefined && value.length < node.minItems) {
      errors.push(`${path}: 少于 minItems ${node.minItems}`)
    }
    if (node.items) value.forEach((item, i) => check(item, node.items, at(`[${i}]`), errors))
  }
  if (node.type === 'object' && typeof value === 'object' && value !== null) {
    const required = node.required ?? []
    for (const key of required) {
      if (!(key in value)) errors.push(`${path}: 缺必填字段 ${key}`)
    }
    if (node.minProperties !== undefined && Object.keys(value).length < node.minProperties) {
      errors.push(`${path}: 属性数少于 minProperties ${node.minProperties}`)
    }
    const props = node.properties ?? {}
    for (const [key, child] of Object.entries(value)) {
      if (node.additionalProperties === false && !(key in props)) {
        errors.push(`${path}: 未知字段 ${key}`)
        continue
      }
      if (key in props) check(child, props[key], at(key), errors)
    }
  }
}

/** 样本字段 → 当前 DB schema 承载位置（null = 无处落库）。对照迁移文件人工核对后固化。 */
const DB_COVERAGE = {
  'id': 'venue.venue_code',
  'name.zh': 'venue.name',
  'city.zh': 'venue.city',
  'name.en': null,
  'city.en': null,
  'type': null,
  'priority': null,
  'boundaryCase': null,
  'aliases': null,
  'capacity': null,
  'stands': null,
  'boxes': null,
  'field': null,
  'floor': null,
  'areas': null,
  'years': null,
  'notPublic': null,
}

const venueJsons = readdirSync(here)
  .filter((f) => f.endsWith('.json') && f !== 'schema.json')
  .sort()

if (venueJsons.length === 0) {
  console.error('没有场馆样本可校验')
  process.exit(1)
}

let failed = false
const ids = new Set()
const unmapped = new Map()

for (const file of venueJsons) {
  const record = JSON.parse(readFileSync(join(here, file), 'utf8'))
  const errors = []
  check(record, schema, file, errors)

  if (record.id && basename(file, '.json') !== record.id) {
    errors.push(`${file}: 文件名与 id 不一致（id=${record.id}）`)
  }
  if (record.id && ids.has(record.id)) errors.push(`${file}: id 重复 ${record.id}`)
  if (record.id) ids.add(record.id)

  // 容量口径冲突必须显式记录，不允许静默择一
  const values = new Set((record.capacity ?? []).map((c) => c.value))
  if (values.size > 1 && !(record.capacity ?? []).every((c) => String(c.note).includes('不一致') || c.note.length > 0)) {
    errors.push(`${file}: 存在多个容量口径但未逐条说明来源差异`)
  }
  // 座席级数据红线：样本里不允许出现分区编号/排座列表
  const compact = JSON.stringify(record)
  for (const banned of ['"sections"', '"rows"', '"seats"', '"seatNo"']) {
    if (compact.includes(banned)) errors.push(`${file}: 含座席级字段 ${banned}，违反“不编造精确座位号”红线`)
  }

  for (const [field, target] of Object.entries(DB_COVERAGE)) {
    if (Object.hasOwn(record, field.split('.')[0]) && target === null) {
      unmapped.set(field, unmapped.get(field) ?? [])
      unmapped.get(field).push(file)
    }
  }

  console.log(`${errors.length === 0 ? 'PASS' : 'FAIL'} ${file}`)
  for (const e of errors) console.log(`     ${e}`)
  if (errors.length) failed = true
}

console.log('\n样本字段 → 当前 DB schema 承载位置（venue 表 V4/V19/V26 + platform_seats V17）：')
for (const [field, target] of Object.entries(DB_COVERAGE)) {
  console.log(`  ${target === null ? '✗ 无处落库' : `✓ ${target}`.padEnd(14)} ${field}`)
}

console.log(`\n无处落库字段数：${unmapped.size}`)
if (unmapped.size === 0) {
  console.error('断言不符：所有样本字段都能落库，说明 DB schema 已变化，需更新本矩阵')
  failed = true
}

if (failed) process.exit(1)
console.log('\n样本结构校验通过；缺口断言成立（现有 schema 无法表达场馆布局，见 docs/design/venue-layout-schema.md）')
