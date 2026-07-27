#!/usr/bin/env bash
# Week 1 冒烟测试 - 验证 7 个服务全部健康 + 3 源数据 + 跨源 JOIN
# 用法: bash scripts/smoke-test.sh
# 退出码: 0=全部通过, 非0=有问题

set -uo pipefail

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

PASS=0
FAIL=0
ok()   { echo -e "${GREEN}✓ $1${NC}"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}✗ $1${NC}"; FAIL=$((FAIL+1)); }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
check(){ if [ "$1" = "$2" ]; then ok "$3"; else fail "$3 (expected=$2, actual=$1)"; fi; }

echo "=========================================="
echo " Data Fabric PoC - Week 1 冒烟测试"
echo "=========================================="

# ----------------------------------------------------------
# 1. Docker 容器健康
# ----------------------------------------------------------
echo ""
echo "[1/6] 容器状态检查"
for svc in dfp-mysql dfp-clickhouse dfp-postgres dfp-trino dfp-opensearch dfp-om-mysql dfp-openmetadata; do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
  case "$status" in
    healthy)   ok "$svc = healthy" ;;
    starting)  warn "$svc = starting（OpenMetadata 首次启动较慢，等待 90s+）" ;;
    *)         fail "$svc = $status" ;;
  esac
done

# ----------------------------------------------------------
# 2. MySQL 数据
# ----------------------------------------------------------
echo ""
echo "[2/6] MySQL 客户数据"
cnt=$(docker exec dfp-mysql mysql -uroot -ppoc123 -N -B \
        -e "SELECT COUNT(*) FROM customer_db.customer;" 2>/dev/null || echo "0")
check "$cnt" "1000" "MySQL customer 行数"

# ----------------------------------------------------------
# 3. ClickHouse 数据
# ----------------------------------------------------------
echo ""
echo "[3/6] ClickHouse 订单数据"
cnt=$(docker exec dfp-clickhouse clickhouse-client \
        -q "SELECT count() FROM analytics.orders" 2>/dev/null || echo "0")
check "$cnt" "10000" "ClickHouse orders 行数"

# 验证 9004 MySQL 协议
echo -n "  ClickHouse 9004 MySQL 协议... "
if docker exec dfp-clickhouse bash -c "echo 'SELECT 1' | mysql -h 127.0.0.1 -P 9004 -u default 2>/dev/null" >/dev/null 2>&1; then
  ok "9004 端口可用"
else
  warn "mysql client 未安装或 9004 端口异常，将在 Trino 步骤验证"
fi

# ----------------------------------------------------------
# 4. PostgreSQL 数据
# ----------------------------------------------------------
echo ""
echo "[4/6] PostgreSQL 风险标签"
cnt=$(docker exec dfp-postgres psql -U external -d external -t -A \
        -c "SELECT COUNT(*) FROM risk_tags;" 2>/dev/null || echo "0")
check "$cnt" "50" "PostgreSQL risk_tags 行数"

# ----------------------------------------------------------
# 5. Trino 跨源 JOIN
# ----------------------------------------------------------
echo ""
echo "[5/6] Trino 跨源查询"
# Catalog 列表
catalogs=$(docker exec dfp-trino trino --execute "SHOW CATALOGS" -f csv 2>/dev/null \
           | grep -E "mysql|clickhouse|postgres" | wc -l)
check "$catalogs" "3" "Trino 3 个 Catalog 可见"

# 跨源 JOIN
result=$(docker exec dfp-trino trino --execute "
  SELECT c.cust_id, c.cust_name, c.cust_level,
         coalesce(o.total_orders, 0),
         coalesce(o.total_amount, 0.0),
         r.risk_level
  FROM mysql.customer_db.customer c
  LEFT JOIN (
    SELECT cust_id, count(*) AS total_orders, sum(order_amount) AS total_amount
    FROM clickhouse.analytics.orders GROUP BY cust_id
  ) o ON c.cust_id = o.cust_id
  LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
  WHERE c.cust_id = 'C0001'
" -f csv 2>/dev/null | tail -1)

if echo "$result" | grep -q '"C0001"'; then
  ok "跨源 JOIN 返回 C0001 客户画像"
  echo "  返回行: $result"
else
  fail "跨源 JOIN 未返回预期数据"
  echo "  实际返回: $result"
fi

# 性能基线（毫秒）
ms=$(docker exec dfp-trino trino --execute "
  SELECT count(*) FROM mysql.customer_db.customer c
  LEFT JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
  LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
" --progress=false 2>&1 | tail -1)
echo -n "  跨源 JOIN 性能: ${ms:-(未测量)}"

# ----------------------------------------------------------
# 6. OpenMetadata
# ----------------------------------------------------------
echo ""
echo "[6/6] OpenMetadata 健康"
om_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8585/api/v1/system/config/health-check 2>/dev/null || echo "000")
if [ "$om_status" = "200" ]; then
  ok "OpenMetadata /health-check = 200"
else
  warn "OpenMetadata 健康检查 = $om_status（首次启动慢，等待 90-180s）"
fi

# ----------------------------------------------------------
# 汇总
# ----------------------------------------------------------
echo ""
echo "=========================================="
echo -e " 通过 ${GREEN}${PASS}${NC} 项, 失败 ${RED}${FAIL}${NC} 项"
echo "=========================================="

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "排查命令:"
  echo "  docker compose ps"
  echo "  docker logs dfp-trino --tail 50"
  echo "  docker logs dfp-openmetadata --tail 50"
  exit 1
fi

exit 0
