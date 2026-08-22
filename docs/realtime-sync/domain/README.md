# Realtime Sync Domain Design

> 目标：为 Yak Ops 实时同步建立稳定、可演进、可约束 AI 的领域内核，并用自动化检查抵抗架构漂移。

这组文档描述 **Realtime Sync Domain**，不是 Flink CDC 使用手册，也不是前端页面说明。

## 阶段路线

| 阶段 | 文档 | 目标 |
|---|---|---|
| 1 | [领域边界与统一语言](./01-domain-boundary-and-language.md) | 定义实时同步负责什么、绝不负责什么，以及统一术语 |
| 2 | [核心领域模型 v1](./02-core-domain-model.md) | 确定聚合根、Entity、Value Object 和核心对象关系 |
| 3 | [领域不变量与生命周期](./03-invariants-and-lifecycle.md) | 固定 Draft / Publish / Execution 不变量、状态机、并发和快照规则 |
| 4 | [现有代码到领域模型 Mapping](./04-current-code-mapping.md) | 记录迁移前/迁移中的 Mapping、Gap 与施工顺序 |
| 5 | [AI 领域开发宪法](./05-ai-domain-rules.md) | 把领域设计转换成 AI 强制执行规则 |
| 6 | [Stage 6 Migration Completion](./06-stage6-migration-completion.md) | 记录 Wave 0～6 完成后的当前实现事实、兼容边界和剩余 Gap |
| 7 | [自动化领域护栏](./07-automated-domain-guardrails.md) | 将领域规则转成 Static Guard / Core Smoke / PR Contract，以及可选 private-framework JUnit Hook |

模块级最高优先级规则入口：

```text
yak-ops-business/yak-ops-business-sync/
yak-ops-business-sync-realtime/DOMAIN.md
```

任何 AI / Codex / 开发者修改 realtime-sync 前都应先读 `DOMAIN.md`。

---

## 当前领域模型

Realtime Sync 使用三个聚合根：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

唯一配置事实模型：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

核心生命周期：

```text
Task.currentDraft
   ↓ publish
DefinitionVersion (immutable)
   ↓ start
SyncExecution
```

固定原则：

- `Task ≠ Definition ≠ Version ≠ Execution`；
- Draft 可以在旧 Execution 运行时继续编辑/发布；
- Start 只读取 immutable Published DefinitionVersion；
- RestartExecution 固定旧 Execution 的 VersionRef；
- ApplyPublishedVersion 使用命令开始时捕获的 Published Ref；
- 每次 Start/Restart/Apply 都创建新的 SyncExecution；
- 单个 Execution 的 `STOPPED / FAILED` 是终态；
- `UNKNOWN / CONFLICT` 禁止自动创建第二个运行实例；
- Runtime Environment：Definition 存 Ref，Execution 存 Snapshot；
- Flink / YAML / SSH / JDBC credential / adapter-private tuning 不进入 Core Domain；
- 新场景优先扩 Selector / Route / Target / Policy，不优先增加 sceneType/syncType。

---

## Stage 6 当前实现事实

```text
Wave 0  Core VO + compatibility mapper                         ✅
Wave 1  Immutable DefinitionVersion                            ✅
Wave 2  Start by Published DefinitionVersion                   ✅
Wave 3  SyncExecution lifecycle ownership                      ✅
Wave 4  Active Execution 下继续编辑 / 发布                      ✅
Wave 5  RestartExecution / ApplyPublishedVersion               ✅
Wave 6  Legacy runtime projection / contract cleanup           ✅
```

当前可以稳定表达：

```text
Running E100(V3)
+
Draft r4
+
Published V4
```

并满足：

```text
Save Draft       != mutate E100
Publish V4       != mutate E100
RestartExecution -> E101(V3)
ApplyPublished   -> E101(V4)
Runtime state    -> SyncExecution only
Version identity -> immutable DefinitionVersionId only
```

Task 表的 `desired_state / observed_state / last_error` 即使物理存在，也只是 inert compatibility storage：Application 不写、Runtime command 不读、Read Model 不 fallback。

兼容名如 `job_definition / job_deployment / definition_version / published_version / config_digest / status / latestDeployment / HTTP /restart` 可以暂时存在，但不能反向决定领域语义。

阶段 4 文档中的“当前实现事实”是历史迁移快照；Stage 6 后的当前事实以 [06-stage6-migration-completion.md](./06-stage6-migration-completion.md) 和 `DOMAIN.md` 为准。

---

## Stage 7 自动化护栏

Stage 7 已把关键规则转成机器检查。

### 强制层 A：Static Domain Contract

```text
tools/realtime_domain_guardrails.py
```

检查：

```text
Core Domain dependency purity
second Spec / syncType / sceneType anti-pattern
Task runtime truth 回流
immutable VersionId identity
RestartExecution / ApplyPublishedVersion 分离
Start-by-Published
Digest semantic aliases
Realtime PR body contract
```

本地最快命令：

```bash
python3 tools/realtime_domain_guardrails.py
```

### 强制层 B：Framework-free Core Smoke

GitHub Actions 使用 JDK 21、无 Spring/Maven classpath 直接编译 Core Domain，并运行：

```text
tools/realtime-domain-smoke/RealtimeDomainSmoke.java
```

覆盖 Definition invariant、semantic digest 和 Execution terminal/active 规则。

### 强制层 C：PR Contract

Realtime PR 必须包含：

```text
Domain Impact Analysis
Domain Gap
Domain Compliance Report
```

PR description 编辑后也会重新触发检查。

### 条件层：Backend Maven/JUnit Regression Hook

深层 JUnit 依赖私有：

```text
weifuwan/yak-framework
```

普通 yak-ops `GITHUB_TOKEN` 无法读取该 sibling private repo。

因此完整 Maven/JUnit 回归只有在仓库配置：

```text
YAK_FRAMEWORK_TOKEN
```

并具有 yak-framework read 权限时才执行。

没有 secret 时 workflow 会明确发 Notice 并 skip Maven/JUnit；**绿色 Backend regression hook 不代表 JUnit 已通过**。

详细设计见 [07-automated-domain-guardrails.md](./07-automated-domain-guardrails.md)。

---

## 仍然存在的独立 Gap

Stage 6/7 完成不代表所有技术债清零：

```text
Audit-safe Archive/Tombstone delete
ExecutionPolicy checkpoint/restart runtime application
Flink FINISHED normal completion / snapshot-only
legacy failure-rate mapping
Read-model package hygiene
Compute Environment physical context/package cleanup
API v2 / physical schema naming cleanup
```

这些问题必须单独做 Domain Impact Analysis，不能以“cleanup”名义混入普通功能 PR，也不能先关闭 guardrail 绕过领域讨论。

---

## AI / 开发执行顺序

```text
1. 读 DOMAIN.md
2. 输出 Domain Impact Analysis
3. 查 Stage 6 当前事实
4. 需要设计依据时查 01～05
5. 实现代码
6. 运行 Static Domain Guard
7. 等待 Mandatory Stage 7 CI
8. 有 private framework token 的环境再跑完整 Maven/JUnit regression
9. 输出 Domain Compliance Report
```

如果需求无法映射到三个聚合、`SyncDefinition` 子模型、现有生命周期或明确邻接上下文：

```text
Domain Gap = yes
```

先讨论模型，不允许直接增加新的 `syncType / sceneType / *Spec / *Task` 体系，也不能绕过幂等、快照、UNKNOWN/CONFLICT、runtime identity、credential zeroize 等安全机制。
