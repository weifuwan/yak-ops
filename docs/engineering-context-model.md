# Yak Ops Engineering Context Model

Status: Active

Scope: 文档归属、按需上下文加载与验证证据。

## Core Model

每条规范只有一个权威定义位置；其他文档用链接定位，不复制完整规则。

| 问题 | 权威位置 | 不承担的内容 |
| --- | --- | --- |
| 本次任务先读什么 | `AGENTS.md` | 产品规则、PR 进度 |
| 去哪里找某项知识 | `docs/README.md` 及局部索引 | 第二份能力说明 |
| 系统应该如何工作 | Capability / 组件 Contract | 实施流水账 |
| 代码属于哪里 | 所属 `ARCHITECTURE.md` | 完整业务状态机、视觉参数 |
| 实现必须遵守什么 | 就近 `*_RULES.md` | 已完成计划、其他模块的规则副本 |
| 如何重复验证 | 现有测试、脚本、Workflow、E2E 操作手册 | 本次执行结果 |
| 本次变更实际验证了什么 | PR / CI 记录 | 对后续提交永久有效的通过结论 |
| 某版本包含什么、能否发布 | 版本 Contract / Notes / Readiness / Evidence | 日常开发所需的完整现行契约 |

## Capability Contract

按能力组织当前行为、身份与状态、不变量、失败边界、兼容性、代码入口和验证入口。小能力不必填满模板，也不为每个 PR 新建一份文档。

未实现方案必须明确标注为提案，不能混入当前行为。实现落地后原位更新正文，替换失效描述；不要在旧结论后追加“某 PR 已完成”。有价值的设计原因保留简短说明，实施顺序留在 PR / Git History。

版本号用于兼容性和发布边界时保留。PR 链接可以作为追溯证据，但不能要求读者还原 PR1、PR2 的先后顺序才能得出当前规则。

## Code Rules

全局规则管共性，就近规则管对应范围的约束。组件特有的行为由组件文档定义，通用 UI 规则只引用；业务 Contract 不在 Architecture、Rules 和 Release 中各复制一份。

可直接从类型、配置、Token 或脚本读取的参数不手工铺成第二套清单；必要的默认值、稳定保证和不易从代码推断的原因可以写入契约，并链接实现入口。

## Architecture

只描述当前模块、目录、职责和依赖边界；不保留已结束的迁移方向，不把计划中的层级写成既有结构。

## Context Loading

路由由 [AGENTS.md](../AGENTS.md) 维护，知识导航由 [Docs Index](README.md) 维护。业务任务读取对应能力，组件任务读取对应组件；不默认加载发布历史或所有验收场景。

`Depends On` 表示适用约束，不表示无限递归阅读。Release 可以引用 Capability 的当前规则，Capability 的日常理解不应依赖整份版本历史；必要的升级差异只链接对应发布段落。

## Conflict Handling

文档声明“应该怎样”，代码和测试说明“实际怎样、验证到哪里”；不能自动认为任意一方永远正确。

发现冲突时，核对同一提交的权威契约、实现、Schema / 配置和相关测试。确认是文档过期则修正文档；确认是实现回归则单独修复代码。尚无法判断时在 PR 列明差异和待确认点，不删除有效约束，也不把已知缺口改写成既定保证。

旧 Phase / PR 段落不是能力已实现或未实现的可靠证明。文档整理不授权修改业务行为，也不代表所有被链接的专题已经完成一致性校验。

## Verification

仓库维护自动化测试、GitHub Actions CI 和手工 E2E；验证方式与一次执行的事实分开维护。

验证入口：

- [Java Rules](../JAVA_RULES.md)：本地后端格式与验证命令。
- [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)：前端格式、Lint、类型、架构和构建门禁。
- [Quality Check](../.github/workflows/quality-check.yml)：普通前后端质量检查。
- [JDBC Source Acceptance](../.github/workflows/jdbc-source-acceptance.yml) / [JDBC Sink Cross-Database Acceptance](../.github/workflows/jdbc-sink-cross-database-acceptance.yml)：保留真实数据库验收，普通 PR 不自动触发；按需手动运行，正式 Release Gate 复用。
- [Backend Acceptance](../.github/workflows/backend-acceptance.yml)：产品 Runtime E2E 的状态及完整门禁；链路未恢复时明确 fail-closed，不把独立 JDBC 验收当成完整产品验收。
- [Data Sync Manual E2E](e2e/data-sync/README.md)：可重复执行的产品级人工步骤。
- [Version Management](release/README.md)：版本验收、发布决策及证据要求。

命令与触发条件以对应规则、脚本和 Workflow 为入口，不在这里复制第二份实现。

## Evidence Chain

一次验证记录必须能对应到实际提交、环境、命令或场景、结果及日志位置。CI 还需关联 Workflow Run / Job；手工验收记录执行人、时间和证据。普通变更放在 PR，版本发布放在对应 Readiness / Evidence。

必须区分本地检查、CI、独立夹具与真实产品 E2E。`skipped` 不是该项验收通过，历史绿色基线不是新提交的证明；没有执行就明确写未执行。可复用步骤长期保留，一次性的错误排查和环境限制不写进当前能力正文。

## Document Creation Rule

更新优先于新增，引用优先于复制：

- 新增前确认现有文档能否承载；仅独立职责、不同读者或确有按需加载价值时拆分，不按 PR 数量增加文件。
- 修改规则时同时替换同一范围内失效的正文并修正直接引用，不保留互相矛盾的旧、新两版结论。
- E2E 保留准备、步骤、预期和清理；发布证据保留对应版本事实，不作为普通任务默认上下文。
- 变更文档路径、锚点或机器读取字段前检查消费者。发布门禁依赖的 Readiness / Notes 路径和 `Status: Ready` 不得作为普通精简随意删除或改名。

## Principle

文档描述当前系统，PR 记录变更过程，验证证据绑定实际执行；不把三者混成一份历史日记。
