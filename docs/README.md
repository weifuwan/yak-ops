# Yak Ops Docs

这是 Yak Ops 的文档入口。

当前产品范围只有：

```text
Datasource
```

文档只帮助定位 Context：

- 任务属于什么能力
- 谁拥有它
- 受什么规则约束
- 应该读哪些代码
- 本次改动实际验证了什么

## Engineering Context Model

```text
What to build       → Capability Contract
How to code         → *_RULES.md
Where things belong → ARCHITECTURE.md
How to prove        → Explicit Verification
```

完整定义见 [Engineering Context Model](./engineering-context-model.md)。

## Current Knowledge Map

```text
Datasource Domain
  → Domain README
  → Capability Contracts
  → Code / Data
```

当前入口：

- [Datasource](./capabilities/datasource/README.md)

## Context Loading

```text
Task
→ Datasource README
→ Target Capability when one exists
→ target code / data
→ nearest RULES
→ implementation
→ explicit verification
```

## Capability Manifest

新的 Capability 第一屏默认使用：

```text
Status
Domain
Depends On
Related
Frontend
Backend
Data
```

正文默认：

```text
Purpose
Contract
Flow
Boundary
```

## Status

```text
Planned
Designing
Implementing
Review
Done
```

`Done` 必须描述当前真实实现。

## Feature Development Rule

```text
选择能力
→ 读取 Contract / Code
→ 找真实 Gap
→ 更新 Contract
→ 最小实现
→ Review
→ 显式验证
→ Done
```

禁止：
- 一个 PR 同时做多个独立能力。
- 当前能力没 Review 完就扩下一块。
- 为未来需求提前加层或扩展点。
- 为“架构完整”增加 Manager / Coordinator / Handler / Assembler。

## Engineering Rules

- [Architecture](../ARCHITECTURE.md)
- [Java Rules](../JAVA_RULES.md)
- [Controller Rules](../CONTROLLER_RULES.md)
- [Datasource Rules](../yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md)
- [Common Rules](../yak-ops-common/COMMON_RULES.md)
- [Core Rules](../yak-ops-core/CORE_RULES.md)
- [SPI Rules](../yak-ops-spi/SPI_RULES.md)
- [Datasource Plugin Rules](../yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md)
- [Frontend Architecture](../yak-ops-ui/ARCHITECTURE.md)
- [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)
- [Frontend Service Rules](../yak-ops-ui/SERVICE_RULES.md)

> **稳定行为写 Contract，共享约束写 Rule，结构事实写 Architecture，执行结果写 Verification。**
