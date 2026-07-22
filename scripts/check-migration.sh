#!/usr/bin/env bash
# ============================================================
# Flyway 迁移 SQL 前置检查
#
# 拦截最常见的迁移失败原因：列名拼写错误。
# 在 git pre-commit 或 CI 中运行。
#
# 用法:
#   bash scripts/check-migration.sh
#   # 或安装为 pre-commit hook:
#   # cp scripts/check-migration.sh .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
# ============================================================

set -euo pipefail

MIGRATION_DIR="lumina-modules/lumina-business-base/src/main/resources/db/migration"

# 检查是否有新的迁移文件
NEW_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null | grep "V[0-9]*__.*\.sql$" || true)
if [ -z "$NEW_MIGRATIONS" ]; then
    # 没有暂存的，检查工作区
    NEW_MIGRATIONS=$(git diff --name-only 2>/dev/null | grep "V[0-9]*__.*\.sql$" || true)
fi

if [ -z "$NEW_MIGRATIONS" ]; then
    exit 0  # 没有迁移文件变更，跳过
fi

echo "🔍 检查 Flyway 迁移 SQL..."

ERRORS=0

for file in $NEW_MIGRATIONS; do
    if [ ! -f "$file" ]; then
        continue
    fi

    # 检查常见错误列名
    # lumina_permission: 应该是 permission_name/permission_code/permission_id
    if grep -q "INSERT INTO.*lumina_permission" "$file" 2>/dev/null; then
        if grep -qE "INSERT INTO.*lumina_permission.*\b(name|code|id)\b" "$file" 2>/dev/null && \
           ! grep -q "permission_name\|permission_code\|permission_id" "$file" 2>/dev/null; then
            echo "❌ $file: lumina_permission 列名疑似错误（应为 permission_name/permission_code/permission_id）"
            ERRORS=$((ERRORS + 1))
        fi
    fi

    # 检查是否有 DESCRIBE 或 grep 的痕迹（理想情况应先查表结构）
    # 这里不做强制，只是提醒

    # 检查 parent_id 引用的父权限是否存在（简单 grep 检查）
    PERM_INSERTS=$(grep "INSERT INTO.*lumina_permission" "$file" 2>/dev/null || true)
    if [ -n "$PERM_INSERTS" ]; then
        # 检查是否有 SELECT ... FROM lumina_permission 的子查询（正确模式）
        if ! echo "$PERM_INSERTS" | grep -q "SELECT.*permission_id.*FROM lumina_permission"; then
            echo "⚠️  $file: 权限种子 INSERT 建议用子查询获取 parent_id（参考 V17/V25 格式）"
        fi
    fi

    # 检查版本号是否比当前最大版本大
    VERSION=$(echo "$file" | grep -oE 'V[0-9]+' | head -1 | tr -d 'V')
    if [ -n "$VERSION" ]; then
        MAX_VERSION=$(ls "$MIGRATION_DIR"/V[0-9]*__.sql 2>/dev/null | grep -oE 'V[0-9]+' | tr -d 'V' | sort -n | tail -1)
        if [ -n "$MAX_VERSION" ] && [ "$VERSION" -le "$MAX_VERSION" ] 2>/dev/null; then
            # out-of-order 允许但提醒
            echo "⚠️  $file: 版本号 V$VERSION <= 当前最大版本 V$MAX_VERSION（out-of-order 迁移）"
        fi
    fi
done

if [ "$ERRORS" -gt 0 ]; then
    echo ""
    echo "❌ 发现 $ERRORS 个迁移 SQL 错误，请修复后再提交。"
    echo "   提示：写 INSERT 前先执行 DESCRIBE 查表结构"
    exit 1
fi

echo "✅ 迁移 SQL 检查通过"
