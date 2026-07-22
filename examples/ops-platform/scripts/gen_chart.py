#!/usr/bin/env python3
"""
Lumina 智能运维平台 — 多模态测试图生成器

生成用于多模态测试的图片：
  1. 系统架构图（供 Agent 视觉分析识别架构组件）
  2. CPU 趋势图（供 Agent 分析监控趋势）

用法:
    python3 gen_chart.py                          # 默认输出到 /tmp/lumina-ops/images/
    python3 gen_chart.py --outdir ./images        # 自定义输出目录

需要 matplotlib（pip install matplotlib）。
如果没有 matplotlib，会自动降级为纯文本 SVG（无需任何依赖）。
"""

import argparse
import os
import sys


def try_matplotlib(outdir):
    """用 matplotlib 生成高质量图表"""
    import matplotlib
    matplotlib.use("Agg")  # 无头模式
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches

    # === 1. 系统架构图 ===
    fig, ax = plt.subplots(1, 1, figsize=(10, 6))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 7)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.set_title("Lumina 系统架构图", fontsize=16, fontweight="bold")

    # 组件方框
    components = [
        (1, 5, "用户/客户端"),
        (4, 5.5, "Nginx\n反向代理"),
        (4, 3.5, "Gateway\n网关"),
        (7, 5.5, "Agent\n服务"),
        (7, 3.5, "Base\n服务"),
        (9, 5.5, "MySQL"),
        (9, 3.5, "Redis"),
        (4, 1, "知识库\n(Qdrant)"),
    ]

    for x, y, label in components:
        rect = mpatches.FancyBboxPatch(
            (x - 0.8, y - 0.5), 1.6, 1.0,
            boxstyle="round,pad=0.1",
            facecolor="#4A90D9", edgecolor="#2C5F8A", linewidth=2,
        )
        ax.add_patch(rect)
        ax.text(x, y, label, ha="center", va="center", fontsize=8, color="white", fontweight="bold")

    # 箭头
    arrows = [(1.8, 5, 3.2, 5.5), (4.8, 5.5, 6.2, 5.5), (4.8, 5, 6.2, 3.5),
              (7.8, 5.5, 8.2, 5.5), (7.8, 3.5, 8.2, 3.5), (4, 3, 4, 1.5)]
    for x1, y1, x2, y2 in arrows:
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle="->", color="#666", lw=1.5))

    plt.tight_layout()
    arch_path = os.path.join(outdir, "architecture.png")
    fig.savefig(arch_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✅ 系统架构图: {arch_path}")

    # === 2. CPU 趋势图 ===
    fig, ax = plt.subplots(figsize=(10, 4))
    hours = list(range(24))
    cpu_normal = [20 + 15 * abs(np_sin(h / 3.14)) for h in hours]
    cpu_spike = cpu_normal.copy()
    # 在 14-16 点注入 CPU 飙升
    for h in range(14, 17):
        cpu_spike[h] = 85 + (h - 14) * 8

    ax.plot(hours, cpu_normal, "g-o", linewidth=2, markersize=4, label="正常范围")
    ax.plot(hours, cpu_spike, "r-s", linewidth=2, markersize=5, label="异常飙升")
    ax.axhline(y=90, color="r", linestyle="--", alpha=0.5, label="P1 阈值 90%")
    ax.axhline(y=75, color="y", linestyle="--", alpha=0.5, label="P2 阈值 75%")
    ax.fill_between(hours, cpu_spike, alpha=0.15, color="red")

    ax.set_xlabel("时间（小时）")
    ax.set_ylabel("CPU 使用率 (%)")
    ax.set_title("CPU 使用率 24h 趋势 — prod-web-01", fontsize=13, fontweight="bold")
    ax.set_ylim(0, 105)
    ax.legend(loc="upper left")
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    cpu_path = os.path.join(outdir, "cpu-trend.png")
    fig.savefig(cpu_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✅ CPU 趋势图: {cpu_path}")

    return [arch_path, cpu_path]


def np_sin(x):
    """简易正弦（避免 numpy 依赖）"""
    import math
    return math.sin(x)


def fallback_svg(outdir):
    """无 matplotlib 时用纯文本 SVG 降级"""
    print("  ⚠️  matplotlib 未安装，降级为 SVG 格式")

    # 简易架构图 SVG
    arch_svg = '''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 400" style="font-family:sans-serif">
  <rect width="600" height="400" fill="#f5f5f5"/>
  <text x="300" y="30" text-anchor="middle" font-size="20" font-weight="bold">Lumina 系统架构图</text>

  <rect x="30" y="120" width="120" height="60" fill="#4A90D9" rx="8"/>
  <text x="90" y="155" text-anchor="middle" fill="white">用户/客户端</text>

  <rect x="200" y="80" width="120" height="60" fill="#4A90D9" rx="8"/>
  <text x="260" y="115" text-anchor="middle" fill="white">Nginx 反向代理</text>

  <rect x="200" y="200" width="120" height="60" fill="#4A90D9" rx="8"/>
  <text x="260" y="235" text-anchor="middle" fill="white">Gateway 网关</text>

  <rect x="370" y="80" width="120" height="60" fill="#E8743B" rx="8"/>
  <text x="430" y="115" text-anchor="middle" fill="white">Agent 服务</text>

  <rect x="370" y="200" width="120" height="60" fill="#E8743B" rx="8"/>
  <text x="430" y="235" text-anchor="middle" fill="white">Base 服务</text>

  <rect x="480" y="80" width="100" height="50" fill="#5CB85C" rx="8"/>
  <text x="530" y="110" text-anchor="middle" fill="white">MySQL</text>

  <rect x="480" y="200" width="100" height="50" fill="#5CB85C" rx="8"/>
  <text x="530" y="230" text-anchor="middle" fill="white">Redis</text>

  <line x1="150" y1="150" x2="200" y2="110" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="320" y1="110" x2="370" y2="110" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="320" y1="230" x2="370" y2="230" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="490" y1="110" x2="480" y2="105" stroke="#666" stroke-width="2"/>
  <line x1="490" y1="230" x2="480" y2="225" stroke="#666" stroke-width="2"/>

  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
      <path d="M0,0 L0,6 L9,3 z" fill="#666"/>
    </marker>
  </defs>
</svg>'''

    arch_path = os.path.join(outdir, "architecture.svg")
    with open(arch_path, "w", encoding="utf-8") as f:
        f.write(arch_svg)
    print(f"  ✅ 系统架构图 (SVG): {arch_path}")

    # CPU 趋势 SVG
    import math
    points = []
    for h in range(24):
        cpu = 20 + 15 * abs(math.sin(h / 3.14))
        if 14 <= h <= 16:
            cpu = 85 + (h - 14) * 8
        x = 50 + h * 22
        y = 180 - cpu * 1.5
        points.append(f"{x},{y}")

    cpu_svg = f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 250" style="font-family:sans-serif">
  <rect width="600" height="250" fill="white"/>
  <text x="300" y="20" text-anchor="middle" font-size="14" font-weight="bold">CPU 使用率 24h 趋势</text>
  <line x1="50" y1="45" x2="50" y2="180" stroke="#ccc"/>
  <line x1="50" y1="180" x2="570" y2="180" stroke="#ccc"/>
  <line x1="50" y1="45" x2="570" y2="45" stroke="red" stroke-dasharray="4" opacity="0.5"/>
  <text x="45" y="48" text-anchor="end" font-size="9" fill="red">90%</text>
  <line x1="50" y1="67" x2="570" y2="67" stroke="orange" stroke-dasharray="4" opacity="0.5"/>
  <text x="45" y="70" text-anchor="end" font-size="9" fill="orange">75%</text>
  <polyline points="{' '.join(points)}" fill="none" stroke="#E8743B" stroke-width="2"/>
  <text x="300" y="220" text-anchor="middle" font-size="10" fill="#666">14-16 点 CPU 飙升至 93%+（P1 告警）</text>
</svg>'''

    cpu_path = os.path.join(outdir, "cpu-trend.svg")
    with open(cpu_path, "w", encoding="utf-8") as f:
        f.write(cpu_svg)
    print(f"  ✅ CPU 趋势图 (SVG): {cpu_path}")

    return [arch_path, cpu_path]


def main():
    parser = argparse.ArgumentParser(description="生成多模态测试图片")
    parser.add_argument("--outdir", default="/tmp/lumina-ops/images",
                        help="输出目录（默认 /tmp/lumina-ops/images）")
    args = parser.parse_args()

    os.makedirs(args.outdir, exist_ok=True)

    try:
        files = try_matplotlib(args.outdir)
    except ImportError:
        files = fallback_svg(args.outdir)

    print(f"\n✅ 图片已生成到 {args.outdir}")
    print(f"   可上传到 Lumina 文件服务后，通过多模态接口让 Agent 分析")


if __name__ == "__main__":
    main()
