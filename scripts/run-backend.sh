#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib/environment.sh"
cd "$ALPHASCOPE_ROOT"
alphascope_load_env
alphascope_require_java21
exec ./mvnw -pl backend spring-boot:run
