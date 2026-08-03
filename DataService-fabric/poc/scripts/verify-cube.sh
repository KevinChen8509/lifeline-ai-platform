#!/usr/bin/env bash
# Cube.dev 语义层验证脚本（Week 2 W2.1）
#
# 验证：
#   1. Cube.dev 服务健康
#   2. Schema 已加载（GET /meta）
#   3. CustomerProfile 业务对象可查询（customerCount）
#   4. 跨源 JOIN 衍生指标（ARPU / VIP3 / 高风险计数）
#
# 用法：
#   bash scripts/verify-cube.sh
#
# 前置：docker compose up -d cube 之后等约 20 秒（schema 编译）

set -euo pipefail

CUBE_HOST="${CUBE_HOST:-http://localhost:4000}"
API_SECRET="${CUBEJS_API_SECRET:-datafabric-poc-secret-2026}"
AUTH="Authorization: Bearer ${API_SECRET}"

echo "================================================"
echo " Cube.dev 语义层验证 (${CUBE_HOST})"
echo "================================================"

# --- Step 1: 健康检查 ---
echo
echo "[1/4] 健康检查 GET /"
if ! curl -fsS -o /dev/null -w "HTTP %{http_code}\n" "${CUBE_HOST}/"; then
  echo "FAIL: Cube.dev 未启动或 4000 端口未暴露"
  exit 1
fi

# --- Step 2: Schema meta ---
echo
echo "[2/4] Schema 元数据 GET /cubejs-api/v1/meta"
META=$(curl -fsS -H "${AUTH}" "${CUBE_HOST}/cubejs-api/v1/meta")
CUBES=$(echo "${META}" | python -c "import sys,json; d=json.load(sys.stdin); print(','.join(d.get('cubes',{}).keys()))" 2>/dev/null || echo "PARSE_FAIL")
echo "已加载 cubes: ${CUBES}"
if [[ "${CUBES}" != *"CustomerProfile"* ]] || [[ "${CUBES}" != *"CustomerMetrics"* ]]; then
  echo "FAIL: CustomerProfile / CustomerMetrics 未加载"
  exit 1
fi

# --- Step 3: customerCount（跨源 JOIN 验证）---
echo
echo "[3/4] CustomerProfile.customerCount（跨源 JOIN）"
Q1=$(cat <<JSON
{"query":{"measures":["CustomerProfile.customerCount"],"timeDimensions":[],"order":{},"filters":[]}}
JSON
)
COUNT=$(curl -fsS -X POST -H "${AUTH}" -H "Content-Type: application/json" \
  -d "${Q1}" "${CUBE_HOST}/cubejs-api/v1/load")
echo "响应：${COUNT}" | head -c 300; echo

# --- Step 4: 衍生指标 ---
echo
echo "[4/4] CustomerMetrics 指标（ARPU / VIP3 / 高风险计数）"
Q2=$(cat <<JSON
{"query":{"measures":["CustomerMetrics.arpu","CustomerMetrics.vipCustomerCount","CustomerMetrics.highRiskCustomerCount"],"timeDimensions":[],"order":{},"filters":[]}}
JSON
)
curl -fsS -X POST -H "${AUTH}" -H "Content-Type: application/json" \
  -d "${Q2}" "${CUBE_HOST}/cubejs-api/v1/load"

echo
echo "================================================"
echo " 验证通过 ✓（详见上方响应）"
echo "================================================"
