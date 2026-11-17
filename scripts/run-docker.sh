#!/usr/bin/env bash
# 本机 Docker 应用入口；不创建、停止、迁移或删除 MySQL。
set -euo pipefail
source "$(dirname "$0")/lib/environment.sh"
cd "$ALPHASCOPE_ROOT"
action="${1:-up}"
case "$action" in
  up|start|stop|status|logs|build|check) ;;
  *) echo '用法：bash scripts/run-docker.sh [up|start|stop|status|logs|build|check]' >&2; exit 2 ;;
esac
if [[ ! -f .env ]]; then
  echo '缺少根目录.env，请按.env.example填写本机MySQL配置。' >&2
  exit 1
fi
requested_image="${ALPHASCOPE_IMAGE:-}"
alphascope_load_env
# 显式传入的镜像引用优先于.env，确保回滚命令不会被默认值覆盖。
if [[ -n "$requested_image" ]]; then export ALPHASCOPE_IMAGE="$requested_image"; fi
# Compose显式列出容器所需变量，不把整份.env或宿主机JDK路径带入容器。
export ALPHASCOPE_LOCAL_UID="$(id -u)"
export ALPHASCOPE_LOCAL_GID="$(id -g)"
compose=(docker compose --env-file "$ALPHASCOPE_ROOT/.env" -f "$ALPHASCOPE_ROOT/deploy/compose.local.yml")
"${compose[@]}" config --quiet
case "$action" in
  up|start)
    # 防止本机Java进程和容器同时消费同一个库的任务。
    if command -v lsof >/dev/null 2>&1; then
      for local_pid in $(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true); do
        local_command="$(ps -p "$local_pid" -o command= || true)"
        if [[ "$local_command" == *alphascope-backend* || "$local_command" == *com.alphascope.AlphaScopeApplication* ]]; then
          echo "本机AlphaScope后端仍在运行（PID $local_pid），请先正常停止，未启动容器。" >&2
          exit 1
        fi
      done
    fi
    mkdir -p .runtime
    if [[ "$action" == up ]]; then
      previous_image="$(docker inspect --format '{{.Image}}' alphascope-local-app-1 2>/dev/null || true)"
      if [[ -n "$previous_image" ]]; then docker image tag "$previous_image" alphascope:rollback; fi
      "${compose[@]}" up -d --build --force-recreate --wait --wait-timeout 180
    else
      "${compose[@]}" up -d --no-build --pull never --force-recreate --wait --wait-timeout 180
    fi
    echo 'AlphaScope：http://127.0.0.1:5173/（MySQL仍使用本机现有数据库）'
    ;;
  stop) "${compose[@]}" down ;;
  status) "${compose[@]}" ps ;;
  logs) "${compose[@]}" logs --tail=100 --follow ;;
  build)
    previous_image="$(docker inspect --format '{{.Image}}' alphascope-local-app-1 2>/dev/null || true)"
    if [[ -n "$previous_image" ]]; then docker image tag "$previous_image" alphascope:rollback; fi
    "${compose[@]}" build app
    ;;
  check) echo '本机Docker应用配置校验通过（未启动容器）。' ;;
esac
