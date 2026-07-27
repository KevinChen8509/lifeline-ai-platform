#!/usr/bin/env bash
# 简化版健康检查（每 30s 轮询，直到 OpenMetadata healthy 或超时）
# 用法: bash scripts/healthcheck.sh [timeout_sec]

set -uo pipefail
TIMEOUT=${1:-300}
START=$(date +%s)

while true; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))
  if [ "$ELAPSED" -gt "$TIMEOUT" ]; then
    echo "❌ 超时 ${TIMEOUT}s，仍有服务未就绪"
    docker compose ps
    exit 1
  fi

  UNHEALTHY=$(docker compose ps --format json 2>/dev/null \
              | grep -v '"Health":"healthy"' \
              | grep -E '"Health"' \
              | wc -l)

  if [ "$UNHEALTHY" -eq 0 ]; then
    echo "✅ 全部服务健康（耗时 ${ELAPSED}s）"
    docker compose ps
    exit 0
  fi

  echo "  [${ELAPSED}s] 等待 ${UNHEALTHY} 个服务健康..."
  sleep 10
done
