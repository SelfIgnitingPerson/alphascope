#!/usr/bin/env bash
# 公共运行环境只读取项目配置，不依赖开发者用户名或临时目录。
ALPHASCOPE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
alphascope_load_env() {
  if [[ -f "$ALPHASCOPE_ROOT/.env" ]]; then
    set -a
    source "$ALPHASCOPE_ROOT/.env"
    set +a
  fi
}
alphascope_require_java21() {
  local detected_java java_version
  if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ "$(uname -s)" == Darwin ]] && detected_java=$(/usr/libexec/java_home -v 21 2>/dev/null); then
      export JAVA_HOME="$detected_java"
    elif command -v java >/dev/null 2>&1; then
      detected_java=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p')
      if [[ -n "$detected_java" ]]; then export JAVA_HOME="$detected_java"; fi
    fi
  fi
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo '请安装Java 21，并通过JAVA_HOME配置JDK安装目录。' >&2
    return 1
  fi
  java_version=$("$JAVA_HOME/bin/java" -version 2>&1) || {
    echo 'JAVA_HOME中的Java无法执行，请检查JDK安装与CPU架构。' >&2
    return 1
  }
  if [[ "$java_version" != *'version "21.'* && "$java_version" != *'version "21"'* ]]; then
    echo '本项目要求Java 21，请调整JAVA_HOME。' >&2
    return 1
  fi
}
