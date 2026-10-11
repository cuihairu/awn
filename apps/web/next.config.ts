import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // API 契约见 docs/api/contracts.md，全部走 /api/v1
  async rewrites() {
    const apiBase = process.env.AWN_API_BASE ?? 'http://localhost:8080'
    return [{ source: '/api/v1/:path*', destination: `${apiBase}/api/v1/:path*` }]
  },
}

export default nextConfig
