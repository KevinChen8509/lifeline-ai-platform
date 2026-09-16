#!/usr/bin/env bash
# =============================================================================
# 整体性测试验证 — 从数据接入到数据服务（E2E Full-Chain）
#
# 一条命令把全链路串起来：数据接入(双源 H2 替身) → 元数据目录(OM stub) →
# 服务化四形态发布+金标 → 安全治理 → 运营(Key/限流/计量) → 消费通道 →
# 重启持久化 → dashboard 代理。分层断言 PASS/FAIL，exit code = 失败数。
#
# 栈（不依赖 Docker，复用 w4-demo.sh 配方）：
#   1. om-stub      :18585  OpenMetadata 4 表元数据桩
#   2. data-service :8090   MySQL/PG 双源 H2 替身 + 文件注册表 ./data/e2e-fullchain
#   3. dashboard    :3210   消费代理层（3000 落 Windows WinNAT 排除区间）
#
# 范围外（外部依赖，仅断言降级契约）：W1 Trino / W2 Cube（→ 502 固定文案不泄漏）、
# W3 LLM Agent（w4-demo.sh / red-team-b-path.sh 已覆盖）。
#
# 用法：  bash scripts/e2e-full-chain.sh
# 可重复跑：slug 带时间戳不冲突；注册表用独立文件库（不动开发库 ./data/registry）
# 耗时：  ~3-5 分钟（含一次 data-service 重启）
# =============================================================================
set -uo pipefail
cd "$(dirname "$0")/.."   # poc/

MVN="${MVN:-C:/Users/陈亮/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn.cmd}"
export JAVA_HOME="${JAVA_HOME:-D:/Program Files/Java/jdk-17.0.1}"
KEY="${DATAFABRIC_API_KEY:-datafabric-poc-api-key-2026-please-rotate}"
POC_DIR="$PWD"
# MSYS bash 的 $PWD 是 /e/... —— H2 INIT=RUNSCRIPT 的 JDBC URL 必须是 E:/... Windows 盘符路径
# （否则 H2 打不开种子文件 → 池 total=0 连接建不起来 → 所有查询 502）
if command -v cygpath >/dev/null 2>&1; then
  POC_DIR_WIN=$(cygpath -m "$PWD")
else
  POC_DIR_WIN=$(printf '%s' "$PWD" | sed 's|^/\([a-zA-Z]\)/|\1:/|')
fi
SVC_DIR="$POC_DIR/data-service"
WORK="$POC_DIR/scripts/.e2e-tmp"
TS=$(date +%s)
API="http://localhost:8090"
# 3000 落在本机 Windows 排除端口区间（WinNAT 保留，EACCES）→ 用 3210
DASH_PORT="${DASH_PORT:-3210}"
DASH="http://localhost:$DASH_PORT"

mkdir -p "$WORK"
: > "$WORK/headers"

# ---------------------------------------------------------------- 断言框架
TOTAL=0; FAILS=0
declare -a L_NAMES=() L_PASS=() L_FAIL=()
LI=-1

layer() {
  LI=$((LI+1)); L_NAMES+=("$1"); L_PASS+=(0); L_FAIL+=(0)
  echo; echo "━━━ $1 ━━━"
}

chk() {  # chk <desc> <ok:0/1> [detail]
  TOTAL=$((TOTAL+1))
  if [ "$2" = "0" ]; then
    L_PASS[$LI]=$(( ${L_PASS[$LI]} + 1 ))
    echo "  [PASS] $1${3:+ · $3}"
  else
    FAILS=$((FAILS+1)); L_FAIL[$LI]=$(( ${L_FAIL[$LI]} + 1 ))
    echo "  [FAIL] $1${3:+ · $3}"
  fi
}

# ---------------------------------------------------------------- HTTP 助手
# http <METHOD> <url> [bodyFile|-] [authKey|NONE]   → CODE / $WORK/resp.json / $WORK/headers
http() {
  local m=$1 u=$2 f=$3 ak=$4
  local hdr=()
  [ "$ak" != "NONE" ] && hdr+=(-H "X-API-Key: $ak")
  if [ -n "$f" ] && [ "$f" != "-" ]; then
    CODE=$(curl -s -D "$WORK/headers" -o "$WORK/resp.json" -w '%{http_code}' -X "$m" \
      "${hdr[@]}" -H 'Content-Type: application/json' --data-binary @"$f" "$u")
  else
    CODE=$(curl -s -D "$WORK/headers" -o "$WORK/resp.json" -w '%{http_code}' -X "$m" "${hdr[@]}" "$u")
  fi
}

