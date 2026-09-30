# Data Sync Task Editor Rules

Status: Active

Scope: `app/data-sync/task-editor.tsx` 及其编辑器辅助组件。

业务语义见 [Data Sync Contract](../../../../../docs/capabilities/data-sync/README.md)；App 职责见 [App Rules](../../APP_RULES.md)。实例列表和任务运维虽复用 `app/data-sync` 目录，但不是编辑器的职责。

## Ownership

DataSyncTaskEditorPage 显式接收 syncType，不按 URL pathname 猜模式。OFFLINE / REALTIME 共用数据源选择、绑定范围展示、Catalog 查询、字段映射和保存实现；差异留在模式参数、运行配置、文案及返回路径，不复制完整编辑器。

## Data / Validation

- REALTIME Source 只展示 MySQL，Target 只展示 MySQL / PostgreSQL / Oracle；最终拓扑、主键和兼容性由后端校验。
- Datasource 保存 ID、显示名称；表选择保留稳定身份与异步回显，遵循 [Select](../../../../packages/yak-ui/docs/select-motion.md)。数据源已绑定的数据库/Schema 不提供重复覆盖输入。
- 只读展示后端自动同名映射，不在前端重建 JDBC 类型兼容或 Transform。修改依赖字段后重新查询，过期结果不得覆盖当前选择。
- OFFLINE 只发送 runtimeConfig；REALTIME 只发送 realtimeConfig，Task writeMode 保持 APPEND。已有 retryPolicy 随任务加载/保存，不因缺少专用配置 UI 而丢失。
- 不暴露 Debezium 状态目录、offset、schema history 或 serverId。

## Save / Publish

编辑接口要求任务未上线。已上线任务进入编辑页时只提供说明和返回入口，不形成另一条可编辑路径。

保存是 Task → 可选 OFFLINE Schedule；保存并上线是 Task → Schedule → Publish。Schedule 失败不继续 Publish，明确“任务已保存但调度失败”；Publish 失败保留已保存且未上线的任务，并返回可继续处理的编辑身份。保存不隐式启用调度，也不启动任务。

新任务未配置 Cron 且没有历史 Schedule 时为手工运行；已有 Schedule 不能用清空 Cron 冒充删除。Cron / Time Zone 保存与运维中心启停分开，运行时间从后端读模型获取。

## Layout

OFFLINE 编辑区使用 [SectionCard](../../../../packages/yak-ui/docs/section-card.md) 按“基本信息 / 数据源 / 数据来源 / 数据去向 / 字段映射 / 调度配置 / 运行参数”分块展示；SectionCard 自身就是每个区块的唯一外层 Surface，不再额外包整页 Card，也不在区块内重复套一层同职责 Card。运行参数在 OFFLINE 中保持直接可见，不再通过折叠隐藏。

REALTIME 继续使用 [CollapseSection](../../../../packages/yak-ui/docs/controls.md#collapsesection)，保持现有折叠交互和内容 Surface，不因 OFFLINE 布局收口而同步迁移。

OFFLINE 可编辑页面使用父容器高度、固定 PageHeader 与左侧局部滚动；右侧锚点导航在滚动区外，不能跟随内容滚走。REALTIME 当前保持原页面滚动方式，不把离线布局描述为两种模式都已采用。具体列宽与断点归 [task-editor.tsx](task-editor.tsx)，不照抄普通管理 Modal 的密度覆盖配置页。

## Risk Presentation

持久风险使用 [Alert](../../../../packages/yak-ui/docs/alert.md)：未上线前置条件、OVERWRITE、Split 快照限制、CDC 前置条件及执行定义变化后的重新快照。普通帮助保留说明文字；不将“保存成功”当成同步结果。

## Verification

在真实页面验证离线/实时加载和异步回显、上线不可编辑、Schedule/Publish 部分失败、配置保存不自动运行；OFFLINE 额外验证 SectionCard 分块、无整页外层 Card、标题/锚点固定与内容滚动，REALTIME 保持 CollapseSection 折叠交互。业务结果验证复用已有 E2E，不在本规则记录一次执行的 PASS。
