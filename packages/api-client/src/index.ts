/**
 * AWN API 客户端。
 *
 * 契约来源：docs/api/contracts.md（/api/v1）。
 * 订单创建必须携带 Idempotency-Key；相同幂等键 + 相同请求返回原结果，
 * 相同幂等键 + 不同请求返回冲突错误。
 */

export const API_BASE = '/api/v1'

/**
 * 统一响应处理：非 2xx 抛 ApiError（携带 HTTP 状态与后端错误码）。
 * 调用方据此区分「资源不存在」（404）与「链路 / 服务故障」（其余）——
 * 不再把错误信封当成功体返回，避免列表页把 {code,message} 当数组渲染。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function readJson<T>(res: Response): Promise<T> {
  const body: unknown = await res.json().catch(() => null)
  if (!res.ok) {
    const envelope = body as { code?: unknown; message?: unknown } | null
    const code = typeof envelope?.code === 'string' ? envelope.code : `HTTP_${res.status}`
    const message =
      typeof envelope?.message === 'string' ? envelope.message : res.statusText
    throw new ApiError(res.status, code, message)
  }
  return body as T
}

export interface OrderCreatePayload {
  /** 客户端生成的幂等键 */
  idempotencyKey: string
  lines: { productId: string; sessionId: string; quantity: number }[]
}

export interface OrderStatus {
  orderId: string
  status:
    | 'PENDING_PAYMENT'
    | 'PAID'
    | 'FULFILLING'
    | 'FULFILLED'
    | 'REFUND_PENDING'
    | 'REFUNDED'
    | 'CANCELLED'
    | 'EXPIRED'
  amountMinor: number
  currency: 'CNY'
}

export async function createOrder(
  payload: OrderCreatePayload,
  init?: RequestInit,
): Promise<unknown> {
  return fetch(`${API_BASE}/orders`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'idempotency-key': payload.idempotencyKey,
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(payload),
  }).then((res) => readJson<unknown>(res))
}

/* ------------------------------------------------------------------ *
 * 明星图鉴（docs/design/star-catalog.md）
 * 公开读只返回 PUBLISHED；photoUrl 为 null 表示无授权图片，按占位图渲染。
 * 人物与作品是多对多，关系行（PersonWorkEntry）带 roleType / roleName /
 * billingOrder——两端详情都复用同一条关系结构，互相跳转时不必再查一次。
 * ------------------------------------------------------------------ */

export type ProfessionType =
  | 'actor'
  | 'singer'
  | 'host'
  | 'director'
  | 'musician'
  | 'producer'
  | 'screenwriter'
  | 'dancer'
  | 'model'

export type WorkType =
  | 'movie'
  | 'tv'
  | 'variety'
  | 'song'
  | 'album'
  | 'concert'
  | 'stage'

export type RoleType =
  | 'cast_lead'
  | 'cast_supporting'
  | 'cast_cameo'
  | 'director'
  | 'screenwriter'
  | 'producer'
  | 'composer'
  | 'lyricist'
  | 'theme_song'
  | 'voice'

export interface PersonView {
  id: string
  name: string
  originalName: string | null
  aliases: string[]
  nationality: string
  professionTypes: ProfessionType[]
  bio: string | null
  /** 仅在有授权来源时透出；null 表示未收录图片，前端渲染占位图 */
  photoUrl: string | null
  status: string
}

export interface WorkView {
  id: string
  title: string
  type: WorkType
  year: number | null
  description: string | null
  status: string
}

/** 关系行：一人 + 一部作品 + 一种身份 */
export interface PersonWorkEntry {
  personId: string
  workId: string
  personName: string
  workTitle: string
  workType: WorkType
  workYear: number | null
  roleType: RoleType
  roleName: string
  billingOrder: number | null
}

export interface PersonDetail {
  person: PersonView
  works: PersonWorkEntry[]
}

export interface WorkDetail {
  work: WorkView
  cast: PersonWorkEntry[]
}

export interface PersonQuery {
  profession?: string
  nationality?: string
  q?: string
}

export interface WorkQuery {
  type?: string
  q?: string
}

/** 空值参数不下发，避免 ?q= 之类的空查询条件落到后端 */
function withQuery(path: string, params: Record<string, string | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    const trimmed = value?.trim()
    if (trimmed) search.set(key, trimmed)
  }
  const qs = search.toString()
  return qs ? `${API_BASE}${path}?${qs}` : `${API_BASE}${path}`
}

export async function listPersons(
  query: PersonQuery = {},
  init?: RequestInit,
): Promise<PersonView[]> {
  return fetch(
    withQuery('/stars', {
      profession: query.profession,
      nationality: query.nationality,
      q: query.q,
    }),
    init,
  ).then((res) => readJson<PersonView[]>(res))
}

export async function getPerson(
  personId: string,
  init?: RequestInit,
): Promise<PersonDetail> {
  return fetch(`${API_BASE}/stars/${personId}`, init)
    .then((res) => readJson<PersonDetail>(res))
}

export async function listWorks(
  query: WorkQuery = {},
  init?: RequestInit,
): Promise<WorkView[]> {
  return fetch(
    withQuery('/works', { type: query.type, q: query.q }),
    init,
  ).then((res) => readJson<WorkView[]>(res))
}

export async function getWork(
  workId: string,
  init?: RequestInit,
): Promise<WorkDetail> {
  return fetch(`${API_BASE}/works/${workId}`, init)
    .then((res) => readJson<WorkDetail>(res))
}
