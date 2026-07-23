# D06 — OCR 文档解析

> **前置要求**：已完成 [D05-RRF+Rerank](D05-rrf-rerank.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

用户上传的知识库文档可能是 PDF（含图片）、扫描件。纯文本提取提取不出图片里的文字。**OCR**（光学字符识别）负责从图片里提取文字。

---

## 五种 OCR 引擎

Lumina 用 `@ConditionalOnProperty` 切换 5 种实现（详见[第一阶 05-Spring Boot 在 Lumina](../stage-1-foundation/05-spring-boot-in-lumina.md)）：

| 引擎 | 配置值 | 说明 |
|------|--------|------|
| **None** | `none`（默认） | 不做 OCR |
| **百度** | `baidu` | 百度智能云 OCR API |
| **腾讯** | `tencent` | 腾讯云 OCR API |
| **阿里** | `alibaba` | 阿里云 OCR API |
| **本地** | `local` | 本地模型 |

```yaml
lumina:
  rag:
    reader:
      ocr:
        provider: none    # 改成 baidu/tencent/alibaba/local 启用
```

---

## PDF 解析流程

```
上传 PDF
  ↓
PDFBox 提取文本
  ↓ 文本够多？
  ├──够──► 直接用（文本型 PDF）
  └──不够──► 可能是扫描件
            ↓
            转图片 → OCR 识别 → 补充文本
```

```java
// 文件：lumina-agent-core/.../rag/PdfOcrProcessor.java
// 扫描件检测 + OCR 补充
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| OCR | 从图片提取文字 |
| 五引擎 | 靠一个配置值切换（@ConditionalOnProperty） |
| None 默认 | 安全——不做 OCR 而不是报错 |
| 扫描件检测 | 文本不够多才走 OCR |

> 🚀 [D07 — 知识库联邦 →](D07-knowledge-base-federation.md)

---

📝 **本篇撰写期间修正的代码**：无。
