# Data Sync Task Editor Rules

Status: Active

Scope: `app/data-sync/task-editor.tsx` 及其编辑器辅助组件。

业务语义见 [Data Sync Contract](../../../../../docs/capabilities/data-sync/README.md)；App 职责见 [App Rules](../../APP_RULES.md)。实例列表和任务运维虽复用 `app/data-sync` 目录，但不是编辑器的职责。

## Ownership

DataSyncTaskEditorPage 显式接收 syncType，不按 URL pathname 猜模式。OFFLINE / REALTIME 共用数据源选择、Catalog 查询、字段映射和保存实现；差异留在模式参数、运行配置、文案及返回路径，不复制完整编辑器。

## Data / Validation

- REALTIME Source 只展示 MySQL，Target 只展示 MySQL / PostgreSQL / Oracle；最终拓扑、主键和兼容性由后端校验。
- Datasource 保存 ID、显示名称；表选择保留稳定身份与异步回显，遵循 [Select](../../../../packages/yak-ui/docs/select-motion.md)。数据源已绑定的数据库/Schema 不提供重复覆盖输入。数据源卡片的“类型”复用 datasource 模块已有 DatabaseIcons 与产品化类型名称（MySQL / PostgreSQL / Oracle），不直接展示后端枚举，也不重复显示“连接范围”等说明性副文本。
- Schema Mapping Editor 只负责字段选择、改名、重排和连线交互，不在前端重建 JDBC 类型兼容、主键契约或 Transform；每次 Mapping 变化都调用后端 Mapping Preview，兼容性、类型与 DDL 结果以后端为准。修改依赖字段后重新查询，过期结果不得覆盖当前选择。同一 Source / Target Scope 内仅 Mapping 变化时，Preview 刷新不得先清空上一份有效结果造成 Header / Diagnostics 反复挂载；使用独立 loading 状态阻断保存并保持布局稳定。Scope 身份变化时才清空旧 Preview，避免跨表展示陈旧状态。
- 编辑器对用户展示的章节名称统一使用“去向字段映射”；内部 Contract / 类型 / 代码仍可使用 Schema Mapping，不为了文案重命名技术模型。
- Mapping Preview 的字段名、字段类型与映射关系直接回收到 Schema Mapping Editor：不再在编辑器下方重复渲染第二张字段映射明细表。CollapseSection Header 只保留目标表 / 兼容性摘要；正文只保留阻断原因与规划警告，不再铺开自动建表 DDL。自动建表 DDL 属于“数据去向 → 目标表”的辅助信息，只能通过目标表右侧的 Yak UI `DDL` Button 按需打开 Popover 查看。Popover 必须展示后端返回的完整 ddlStatements（CREATE TABLE + 可能的 Comment DDL），不能只展示第一条 CREATE TABLE；createTableSql 仅作为旧响应兼容回退。无实际诊断内容时 SchemaPreviewDiagnostics 必须直接返回 null，不得渲染零高度空容器参与父级 gap / space 布局。
- Task Editor 默认不发送 runtimeConfig / realtimeConfig / retryPolicy；运行策略由后端拥有。REALTIME Task writeMode 保持 APPEND。
- Runtime tuning 与 Retry Policy 从 Task Editor 内化：普通用户不直接配置 fetch / batch / split / parallelism / checkpoint / queue / timeout / maxAttempts / backoffSeconds。前端 Save Request 默认省略 runtimeConfig / realtimeConfig / retryPolicy，由后端在创建时物化系统默认值、编辑时保留已有具体值。
- Runtime Config / Retry Policy 仍是 Task / Execution 的执行事实，查询与详情可以展示；编辑器隐藏参数不等于删除 Contract，也不得在前端重新维护一套默认值。新建普通任务的 Retry Policy 由后端物化为 SMART，前端不复制瞬时异常分类、写入安全判断或退避算法。
- Column Mapping Editor 支持同名映射、同行映射（按 Catalog 顺序）、单条添加 / 修改、删除、清空、字段搜索、点击连接和拖拽连接。已有目标表只能选择真实 Catalog 目标字段；自动建表且目标表不存在时允许输入自定义目标字段名。编辑已有任务时 hydrate 已持久化 mapping；新任务 mapping 为空时 UI 按后端隐式同名语义展示，但只有用户修改后才物化为显式 mapping。Source / Target 数据源、Schema、表或自动建表模式变化时清空旧 Mapping，避免旧 Schema 身份被带到新范围。
- 清空 Mapping 只允许作为编辑中间态：前端不向 Preview / Save 发送空 columns，保存按钮保持不可用，至少恢复一条 Mapping 后才能保存。
- 不暴露 Debezium 状态目录、offset、schema history 或 serverId。

## Save / Publish

编辑接口要求任务未上线。已上线任务进入编辑页时只提供说明和返回入口，不形成另一条可编辑路径。

保存是 Task → 可选 OFFLINE Schedule；保存并上线是 Task → Schedule → Publish。Schedule 失败不继续 Publish，明确“任务已保存但调度失败”；Publish 失败保留已保存且未上线的任务，并返回可继续处理的编辑身份。保存不隐式启用调度，也不启动任务。

