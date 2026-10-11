# 网页端（Next.js 静态导出 + 独立运行）
FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.22.0 --activate
COPY package.json pnpm-workspace.yaml ./
COPY docs/package.json ./docs/package.json
COPY apps/web/package.json ./apps/web/package.json
COPY packages ./packages
RUN pnpm install --frozen-lockfile=false --filter @awn/web...

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.22.0 --activate
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/web/node_modules ./apps/web/node_modules
COPY --from=deps /app/packages ./packages
COPY apps/web ./apps/web
COPY package.json pnpm-workspace.yaml ./
RUN pnpm --filter @awn/web build

FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/apps/web/.next ./.next
COPY --from=build /app/apps/web/node_modules ./node_modules
COPY --from=build /app/apps/web/package.json ./package.json
EXPOSE 3000
CMD ["pnpm", "--filter", "@awn/web", "start"]
