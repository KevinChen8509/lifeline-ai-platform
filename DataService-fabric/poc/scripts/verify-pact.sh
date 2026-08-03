#!/usr/bin/env bash
# W2.4 Pact 契约测试验证脚本
#
# 跑 CubeClientPactTest，生成 pact JSON 到 target/pacts/
# 不依赖 Docker，纯 Maven 测试
#
# 用法：
#   bash scripts/verify-pact.sh
#
# 前置：data-service 目录下有 Maven（或用 Docker: docker run --rm -v $PWD:/app -w /app maven:3.9-eclipse-temurin-17 mvn test）

set -euo pipefail

DS_DIR="${DS_DIR:-poc/data-service}"
PACT_FILE="${DS_DIR}/target/pacts/data-service-cube-dev.json"

echo "================================================"
echo " W2.4 Pact 契约测试"
echo "================================================"

# 检查 Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "FAIL: mvn 未安装"
    echo "   选项 A: 装 Maven（apt install / brew install / choco install）"
    echo "   选项 B: 用 Docker 跑:"
    echo "     docker run --rm -v \"\$PWD:/app\" -w /app maven:3.9-eclipse-temurin-17 mvn -f $DS_DIR test -Dtest=CubeClientPactTest"
    exit 1
fi

cd "$DS_DIR"

echo
echo "[1/3] 跑 Pact consumer 测试"
mvn test -Dtest=CubeClientPactTest -q

echo
echo "[2/3] 检查生成的 Pact 契约文件"
if [[ ! -f "$PACT_FILE" ]]; then
    echo "FAIL: Pact JSON 未生成 ($PACT_FILE)"
    exit 1
fi

echo
echo "[3/3] Pact 摘要"
PACT_SIZE=$(wc -c < "$PACT_FILE")
INTERACTIONS=$(grep -c '"interactions"' "$PACT_FILE" || echo 0)
echo "  文件: ${PACT_FILE}"
echo "  大小: ${PACT_SIZE} bytes"
echo "  交互数: ${INTERACTIONS}（应为 4）"

echo
echo "================================================"
echo " Pact 契约测试通过 ✓"
echo "================================================"
