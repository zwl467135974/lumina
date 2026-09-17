#!/usr/bin/env python3
# skill-reminder-hook.py — PreToolUse hook: 写代码前提醒加载对应技能包
#
# 由 .zcode/config.json 以 type=process 方式直接调用(无 shell)。
# 不 block(exit 0), 仅通过 stdout 输出 additionalContext JSON 注入提醒。
# 用 python 直启而非 bash: Windows 上裸命令 bash 会解析到 WSL stub
# (C:\Windows\System32\bash.exe), 唤醒 WSL 并导致 WSLg 弹窗。

import json
import sys


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception:
        return

    params = data.get("params", data.get("tool_input", {})) or {}
    file_path = params.get("file_path", params.get("path", ""))
    if not file_path:
        return

    ext = file_path.rsplit(".", 1)[-1].lower()
    path_lower = file_path.lower()

    if ext == "java":
        skills = [
            "lumina_architecture",
            "lumina_code_style",
            "lumina_api_design",
            "lumina_mybatis_plus",
            "lumina_domain_model",
            "lumina_json_serialization",
        ]
        if "redis" in path_lower or "cache" in path_lower:
            skills.append("lumina_redis")
        if "migration" in path_lower or "flyway" in path_lower:
            skills.append("lumina_flyway")
        if "test" in path_lower:
            skills.append("lumina_testing")
        if any(k in path_lower for k in ("audit", "metric", "monitor", "log")):
            skills.append("lumina_observability")
    elif ext in ("vue", "scss", "css", "ts"):
        skills = ["lumina_frontend_design"]
    elif ext == "sql":
        skills = ["lumina_flyway", "lumina_mybatis_plus"]
    else:
        return

    skill_list = " ".join("- " + s for s in skills)
    filename = file_path.replace("\\", "/").rsplit("/", 1)[-1]
    message = (
        "[技能提醒] 你正在编辑 " + ext + " 文件(" + filename + ")。"
        "根据 AGENTS.md 前置检查清单,写代码前应加载以下技能包:"
        + skill_list + " 请通过 Skill 工具加载相关技能后再编写代码。"
    )
    print(json.dumps({"additionalContext": message}))


if __name__ == "__main__":
    main()