# jx 'rows.0.cust_name' ['resp.json'] → 值（object→JSON；null→null；异常→JERR）
jx() {
  node -e '
    const fs = require("fs");
    const d = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
    try {
      const v = process.argv[1].split(".").reduce((o, k) => {
        if (o == null) return o;
        if (k === "length") return o.length;
        return /^\d+$/.test(k) ? o[+k] : o[k];
      }, d);
      console.log(v === undefined || v === null ? "null" : (typeof v === "object" ? JSON.stringify(v) : String(v)));
    } catch (e) { console.log("JERR"); }
  ' "$1" "${2:-$WORK/resp.json}"
}

hdr() { grep -i "^$1:" "$WORK/headers" | head -1 | tr -d '\r' | sed 's/^[^:]*: *//'; }

# ---------------------------------------------------------------- 栈管理
port_pids() {  # :PORT → 监听 PID 列表
  netstat -ano | grep "LISTENING" | grep ":$1 " | awk '{print $NF}' | sort -u
}

free_port() {  # 杀占用进程（孤儿进程坑：taskkill 杀不掉 bash 儿孙时按端口兜底）
  local pids; pids=$(port_pids "$1")
  if [ -n "$pids" ]; then
    echo "  端口 $1 被 PID $pids 占用 → taskkill（孤儿进程清理）"
    for pid in $pids; do taskkill //F //PID "$pid" >/dev/null 2>&1 || true; done
    for _ in $(seq 1 10); do port_pids "$1" | grep -q . || return 0; sleep 1; done
  fi
}

wait_up() {  # <url> <grep-pattern> <timeout-tries>
  local i
  for i in $(seq 1 "${3:-60}"); do
    if curl -s --max-time 3 "$1" 2>/dev/null | grep -q "$2"; then return 0; fi
    sleep 2
  done
  return 1
}

SVC_PID=""; OM_PID=""; DASH_PID=""
start_service() {  # 起 data-service（后台，日志 $WORK/svc.log）
  ( cd "$SVC_DIR" && \
    MYSQL_URL="jdbc:h2:mem:e2efc;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '$POC_DIR_WIN/scripts/w4-init.sql'" \
    PG_URL="jdbc:h2:mem:e2efcpg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '$POC_DIR_WIN/scripts/w6d-pg-init.sql'" \
    OPENMETADATA_URL="http://localhost:18585/api" \
    REGISTRY_DB_URL="jdbc:h2:file:./data/e2e-fullchain;MODE=MySQL" \
    "${MVN}" spring-boot:run -Dspring-boot.run.useTestClasspath=true \
      "-Dspring-boot.run.arguments=--datafabric.raw-db.mysql-user=sa --datafabric.raw-db.mysql-password=" \
      > "$WORK/svc.log" 2>&1 ) &
  SVC_PID=$!
}

