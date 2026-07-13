#!/usr/bin/env bash
# skill-reminder-hook.sh — PreToolUse hook: 写代码前提醒加载对应技能包
#
# 不 block(exit 0),仅通过 additionalContext 注入提醒。
# 根据 Edit/Write 的文件类型,提示应加载的 lumina_* 技能。

set -euo pipefail

# 读取 hook 输入(stdin JSON)
input=$(cat)
file_path=$(echo "$input" | python -c "
import sys, json
try:
    data = json.load(sys.stdin)
    params = data.get('params', data.get('tool_input', {}))
    print(params.get('file_path', params.get('path', '')))
except:
    print('')
" 2>/dev/null || echo "")

# 无文件路径则直接放行
if [ -z "$file_path" ]; then
    exit 0
fi

# 根据文件扩展名判断需要的技能
ext="${file_path##*.}"
skills=""

case "$ext" in
    java)
        skills="lumina_architecture lumina_code_style lumina_api_design lumina_mybatis_plus lumina_domain_model lumina_json_serialization"
        # 涉及 Redis 的文件额外提醒
        if echo "$file_path" | grep -qi "redis\|cache"; then
            skills="$skills lumina_redis"
        fi
        # 涉及 Flyway 迁移
        if echo "$file_path" | grep -qi "migration\|flyway"; then
            skills="$skills lumina_flyway"
        fi
        # 涉及测试
        if echo "$file_path" | grep -qi "test\|Test"; then
            skills="$skills lumina_testing"
        fi
        # 涉及可观测性
        if echo "$file_path" | grep -qi "audit\|metric\|monitor\|log"; then
            skills="$skills lumina_observability"
        fi
        ;;
    vue|scss|css|ts)
        skills="lumina_frontend_design"
        ;;
    sql)
        skills="lumina_flyway lumina_mybatis_plus"
        ;;
    *)
        # 非代码文件,放行
        exit 0
        ;;
esac

if [ -z "$skills" ]; then
    exit 0
fi

# 输出 additionalContext(注入对话)
filename=$(basename "$file_path")
skill_list=$(echo "$skills" | tr ' ' '\n' | sed 's/^/- /' | tr '\n' ' ')
echo "{\"additionalContext\":\"[技能提醒] 你正在编辑 ${ext} 文件(${filename})。根据 AGENTS.md 前置检查清单,写代码前应加载以下技能包:${skill_list} 请通过 Skill 工具加载相关技能后再编写代码。\"}"
