#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib/environment.sh"
cd "$ALPHASCOPE_ROOT"
alphascope_load_env
alphascope_require_java21
# 参数限制为工具名、数据源和ISO日期；禁止参数成为Maven选项或其他命令。
for argument in "$@"; do
  if [[ ! "$argument" =~ ^[a-z0-9-]+$ ]]; then echo '仅支持工具名、数据源或ISO日期。' >&2; exit 2; fi
done
./mvnw -q -pl backend compile exec:java -Dexec.mainClass=com.alphascope.tools.ProjectTools "-Dalphascope.root=$ALPHASCOPE_ROOT" "-Dexec.args=$*"
