#!/usr/bin/env bash
# W4 Agent 可观测台 · 半 live 一键启动（不依赖 Docker）
#
#   1. om-stub.js :18585  —— F3 RAG 元数据桩（OpenMetadata 形状）
#   2. data-service :8090 —— B 路 MySQL 用 H2 MySQL 模式替身（3 客户种子数据）
#   3. dashboard :3000    —— 打开 http://localhost:3000/w4-app.html
#
# 能力范围（Cube/三源离线时）：
#   ✓ B 路 SSE 流式 / trace / 工具日志 / token 用量 / 池监控 / 红队注入
#   ✗ A 路 insight（需 Cube）→ 页面 A 栏报错属预期；池监控仅 mysql STARTED 属预期
#
# 用法：  LLM_API_KEY=<Ark key> bash scripts/w4-demo.sh
# 停止：  Ctrl+C（三个进程一起停）
set -euo pipefail
cd "$(dirname "$0")/.."   # poc/

: "${LLM_API_KEY:?请先 export LLM_API_KEY=<Ark API key>}"

MVN="${MVN:-C:/Users/陈亮/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn.cmd}"
JAVA_HOME="${JAVA_HOME:-D:/Program Files/Java/jdk-17.0.1}"
SEED="E:/Agentic/DataService-fabric/poc/scripts/w4-init.sql"
export JAVA_HOME

# 1. OpenMetadata 桩（F3 RAG 上下文来源）
node scripts/om-stub.js 18585 &
OM_PID=$!

# 2. data-service：H2 替代 MySQL
#    URL 走环境变量 —— spring-boot.run.arguments 按空格切分会截断 INIT=RUNSCRIPT FROM
cd data-service
MYSQL_URL="jdbc:h2:mem:w4live;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '${SEED}'" \
OPENMETADATA_URL="http://localhost:18585/api" \
LLM_API_KEY="${LLM_API_KEY}" \
"${MVN}" spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  "-Dspring-boot.run.arguments=--datafabric.raw-db.mysql-user=sa --datafabric.raw-db.mysql-password=" &
SVC_PID=$!
cd ..

# 3. dashboard
cd dashboard
npm start &
DASH_PID=$!
cd ..

trap 'kill $OM_PID $SVC_PID $DASH_PID 2>/dev/null || true' EXIT INT TERM

echo
echo "================================================"
echo " W4 半 live 栈启动中："
echo "   可观测台     http://localhost:3000/w4-app.html"
echo "   data-service http://localhost:8090（B 路 MySQL = H2 替身）"
echo "   om-stub      http://localhost:18585（F3 RAG 桩）"
echo " Ctrl+C 全部停止"
echo "================================================"
wait