新任务未配置 Cron 且没有历史 Schedule 时为手工运行；已有 Schedule 不能用清空 Cron 冒充删除。Cron / Time Zone 保存与任务列表启停分开，运行时间从后端读模型获取。

OFFLINE Cron 使用 Yak UI `CronSchedulerPicker`，主表单不再要求用户直接手输表达式；高级 Cron 仍可在 Picker 内原样保留。Time Zone 使用 Select，默认 `Asia/Shanghai`，并保留历史已有 IANA Zone 值。Picker 的“未来 5 次执行时间”只调用 Data Sync Schedule Preview API，不在前端自行计算 Quartz 触发时间。普通 Cron/Time Zone 帮助文案和 Schedule enable/disable Alert 不重复铺在定义表单；启停属于 OFFLINE Task list。

## Layout

编辑区使用 [CollapseSection](../../../../packages/yak-ui/docs/controls.md#collapsesection)。基本信息、数据源、来源、去向、映射及 OFFLINE 调度默认展开；Task Editor 不再展示重试策略或运行参数章节。共享组件管理标题交互；数据同步内容继续保留白色 Card + border，不把业务内容外观改成共享组件默认规则。目标表 Select 与自动建表 Input 在桌面端都通过外层 Control Slot 约占内容区 50%，不要直接与 Yak UI Input / Select 内部的 `w-full` 抢宽度；窄屏恢复弹性宽度。自动建表时 `DDL` Button 紧跟 Control Slot 右侧，非自动建表不保留空 Action 位。DDL Popover 复用 Yak UI Popover，默认从按钮右侧打开，宽度约 720px、受视口约束，代码区设置 max-height 并独立滚动。CREATE TABLE 预览使用受控轻量 Formatter：只在最外层字段定义逗号处换行，不能拆坏 DECIMAL / PRIMARY KEY 等嵌套括号；多个 DDL statement 之间保留空行。代码区使用浅灰背景与接近 SQL 阅读器的克制配色：Keyword 使用粉红系、数据类型使用紫色、String 使用绿色、Number 使用红粉色、Identifier / 普通文本保持深灰；不为预览引入 Monaco / CodeMirror / Prism / Shiki 等重依赖。

Schema Mapping Editor 保持历史字段映射的紧凑表格视觉：左右分别是“字段 / 类型”行，中间只承载映射连线。左右字段必须共享同一个纵向滚动坐标系，不允许各自独立滚动；连接点由 Mapping Overlay 按字段行位置绘制，不得通过负 offset 把节点塞进字段列表的 overflow 区域。所有映射固定使用 SVG 直线：同行为水平线，跨行为斜线，不使用 Bezier 曲线。Connector 的视觉节点与命中区域必须分离：默认使用约 8px 的紧凑菱形，实际可交互 Hit Area 约 20px；Hover Connector 时显示白底轻边框的“+”连接反馈并使用 pointer cursor，不能把静态状态点当成唯一可操作提示。字段不得改回逐项大圆角 Card。常规小表不常驻搜索框；字段较多时再显示左右搜索。连线 Hover 的删除 / 修改操作必须复用 Yak UI Button，并保持从连线移动到按钮时 Hover 不闪退；兼容性异常直接反映在线条与端点状态上。

OFFLINE 可编辑页面使用父容器高度、固定 PageHeader 与左侧局部滚动；右侧章节导航在滚动区外，不能跟随内容滚走。章节导航使用 Data Sync 本地 EditorAnchorStepper：白色 Card、8px 节点、1px 纵向连接线，当前章节节点与文字使用 Yak 主色，其余使用中性灰；不使用整行 Hover 背景。滚动内容时 Stepper 必须自动跟随当前 CollapseSection，点击节点使用 smooth scroll 定位。OFFLINE 监听 main 局部滚动容器，REALTIME 监听 viewport，并继续按模式决定是否展示调度配置节点；Stepper 不再展示重试策略 / 运行参数节点。窄屏沿用当前规则隐藏右侧导航。该 Stepper 先保持 Data Sync 本地组件，不提前提升到 Yak UI。具体列宽与断点归 [task-editor.tsx](task-editor.tsx)，不照抄普通管理 Modal 的密度覆盖配置页。

## Risk Presentation

持久风险使用 [Alert](../../../../packages/yak-ui/docs/alert.md)：未上线前置条件、OVERWRITE、Split 快照限制、CDC 前置条件及执行定义变化后的重新快照。普通帮助文案默认不展示，优先通过字段名称、控件、Placeholder、状态与校验结果表达；只有数据风险、执行前置条件、阻断原因或重要执行差异允许额外提示，并遵循 [Frontend Rules](../../../../FRONTEND_RULES.md#ui-copy-minimalism)。不将“保存成功”当成同步结果。

## Verification

在真实页面验证离线/实时加载和异步回显、上线不可编辑、Schedule/Publish 部分失败、配置保存不自动运行，以及离线标题/锚点固定和折叠交互。业务结果验证复用已有 E2E，不在本规则记录一次执行的 PASS。
