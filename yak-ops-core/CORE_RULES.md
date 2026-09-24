# Core Rules

Scope:
- `yak-ops-core/**`

Status:
- Reserved

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

## Current Fact

`yak-ops-core` 当前没有生产代码，只保留 Maven Module 壳。

这是有意状态，不是待填空的目录。

## Entry Rule

新能力只有同时满足以下条件之一时才考虑进入 Core：

- 有独立生命周期。
- 有稳定 Runtime Contract。
- 有独立状态机或并发资源边界。
- 明显独立于 Datasource 持久化和 HTTP。
- 未来存在真实多 owner 复用，而不是假设复用。

进入 Core 前必须先更新 `ARCHITECTURE.md` 并 Review ownership。

## Must Not

- 因为 Datasource 类变长就把代码搬进 Core。
- 因为“看起来通用”就提前抽 Core。
- 把 Repository / Mapper / HTTP / Controller 放进 Core。
- 把 Datasource 业务事实变成 Core runtime state。
- 重建已经删除的 Notification / Task / Alert runtime。

## Boundary

默认答案是：**代码留在真实 owner。**

只有独立 Runtime ownership 被证明后，Core 才重新承载代码。