cleanup() {
  echo; echo "── 清理栈进程 ──"
  [ -n "$SVC_PID" ] && kill "$SVC_PID" 2>/dev/null || true
  [ -n "$OM_PID" ]  && kill "$OM_PID" 2>/dev/null || true
  [ -n "$DASH_PID" ] && kill "$DASH_PID" 2>/dev/null || true
  free_port 8090; free_port 18585; free_port "$DASH_PORT"
  # 日志留档（下次跑覆盖）：scripts/.e2e-last/svc.log / dash.log / om.log
  rm -rf "$POC_DIR/scripts/.e2e-last"
  mkdir -p "$POC_DIR/scripts/.e2e-last"
  cp "$WORK"/svc.log "$WORK"/dash.log "$WORK"/om.log "$POC_DIR/scripts/.e2e-last/" 2>/dev/null || true
  rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

echo "================================================"
echo " 整体性测试验证 — 从数据接入到数据服务"
echo " 时间戳 slug 前缀: e2efc-$TS · 注册表: ./data/e2e-fullchain"
echo "================================================"

# ---------------------------------------------------------------- 起栈
echo "── 起栈（清孤儿 → om-stub → data-service → dashboard）──"
free_port 8090; free_port 18585; free_port "$DASH_PORT"

node scripts/om-stub.js 18585 > "$WORK/om.log" 2>&1 &
OM_PID=$!
start_service
( cd dashboard && PORT="$DASH_PORT" npm start > "$WORK/dash.log" 2>&1 ) &
DASH_PID=$!

echo "  等待 data-service :8090 就绪（首次 mvn 编译较慢）…"
if ! wait_up "$API/actuator/health" '"status":"UP"' 75; then
  echo "  data-service 启动失败 —— 最近日志："; tail -30 "$WORK/svc.log"; exit 1
fi
echo "  等待 dashboard :$DASH_PORT 就绪…"
if ! wait_up "$DASH/api/w2/health" 'status' 30; then
  echo "  ⚠ dashboard 未就绪（L8 将 FAIL）—— 日志："; tail -10 "$WORK/dash.log"
fi
echo "  data-service UP · om-stub :18585 · dashboard :$DASH_PORT"

# =================================================================
layer "L0 平台健康 — actuator / 池配置 / W2 Cube 降级契约"

http GET "$API/actuator/health" - NONE
chk "health 无鉴权 UP" $([ "$CODE" = "200" ] && [ "$(jx status)" = "UP" ]; echo $?) "HTTP $CODE"

http GET "$API/api/v1/raw-db/pools" - "$KEY"
chk "pools 端点 200 且三源在列" $([ "$CODE" = "200" ] && [ "$(jx mysql.state)" != "JERR" ] && [ "$(jx postgres.config.maxPoolSize)" != "JERR" ] && [ "$(jx clickhouse.state)" != "JERR" ]; echo $?) "HTTP $CODE"

http GET "$API/api/v1/customers/C0001/profile" - "$KEY"
LEAK=$(jx message)
chk "W2 profile 离线降级 502 SEMANTIC_LAYER_UNAVAILABLE" $([ "$CODE" = "502" ] && [ "$(jx error)" = "SEMANTIC_LAYER_UNAVAILABLE" ]; echo $?) "HTTP $CODE · $(jx error)"
chk "502 文案不泄漏内部 URL（B4）" $(! printf '%s' "$LEAK" | grep -qiE 'http://|cube:4000|CubeDriver|\.java:'; echo $?) "message=$(jx message)"

# =================================================================
layer "L1 元数据目录 — OM 驱动（发布校验的元数据闸来源）"

http GET "$API/api/v1/catalog/tables" - "$KEY"
chk "目录 4 表且未降级" $([ "$CODE" = "200" ] && [ "$(jx tables.length)" = "4" ] && [ "$(jx degraded)" = "false" ]; echo $?) "HTTP $CODE · $(jx tables.length) 表 · degraded=$(jx degraded)"
FQNS=$(jx tables)
for fqn in mysql.customer_db.customer mysql.customer_db.orders clickhouse.orders_db.orders postgres.external.risk_tags; do
  chk "目录含 $fqn" $(printf '%s' "$FQNS" | grep -q "$fqn"; echo $?)
done

# =================================================================
layer "L2 服务化四形态 — 发布即服务 + 各形态金标（数据活体流经 JDBC→API）"

# --- 发布 table-query ---
cat > "$WORK/pub-table.json" <<EOF
{"slug":"e2efc-$TS-table","name":"E2E Table Query","description":"full-chain e2e table-query","fqn":"mysql.customer_db.customer","allowedColumns":["cust_id","cust_name","cust_level","region"],"filters":[{"column":"cust_id","operator":"eq"}],"joins":[],"aggregates":[],"defaultLimit":20}
EOF
http POST "$API/api/v1/services" "$WORK/pub-table.json" "$KEY"
TKEY=$(jx apiKey)
chk "发布 table-query 201 + 服务 key" $([ "$CODE" = "201" ] && [ "${TKEY#sk-w6-}" != "$TKEY" ]; echo $?) "HTTP $CODE · type=$(jx service.type)· key=${TKEY:0:9}…"

http GET "$API/api/v1/services/e2efc-$TS-table/query?cust_id=C0001" - "$KEY"
chk "table-query 金标 C0001=张伟/VIP3/北京（接入数据活体）" $([ "$CODE" = "200" ] && [ "$(jx rows.0.cust_name)" = "张伟" ] && [ "$(jx rows.0.cust_level)" = "VIP3" ] && [ "$(jx rows.0.region)" = "北京" ]; echo $?) "HTTP $CODE · $(jx rows.0.cust_name)/$(jx rows.0.cust_level)/$(jx rows.0.region)"

# --- 发布 fusion ---
cat > "$WORK/pub-fusn.json" <<EOF
{"slug":"e2efc-$TS-fusn","name":"E2E Fusion","description":"full-chain e2e fusion","fqn":"mysql.customer_db.customer","allowedColumns":["cust_id","cust_name","cust_level","region"],"filters":[{"column":"cust_id","operator":"eq"}],"joins":[{"fqn":"mysql.customer_db.orders","name":"orders","columns":["order_id","cust_id","order_amount"],"joinColumn":"cust_id","parentColumn":"cust_id","limitPerParent":20}],"aggregates":[],"defaultLimit":20}
EOF
http POST "$API/api/v1/services" "$WORK/pub-fusn.json" "$KEY"
chk "发布 fusion 201" $([ "$CODE" = "201" ] && [ "$(jx service.type)" = "fusion" ]; echo $?) "HTTP $CODE · type=$(jx service.type)"

http GET "$API/api/v1/services/e2efc-$TS-fusn/query?cust_id=C0001" - "$KEY"
chk "fusion 金标 C0001=张伟+2 单（子行透出关联键）" $([ "$CODE" = "200" ] && [ "$(jx rows.0.cust_name)" = "张伟" ] && [ "$(jx rows.0.orders.length)" = "2" ] && [ "$(jx rows.0.orders.0.cust_id)" = "C0001" ]; echo $?) "HTTP $CODE · $(jx rows.0.orders.length) 单"

# --- 发布 aggregate ---
cat > "$WORK/pub-agg.json" <<EOF
{"slug":"e2efc-$TS-agg","name":"E2E Aggregate","description":"full-chain e2e aggregate","fqn":"mysql.customer_db.orders","allowedColumns":["cust_id"],"filters":[{"column":"cust_id","operator":"eq"}],"joins":[],"aggregates":[{"function":"COUNT","column":null,"alias":"order_count"},{"function":"SUM","column":"order_amount","alias":"total_amount"}],"defaultLimit":50}
EOF
http POST "$API/api/v1/services" "$WORK/pub-agg.json" "$KEY"
chk "发布 aggregate 201" $([ "$CODE" = "201" ] && [ "$(jx service.type)" = "aggregate" ]; echo $?) "HTTP $CODE · type=$(jx service.type)"

http GET "$API/api/v1/services/e2efc-$TS-agg/query?cust_id=C0001" - "$KEY"
chk "aggregate 金标 C0001 → 2 单 / 4670.50" $([ "$CODE" = "200" ] && [ "$(jx rows.0.order_count)" = "2" ] && [ "$(jx rows.0.total_amount)" = "4670.50" ]; echo $?) "HTTP $CODE · $(jx rows.0.order_count)/$(jx rows.0.total_amount)"

# --- 发布 agg-fusion（组合 + 跨源 PG）---
cat > "$WORK/pub-aggf.json" <<EOF
{"slug":"e2efc-$TS-aggf","name":"E2E AggFusion","description":"full-chain e2e agg-fusion xsrc","fqn":"mysql.customer_db.orders","allowedColumns":["cust_id"],"filters":[{"column":"cust_id","operator":"eq"}],"aggregates":[{"function":"COUNT","column":null,"alias":"order_count"},{"function":"SUM","column":"order_amount","alias":"total_amount"}],"joins":[{"fqn":"postgres.external.risk_tags","name":"risk_tags","columns":["risk_level","risk_score","cust_id"],"joinColumn":"cust_id","parentColumn":"cust_id","limitPerParent":20}],"defaultLimit":50}
EOF
http POST "$API/api/v1/services" "$WORK/pub-aggf.json" "$KEY"
AGGF_KEY=$(jx apiKey)
chk "发布 agg-fusion 201 + 服务 key（L6/L7 复用）" $([ "$CODE" = "201" ] && [ "$(jx service.type)" = "agg-fusion" ] && [ -n "$AGGF_KEY" ]; echo $?) "HTTP $CODE · type=$(jx service.type) · key=${AGGF_KEY:0:9}…"

http GET "$API/api/v1/services/e2efc-$TS-aggf/query?cust_id=C0001" - "$KEY"
chk "agg-fusion 金标 C0001 → 2/4670.50 + risk_tags[0]{high,82}（跨源 PG 方言）" $([ "$CODE" = "200" ] && [ "$(jx rows.0.order_count)" = "2" ] && [ "$(jx rows.0.total_amount)" = "4670.50" ] && [ "$(jx rows.0.risk_tags.0.risk_level)" = "high" ] && [ "$(jx rows.0.risk_tags.0.risk_score)" = "82" ]; echo $?) "HTTP $CODE · $(jx rows.0.order_count)/$(jx rows.0.total_amount)/$(jx rows.0.risk_tags.0.risk_level)/$(jx rows.0.risk_tags.0.risk_score)"

http GET "$API/api/v1/services/e2efc-$TS-aggf/query" - "$KEY"
R3=$(jx rows)
G1=0
for pair in "C0001:high" "C0002:medium" "C0003:low"; do
  c=${pair%%:*}; l=${pair##*:}
  printf '%s' "$R3" | grep -q "\"cust_id\":\"$c\"" || G1=1
done
[ "$(jx total)" = "3" ] || G1=1
printf '%s' "$R3" | grep -q '"risk_level":"high"' && printf '%s' "$R3" | grep -q '"risk_level":"medium"' && printf '%s' "$R3" | grep -q '"risk_level":"low"' || G1=1
chk "agg-fusion 无过滤 → 3 组全挂载 high/medium/low 各归其主" $G1 "total=$(jx total)"

# =================================================================
layer "L3 数据接入 — 双源池状态（懒启动）+ H2 种子真实流经"

http GET "$API/api/v1/raw-db/pools" - "$KEY"
chk "mysql 池 STARTED（首查后懒启动）" $([ "$(jx mysql.state)" = "STARTED" ]; echo $?) "state=$(jx mysql.state) · idle=$(jx mysql.idle)"
chk "postgres 池 STARTED（跨源 join 触发）" $([ "$(jx postgres.state)" = "STARTED" ]; echo $?) "state=$(jx postgres.state)"
chk "clickhouse 池 NOT_STARTED（未用不启动，预期）" $([ "$(jx clickhouse.state)" = "NOT_STARTED" ]; echo $?) "state=$(jx clickhouse.state)"

# =================================================================
layer "L4 安全治理 — 鉴权 / 注入双闸 / 钳制 / 撞名闸"

http GET "$API/api/v1/services" - NONE
chk "无 key → 401" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"
http GET "$API/api/v1/services" - "wrong-key-1234567890abcd"
chk "错 key → 401" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"

INJ="C0001%27%20OR%20%271%27%3D%271"
http GET "$API/api/v1/services/e2efc-$TS-table/query?cust_id=$INJ" - "$KEY"
chk "table-query 注入 payload → 200 total=0" $([ "$CODE" = "200" ] && [ "$(jx total)" = "0" ]; echo $?) "HTTP $CODE · total=$(jx total)"
http GET "$API/api/v1/services/e2efc-$TS-aggf/query?cust_id=$INJ" - "$KEY"
chk "agg-fusion 注入 payload → 200 total=0" $([ "$CODE" = "200" ] && [ "$(jx total)" = "0" ]; echo $?) "HTTP $CODE · total=$(jx total)"

http GET "$API/api/v1/services/e2efc-$TS-table/query?__bogus=x" - "$KEY"
chk "未知参数 → 400" $([ "$CODE" = "400" ]; echo $?) "HTTP $CODE · $(jx error)"

http GET "$API/api/v1/services/e2efc-$TS-table/query?limit=99999" - "$KEY"
chk "limit=99999 → 钳制 ≤500 不失控" $([ "$CODE" = "200" ] && [ "$(jx rows.length)" -le 500 ] 2>/dev/null; echo $?) "HTTP $CODE · $(jx rows.length) 行"

cat > "$WORK/probe-col.json" <<EOF
{"slug":"e2efc-$TS-probe1","name":"probe","description":"verify probe","fqn":"mysql.customer_db.customer","allowedColumns":["cust_id","cust_level; DROP TABLE customer"],"filters":[],"joins":[],"aggregates":[],"defaultLimit":5}
EOF
http POST "$API/api/v1/services" "$WORK/probe-col.json" "$KEY"
PCODE=$CODE
[ "$PCODE" = "201" ] && http DELETE "$API/api/v1/services/e2efc-$TS-probe1" - "$KEY" >/dev/null
chk "发布白名单闸：注入列 → 400" $([ "$PCODE" = "400" ]; echo $?) "HTTP $PCODE"

cat > "$WORK/probe-clash.json" <<EOF
{"slug":"e2efc-$TS-probe2","name":"probe","description":"verify probe","fqn":"mysql.customer_db.orders","allowedColumns":["cust_id"],"filters":[],"aggregates":[{"function":"COUNT","column":null,"alias":"order_count"}],"joins":[{"fqn":"postgres.external.risk_tags","name":"order_count","columns":["risk_level","risk_score","cust_id"],"joinColumn":"cust_id","parentColumn":"cust_id","limitPerParent":20}],"defaultLimit":5}
EOF
http POST "$API/api/v1/services" "$WORK/probe-clash.json" "$KEY"
PCODE=$CODE
[ "$PCODE" = "201" ] && http DELETE "$API/api/v1/services/e2efc-$TS-probe2" - "$KEY" >/dev/null
chk "agg-fusion 撞名闸：join.name=顶层别名 → 400" $([ "$PCODE" = "400" ]; echo $?) "HTTP $PCODE"

# =================================================================
layer "L5 服务运营 — Key 生命周期 / 限流 / 计量"

http POST "$API/api/v1/services/e2efc-$TS-table/key/rotate" - "$KEY"
NEWKEY=$(jx apiKey)
chk "rotate → 新 key 返回" $([ "$CODE" = "200" ] && [ -n "$NEWKEY" ] && [ "$NEWKEY" != "$TKEY" ]; echo $?) "HTTP $CODE · ${NEWKEY:0:9}…"

http GET "$API/api/v1/services/e2efc-$TS-table/query?cust_id=C0001" - "$NEWKEY"
chk "新 key 直调 /query → 200（服务 key 通道）" $([ "$CODE" = "200" ] && [ "$(jx rows.0.cust_name)" = "张伟" ]; echo $?) "HTTP $CODE"
http GET "$API/api/v1/services/e2efc-$TS-table/query?cust_id=C0001" - "$TKEY"
chk "旧 key → 401（恒时比较同文案）" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"

http POST "$API/api/v1/services/e2efc-$TS-table/key/revoke" - "$KEY"
chk "revoke → 200" $([ "$CODE" = "200" ]; echo $?) "HTTP $CODE"
http GET "$API/api/v1/services/e2efc-$TS-table/query?cust_id=C0001" - "$NEWKEY"
chk "吊销后 key → 401" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"

cat > "$WORK/pub-rate.json" <<EOF
{"slug":"e2efc-$TS-rate","name":"E2E Rate","description":"full-chain e2e rate limit","fqn":"mysql.customer_db.customer","allowedColumns":["cust_id"],"filters":[],"joins":[],"aggregates":[],"defaultLimit":5,"rateLimitPerMin":2}
EOF
http POST "$API/api/v1/services" "$WORK/pub-rate.json" "$KEY"
chk "发布 rate=2/min 服务 201" $([ "$CODE" = "201" ]; echo $?) "HTTP $CODE"
R1=0
http GET "$API/api/v1/services/e2efc-$TS-rate/query" - "$KEY"; [ "$CODE" = "200" ] || R1=1
http GET "$API/api/v1/services/e2efc-$TS-rate/query" - "$KEY"; [ "$CODE" = "200" ] || R1=1
http GET "$API/api/v1/services/e2efc-$TS-rate/query" - "$KEY"
RA=$(hdr Retry-After)
chk "限流：前两次 200 · 第 3 次 → 429 + Retry-After" $([ "$R1" = "0" ] && [ "$CODE" = "429" ] && [ -n "$RA" ]; echo $?) "前两次 $([ "$R1" = "0" ] && echo 200 || echo FAIL) · 第 3 次 HTTP $CODE · Retry-After=$RA"

http GET "$API/api/v1/services/e2efc-$TS-rate/usage" - "$KEY"
chk "usage：成功 2 次计入（429/401 不计）" $([ "$CODE" = "200" ] && [ "$(jx totalCalls)" = "2" ]; echo $?) "totalCalls=$(jx totalCalls)"

# =================================================================
layer "L6 消费通道 — 服务 key 最小权限（双通道隔离）"

http GET "$API/api/v1/services/e2efc-$TS-aggf/query?cust_id=C0001" - "$AGGF_KEY"
chk "服务 key 调自身 /query → 200 金标" $([ "$CODE" = "200" ] && [ "$(jx rows.0.total_amount)" = "4670.50" ]; echo $?) "HTTP $CODE · $(jx rows.0.total_amount)"
http GET "$API/api/v1/services/e2efc-$TS-fusn/query?cust_id=C0001" - "$AGGF_KEY"
chk "服务 key 调他人 /query → 401" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"
http GET "$API/api/v1/services" - "$AGGF_KEY"
chk "服务 key 调管理端点 → 401" $([ "$CODE" = "401" ]; echo $?) "HTTP $CODE"

# =================================================================
layer "L7 持久化 — 重启 data-service（注册表文件库恢复 + key 不换）"

free_port 8090
sleep 2
start_service
echo "  等待 data-service 重启就绪…"
if ! wait_up "$API/actuator/health" '"status":"UP"' 75; then
  echo "  重启失败 —— 最近日志："; tail -30 "$WORK/svc.log"
  chk "重启后 health UP" 1 "timeout"
else
  chk "重启后 health UP" 0
fi

http GET "$API/api/v1/services" - "$KEY"
RESTORED=$(jx services)
R7=0
for s in "e2efc-$TS-table" "e2efc-$TS-fusn" "e2efc-$TS-agg" "e2efc-$TS-aggf" "e2efc-$TS-rate"; do
  printf '%s' "$RESTORED" | grep -q "\"slug\":\"$s\"" || R7=1
done
chk "5 个 e2efc 服务全部恢复" $R7 "$(printf '%s' "$RESTORED" | grep -o "e2efc-$TS" | wc -l) 个"

http GET "$API/api/v1/services/e2efc-$TS-aggf" - "$KEY"
chk "agg-fusion type/joins/aggregates 三保留" $([ "$CODE" = "200" ] && [ "$(jx service.type)" = "agg-fusion" ] && [ "$(jx service.joins.length)" = "1" ] && [ "$(jx service.aggregates.length)" = "2" ]; echo $?) "type=$(jx service.type) · joins=$(jx service.joins.length) · aggregates=$(jx service.aggregates.length)"

http GET "$API/api/v1/services/e2efc-$TS-aggf/query?cust_id=C0001" - "$AGGF_KEY"
chk "重启前发的服务 key 仍 200 金标逐字复现" $([ "$CODE" = "200" ] && [ "$(jx rows.0.order_count)" = "2" ] && [ "$(jx rows.0.total_amount)" = "4670.50" ] && [ "$(jx rows.0.risk_tags.0.risk_level)" = "high" ]; echo $?) "HTTP $CODE · $(jx rows.0.order_count)/$(jx rows.0.total_amount)/$(jx rows.0.risk_tags.0.risk_level)"

# =================================================================
layer "L8 消费代理 — dashboard 前置层（:$DASH_PORT）"

http GET "$DASH/api/w2/health" - NONE
chk "dashboard /api/w2/health 代理连通" $([ "$CODE" = "200" ]; echo $?) "HTTP $CODE"

http GET "$DASH/api/w5/catalog" - NONE
chk "dashboard /api/w5/catalog 4 表" $([ "$CODE" = "200" ] && [ "$(jx tables.length)" = "4" ]; echo $?) "HTTP $CODE · $(jx tables.length) 表"

http GET "$DASH/api/w5/services" - NONE
NSVC=$(printf '%s' "$(jx services)" | grep -o "e2efc-$TS-[a-z0-9]*" | sort -u | wc -l)
chk "dashboard /api/w5/services ≥5 个 e2efc 服务" $([ "$CODE" = "200" ] && [ "$NSVC" -ge 5 ]; echo $?) "HTTP $CODE · $NSVC 个"

# ---------------------------------------------------------------- 汇总
echo
echo "================================================"
echo " 分层汇总"
echo "------------------------------------------------"
i=0
while [ $i -le $LI ]; do
  printf "  %-58s %2d PASS %2d FAIL\n" "${L_NAMES[$i]}" "${L_PASS[$i]}" "${L_FAIL[$i]}"
  i=$((i+1))
done
echo "------------------------------------------------"
if [ "$FAILS" = "0" ]; then
  echo " ✓ 整体性验证通过：$TOTAL/$TOTAL —— 从数据接入到数据服务全链路 green"
else
  echo " ✕ 验证未通过：$FAILS 项 FAIL（共 $TOTAL 项）"
fi
echo "================================================"
exit "$FAILS"
