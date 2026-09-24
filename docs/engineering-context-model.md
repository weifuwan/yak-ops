# Yak Ops Engineering Context Model

Status: Active

Scope:
- Product development
- Code changes
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

How to prove
→ Explicit Verification
```

当前 Yak Ops 不维护自动化测试体系，也不维护 GitHub Actions CI。

因此 Verification 只记录本次任务真实执行过的本地编译、构建、静态检查或手工验证，不能写不存在的证据。

## Capability Contract

定义稳定产品行为：

```text
能力是什么？
必须满足什么 Contract？
和哪些能力有关？
当前状态是什么？
涉及哪些代码和数据？
```

## Code Rules

`*_RULES.md` 定义 owner 的稳定编码约束。

规则放在离代码最近的位置，不把细节堆到 AGENTS.md。

## Architecture

`ARCHITECTURE.md` 定义当前结构事实和 ownership。

Architecture 只描述当前真实系统，不提前设计未来层级。

## Verification

Verification 是一次变更的执行事实，例如：

```text
./mvnw ... compile
yarn lint
yarn build
manual smoke check
```

只记录真正执行过的动作。

没有测试、没有 CI，就不要写“tests passed”或“CI green”。

## Context Loading

```text
Task
→ Capability Contract
→ ARCHITECTURE.md
→ nearest *_RULES.md
→ target code
→ explicit verification
```

## Evidence Chain

```text
Capability Contract
→ Architecture ownership
→ Code Rules
→ Implementation
→ Explicit Verification
→ Verified Commit
```

## Document Creation Rule

长期工程文档只保留：

```text
Capability Contract
Code Rules
Architecture
Verification facts
```

无法归类的信息优先让代码或 Git History 承担。

## Principle

> **What to build → Capability Contract.**

> **How to code → *_RULES.md.**

> **Where things belong → ARCHITECTURE.md.**

> **How to prove → Explicit Verification.**
