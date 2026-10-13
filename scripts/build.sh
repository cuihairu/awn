#!/usr/bin/env bash
# AWN 默认构建：一条命令产出全部可运行产物（后端 fat jar + 前端静态产物）。
#
# 前置依赖：JDK 21、Maven 3.9+（可用 MAVEN=... 覆盖路径）、Node 20+、pnpm 10+。
# 用法：
#   scripts/build.sh            # 标准构建
#   scripts/build.sh -o         # 追加 Maven 参数（如离线构建 -o，要求本机 .m2 已预热）
# 产物路径见脚本末尾输出；任一步失败立即非零退出。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MAVEN="${MAVEN:-mvn}"

step() { printf '\n== %s\n' "$*"; }

if [ ! -d node_modules ]; then
  echo "node_modules missing: run 'pnpm install' first (workspace root)" >&2
  exit 1
fi

step "[1/4] backend: apps/awn-api + apps/awn-worker fat jars"
"$MAVEN" -B -ntp -DskipTests package -pl apps/awn-api,apps/awn-worker -am "$@"

step "[2/4] frontend: apps/web（Next.js，产物 apps/web/.next/）"
pnpm --filter @awn/web build

step "[3/4] frontend: apps/admin（Vite，产物 apps/admin/dist/）"
pnpm --filter @awn/admin build

step "[4/4] 文档站（docs，产物 docs/.vitepress/dist/）"
pnpm -C docs build

step "artifacts"
for jar in apps/awn-api/target/awn-api-*.jar apps/awn-worker/target/awn-worker-*.jar; do
  ls -lh "$jar" | awk '{print "  " $NF "  " $5}'
done
printf '  apps/web/.next/            （pnpm --filter @awn/web start 运行）\n'
printf '  apps/admin/dist/           （纯静态文件，任意静态服务器可托管）\n'
printf '  docs/.vitepress/dist/      （文档站静态文件）\n'
