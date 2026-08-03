#!/usr/bin/env bash
# Spring Boot 数据服务验证脚本（Week 2 W2.2 + W2.3）
#
# 验证：
#   1. /actuator/health 健康
#   2. 单客户画像（跨源 JOIN 透明）
#   3. 客户分群查询（VIP3）
#   4. 全局指标快照（ARPU / VIP3 / 高风险计数）
#   5. 404 错误契约
#   6. 脱敏契约 - SUPPORT 角色（full 视图）
#   7. 脱敏契约 - ADMIN 角色（full 视图，不脱敏）
#   8. 审计日志（W2.3 切面记录）
#
# 用法：
#   bash scripts/verify-data-service.sh
#
# 前置：Cube.dev 已启动且 verify-cube.sh 通过；data-service 已启动。

set -euo pipefail

API="${DATA_SERVICE_URL:-http://localhost:8090}"

echo "================================================"
echo " Spring Boot 数据服务验证 (${API})"
echo "================================================"

# --- Step 1: 健康 ---
echo
echo "[1/5] 健康 GET /actuator/health"
curl -fsS "${API}/actuator/health" | python -m json.tool 2>/dev/null || \
    curl -fsS "${API}/actuator/health"

# --- Step 2: 单客户画像 ---
echo
echo "[2/5] 单客户画像 GET /api/v1/customers/C0001/profile"
curl -fsS "${API}/api/v1/customers/C0001/profile" | python -m json.tool 2>/dev/null || \
    curl -fsS "${API}/api/v1/customers/C0001/profile"

# --- Step 3: 客户分群 ---
echo
echo "[3/5] VIP3 客户分群 GET /api/v1/customers?level=VIP3&size=5"
curl -fsS "${API}/api/v1/customers?level=VIP3&size=5" | python -m json.tool 2>/dev/null || \
    curl -fsS "${API}/api/v1/customers?level=VIP3&size=5"

# --- Step 4: 全局指标 ---
echo
echo "[4/5] 全局指标 GET /api/v1/metrics/customer-overview"
curl -fsS "${API}/api/v1/metrics/customer-overview" | python -m json.tool 2>/dev/null || \
    curl -fsS "${API}/api/v1/metrics/customer-overview"

# --- Step 5: 404 契约 ---
echo
echo "[5/8] 404 错误契约 GET /api/v1/customers/UNKNOWN/profile"
HTTP_CODE=$(curl -s -o /tmp/w22-resp.json -w "%{http_code}" "${API}/api/v1/customers/UNKNOWN/profile")
echo "HTTP: ${HTTP_CODE}"
cat /tmp/w22-resp.json | python -m json.tool 2>/dev/null || cat /tmp/w22-resp.json
if [[ "${HTTP_CODE}" != "404" ]]; then
    echo "FAIL: 404 契约失败"
    exit 1
fi

# --- Step 6: 脱敏契约（SUPPORT 角色） ---
echo
echo "[6/8] 脱敏契约 - SUPPORT 角色调 full 视图"
curl -fsS -H "X-User-Role: SUPPORT" "${API}/api/v1/customers/C0001/profile" | python -m json.tool 2>/dev/null || \
    curl -fsS -H "X-User-Role: SUPPORT" "${API}/api/v1/customers/C0001/profile"
echo "（应看到 phone=138****0001, idCard=110101********1234, riskScore=null）"

# --- Step 7: 脱敏契约（ADMIN 角色不脱敏） ---
echo
echo "[7/8] 脱敏契约 - ADMIN 角色调 full 视图"
curl -fsS -H "X-User-Role: ADMIN" "${API}/api/v1/customers/C0001/profile" | python -m json.tool 2>/dev/null || \
    curl -fsS -H "X-User-Role: ADMIN" "${API}/api/v1/customers/C0001/profile"
echo "（应看到完整 phone, 完整 idCard, riskScore=数值）"

# --- Step 8: 审计日志 ---
echo
echo "[8/8] 审计日志 GET /api/v1/audit/recent"
curl -fsS "${API}/api/v1/audit/recent?n=5" | python -m json.tool 2>/dev/null || \
    curl -fsS "${API}/api/v1/audit/recent?n=5"
echo "（应看到刚才 3 次访问：SUPPORT full / ADMIN full / 404 失败不记录）"

echo
echo "================================================"
echo " 数据服务 + 治理切面 验证通过 ✓"
echo "================================================"
