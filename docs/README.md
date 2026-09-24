# Yak Ops Docs

这是 Yak Ops 的文档入口。

当前产品范围只有一个 Domain：

```text
Datasource
```

文档的目标不是堆流程，而是让人或 AI 快速找到：

- 这个任务属于什么能力
- 谁拥有它
- 受什么规则约束
- 应该读哪些代码和测试
- 怎么证明改动有效

## Engineering Context Model

```text
What to build       → Capability Contract
How to code         → *_RULES.md
Where things belong → ARCHITECTURE.md
How to test         → *_TEST_RULES.md
How to prove        → Verification
```

完整定义见 [Engineering Context Model](./engineering-context-model.md)。

## Current Knowledge Map

```text
Datasource Domain
  → Domain README
  → Capability Contracts
  → Code / Data / Tests
```

当前入口：

- [Datasource](./capabilities/datasource/README.md)

## Context Loading

```text
Task
→ Datasource README
→ Target Capability when one exists
→ Depends On / related scenario when required
→ target code / data / tests
→ nearest RULES
→ implementation
→ verification
```

没有 Capability 文档时，不跳过设计直接根据旧代码扩展。先读当前代码和测试，再写最小 Contract。

## Capability Manifest

新的 Capability 文档第一屏使用：

```text
Status
Domain
Depends On
Related
Frontend
Backend
Data
Tests
```

正文默认保持：

```text
Purpose
Contract
Flow
Boundary
```

不要把实现过程写成长期文档。

## Status

```text
Planned
Designing
Implementing
Review
Done
```

`Done` 必须描述当前真实实现。

`Designing / Implementing` 必须明确计划内容，不能让未来设计看起来像已经存在。

## Feature Development Rule

一次只推进一个 Capability 或一个明确拆分好的功能块：

```text
选择能力
→ 读取 Contract / Code / Tests
→ 找真实 Gap
→ 更新 Contract
→ 最小实现
→ 测试
→ Review
→ 修复 Gap
→ Done
```

禁止：
- 一个 PR 同时做多个独立产品能力。
- 当前能力没 Review 完就顺手扩下一块。
- 为未来需求提前加层或扩展点。
- 为“架构完整”增加 Manager / Coordinator / Handler / Assembler。
- 让代码变化超出当前 Capability 边界。

## Engineering Rules

- [Architecture](../ARCHITECTURE.md)
- [Java Rules](../JAVA_RULES.md)
- [Backend Test Rules](../BACKEND_TEST_RULES.md)
- [Controller Rules](../CONTROLLER_RULES.md)
- [Datasource Rules](../yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md)
- [Common Rules](../yak-ops-common/COMMON_RULES.md)
- [Core Rules](../yak-ops-core/CORE_RULES.md)
- [SPI Rules](../yak-ops-spi/SPI_RULES.md)
- [Datasource Plugin Rules](../yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md)
- [Frontend Architecture](../yak-ops-ui/ARCHITECTURE.md)
- [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)
- [Frontend Service Rules](../yak-ops-ui/SERVICE_RULES.md)
- [Frontend Test Rules](../yak-ops-ui/TEST_RULES.md)

原则：

> **稳定行为写 Contract，共享约束写 Rule，结构事实写 Architecture，执行结果给 Verification。**
