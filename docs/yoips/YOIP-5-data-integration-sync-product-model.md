# YOIP-5: Data Integration Synchronization Model + Product Interaction

Status: Discussion

Discussion: Pending proposal PR link

Decision: Pending

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

统一数据集成中的数据同步产品模型，以数据源类型、具体同步类型和同步步骤组织创建流程。

## Public Interfaces

提案范围包括统一前端路由、同步方案选择、版本化任务参数以及执行阶段展示。此 PR 只包含设计文档。

## Proposed Changes

数据同步采用一个列表、一个创建入口、一个编辑器和一个详情页；离线、实时属于同步方案，不再拥有独立页面。

## Compatibility, Deprecation, and Migration Plan

保留现有 Task 身份、Workspace 隔离、不可变版本、Schedule 与 Instance 的独立生命周期。旧地址转换到统一入口。

## Test Plan

设计正文将定义创建、回显、配置切换、合法步骤、迁移与执行展示的验收场景；此文档不构成实现或验收通过的声明。

## Rejected Alternatives

不采用离线、实时两个页面隐藏在 Tab 后面的方式，也不为每个同步组合新增 Task 插件或执行引擎。
