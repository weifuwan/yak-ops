# Yak Ops Engineering Context Model

Status: Active

Scope:
- Product development
- Code changes
- Testing
- Verification
- AI context loading

## Core Model

```text
What to build
→ Capability Contract

How to code
→ *_RULES.md

Where things belong
→ ARCHITECTURE.md

How to test
→ *_TEST_RULES.md

How to prove
→ CI / explicit verification
```

这五类 Context 回答不同问题，不能互相替代。

## What to build → Capability Contract

Capability Contract 定义稳定产品行为。

它回答：

```text
能力是什么？
必须满足什么 Contract？
和哪些能力有关？
当前状态是什么？
涉及哪些代码 / 数据 / 测试？
```

产品行为变化先定位 Capability，再改代码。

## How to code → *_RULES.md

RULES 定义稳定编码约束。

它回答：

```text
这个 owner 可以怎么写？
禁止怎么写？
依赖边界是什么？
需要继续加载哪份最近规则？
```

规则放在离 owner 最近的位置，不把所有细节堆到 AGENTS.md。

## Where things belong → ARCHITECTURE.md

Architecture 定义当前结构事实和 ownership。

它回答：

```text
代码放哪里？
谁拥有这个行为？
模块怎么依赖？
什么不属于这里？
```

Architecture 只描述当前真实系统，不预建未来层级。

## How to test → *_TEST_RULES.md

Test Rules 定义需要什么证据。

它回答：

```text
什么必须测试？
在哪一层测？
Mock 什么？
什么时候需要 Integration Test？
什么不值得测？
```

测试保护稳定行为，不保护实现细节和覆盖率数字。

## How to prove → CI / Verification

Verification 为具体 Commit 提供执行证据。

当前 Yak Ops CI 尚未执行完整后端/前端测试，所以必须区分：

```text
CI build green
≠
tests verified
```

直到 CI 补齐测试，PR 需要明确记录实际运行过的测试命令。

## Context Loading

收到任务后默认按：

```text
Task
→ Capability Contract
→ ARCHITECTURE.md
→ nearest *_RULES.md
→ relevant *_TEST_RULES.md
→ target code / tests
→ verification
```

只加载当前任务需要的 Context，不默认扫描整个仓库。

## Evidence Chain

```text
Capability Contract
→ Architecture ownership
→ Code Rules
→ Test Rules
→ Implementation + Tests
→ Verification
→ Verified Commit
```

缺一层时先判断是否真的需要补，不机械创建文档。

## Document Creation Rule

新增长期工程文档前先问：

> 它属于哪一类？

```text
Capability Contract
Code Rules
Architecture
Test Rules
Verification
```

如果无法归类，优先合并到已有文档，或者让代码 / 测试 / Git History 承担信息。

## Principle

> **What to build → Capability Contract.**

> **How to code → *_RULES.md.**

> **Where things belong → ARCHITECTURE.md.**

> **How to test → *_TEST_RULES.md.**

> **How to prove → Verification.**
