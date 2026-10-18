#!/bin/sh
# 情报角色初始化包装（docker-entrypoint-initdb.d，仅空卷首启执行）：
# 从环境变量 AWN_INTEL_DB_PASSWORD 读取密码，以 psql 变量注入 init-intel.sql。
# 纯 POSIX sh——postgres:16-alpine 无 bash；entrypoint 对 .sh 或直接执行或 source，两态兼容。
set -eu

: "${AWN_INTEL_DB_PASSWORD:?set AWN_INTEL_DB_PASSWORD in .env}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v intel_password="$AWN_INTEL_DB_PASSWORD" \
    -f /opt/awn-init/init-intel.sql
