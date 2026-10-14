import { useCallback, useEffect, useState } from 'react'

/**
 * 运营台数据拉取 hook：统一 加载中 / 失败 / 刷新 三态。
 * 每个视图一个调用点；失败时展示错误文案并提供刷新，不静默吞错。
 */
export function useAdminData<T>(fetcher: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    let alive = true
    setLoading(true)
    setFailed(false)
    fetcher()
      .then((value) => {
        if (alive) setData(value)
      })
      .catch(() => {
        if (alive) setFailed(true)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => load(), [load])

  return { data, loading, failed, reload: load }
}
