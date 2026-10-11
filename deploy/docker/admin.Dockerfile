# 管理后台（Vite 静态构建 + Caddy/nginx 托管，镜像内以轻量静态服务运行）
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.22.0 --activate
COPY package.json pnpm-workspace.yaml ./
COPY apps/admin/package.json ./apps/admin/package.json
COPY packages ./packages
RUN pnpm install --frozen-lockfile=false --filter @awn/admin...

COPY apps/admin ./apps/admin
RUN pnpm --filter @awn/admin build

FROM caddy:2.8-alpine AS run
COPY --from=build /app/apps/admin/dist /srv
# 配置：dist 下提供静态文件；反代由 compose 中 caddy 入口完成
EXPOSE 80
CMD ["caddy", "file-server", "--root", "/srv", "--listen", ":80"]
