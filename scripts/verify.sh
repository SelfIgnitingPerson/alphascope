#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib/environment.sh"
cd "$ALPHASCOPE_ROOT"
alphascope_load_env
alphascope_require_java21
node scripts/quality/check-project.mjs
./mvnw -pl backend spotless:check test
(cd frontend && npm run format:check && npm test && npm run build)
# 普通开发检查不依赖Docker；CI与显式容器检查仍严格校验容器配置。
if [[ "${VERIFY_DOCKER_CONFIG:-false}" == true ]]; then
  docker compose config --quiet
  ALPHASCOPE_LOCAL_UID="$(id -u)" ALPHASCOPE_LOCAL_GID="$(id -g)" \
    docker compose --env-file .env -f deploy/compose.local.yml config --quiet
fi
