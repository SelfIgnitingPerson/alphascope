#!/usr/bin/env bash
# 仅供本机开发：由调用方给出已核实的本项目后端PID，不使用宽泛pkill。
set -euo pipefail
source "$(dirname "$0")/../lib/environment.sh"
cd "$ALPHASCOPE_ROOT"
backend_pid="${1:?请传入本项目后端PID}"
# 在停止旧服务前校验运行前提，配置错误时保留现有进程。
alphascope_load_env
alphascope_require_java21
if [[ ! -s backend/target/alphascope-backend-0.1.0.jar ]]; then
  echo '生产JAR不存在或为空，未停止当前后端' >&2
  exit 1
fi
backend_command="$(ps -p "$backend_pid" -o command=)"
if [[ "$backend_command" != *alphascope-backend-0.1.0.jar* && "$backend_command" != *com.alphascope.AlphaScopeApplication* ]]; then
  echo '进程不是本项目后端，未停止' >&2; exit 1
fi
previous_runtime_id=""
if [[ "$backend_command" =~ \.runtime/([0-9]{8}T[0-9]{6})/alphascope-backend-0\.1\.0\.jar ]]; then
  previous_runtime_id="${BASH_REMATCH[1]}"
fi
kill -TERM "$backend_pid"
# 旧进程优雅退出后释放端口；每次短等，不杀其他进程。
for ((wait_count=0; wait_count<100; wait_count++)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if kill -0 "$backend_pid" 2>/dev/null; then
  echo '旧后端尚未退出，已停止重启操作。' >&2
  exit 1
fi
# 只回收刚刚退出的进程副本；发现其他进程仍在使用时保留。
if [[ -n "$previous_runtime_id" ]] && command -v lsof >/dev/null 2>&1; then
  previous_jar=".runtime/$previous_runtime_id/alphascope-backend-0.1.0.jar"
  if [[ -f "$previous_jar" ]] && ! lsof -t "$previous_jar" >/dev/null 2>&1; then
    rm -f -- "$previous_jar"
    rmdir ".runtime/$previous_runtime_id" 2>/dev/null || true
  fi
fi
# 运行副本与构建产物分离，避免下一次Maven打包覆盖正在运行的JAR。
runtime_id="$(date +%Y%m%dT%H%M%S)"
mkdir -p ".runtime/$runtime_id"
cp backend/target/alphascope-backend-0.1.0.jar ".runtime/$runtime_id/alphascope-backend-0.1.0.jar"
exec "$JAVA_HOME/bin/java" -jar ".runtime/$runtime_id/alphascope-backend-0.1.0.jar"
