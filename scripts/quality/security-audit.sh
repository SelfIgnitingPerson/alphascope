#!/usr/bin/env bash
set -euo pipefail
# 只发送公开依赖坐标给漏洞服务；不上传.env、数据库、日志或研究证据。
cd "$(dirname "$0")/../.."
mkdir -p .runtime/security
npm --prefix frontend audit --audit-level=high --json > .runtime/security/npm-audit.json
./mvnw -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputReactorProjects=false -DincludeTestScope=false
# 固定扫描器版本；结果由宿主重定向，不让容器写源码或读取本机配置目录。
docker run --rm --network=bridge \
  --mount "type=bind,src=$PWD/target/bom.json,dst=/scan/bom.json,readonly" \
  --mount "type=bind,src=$PWD/frontend/package-lock.json,dst=/scan/package-lock.json,readonly" \
  ghcr.io/google/osv-scanner:v2.7.1 scan source --sbom=/scan/bom.json --lockfile=/scan/package-lock.json --format=json \
  > .runtime/security/osv.json
node scripts/quality/release-manifest.mjs
