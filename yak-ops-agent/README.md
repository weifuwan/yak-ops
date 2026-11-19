# yak-ops-agent（AgentScope Python harness）

数据同步页面的完整功能 agent（查询 + 创建/修改/发布/下线/删除 + 实例停止/强制成功），基于 AgentScope 构建。
平台侧契约与安全边界见 `yak-ops-openapi/`（契约基线与 agent 白名单）。

## 定位

- 消费方形态：外部 agent 进程（Python），通过 yak-ops REST API 接入；
- 编排：AgentScope `Agent`（ReAct 循环）开箱即用；本仓库零自定义框架代码；
- 安全模型：读工具自动执行（只读快路径）；写工具一律走 **HITL 人工确认**（`RequireUserConfirmEvent`），非交互模式默认拒绝写操作；
- PTC 对齐：`ContextConfig.tool_result_limit` 与客户端 16KB 截断保证中间结果不撑爆上下文。

## 内置能力

| 能力 | 说明 |
| --- | --- |
| **Skill（技能）** | `yak_agent/skills/data-sync-troubleshooting/SKILL.md`：数据同步排障知识（状态语义、排查步骤、常见根因、耗时口径、高危红线），模型按需加载 |
| **审计留痕** | 每次工具调用落 JSONL（`logs/audit/YYYY-MM-DD.jsonl`，`YAK_AGENT_AUDIT_DIR` 可改）：时间、工具、脱敏参数、成功与否、耗时——`parameters_json` 类参数只记占位符，不落盘数据源凭据 |
| **短期记忆** | Agent 内部 context：同一会话内后续问题复用已取数据（省 token） |
| **Middleware** | `ReplyBudgetControlMiddleware`：单回复加权 token 预算（输出 ×2，`YAK_AGENT_REPLY_TOKEN_BUDGET` 默认 25000），超限后收敛不再调工具——长回合的成本/时延护栏；`TracingMiddleware`：OpenTelemetry 全链路追踪，未配置 TracerProvider 时零开销，配置 collector（OTLP）后输出完整 span 树（reply/model_call/tool 层级含 token 计数与 HITL 状态） |
| **PTC（程序化工具调用）** | `run_analysis_script`：模型写 Python 脚本在受限执行器里跑，脚本经生成的只读 `yak_agent_sdk` 调 yak-ops API（含 `download_task_log` 全量日志），**只有结论进上下文**；写操作在 SDK import 时硬禁，静态预检拒绝写符号，超时/输出截断/全量审计 |
| **会话工作区** | 每会话持久目录（`sessions/{sid}/workspace/`）：脚本产物跨轮存活；stdout 超长时自动 offload 落盘；`read_workspace_file` / `list_workspace_files` 只读工具回读（路径逃逸防护）。执行后端可插拔：本地=cocoon 沙箱（默认）或受限子进程，生产=DockerWorkspace |

## 工具（数据同步，7 读 + 7 写）

| 读工具（自动执行） | REST 端点 |
| --- | --- |
| `list_sync_tasks` | `GET /projects/{code}/data-sync/tasks`（keyword/status 过滤） |
| `get_sync_task` | `GET .../tasks/{taskCode}`（含 parameters 模板、revision、definitionVersion） |
| `preview_sync_task` | `POST .../tasks/preview`（校验参数并渲染 YAML，无副作用） |
| `get_sync_overview` | `GET .../overview` |
| `list_sync_instances` | `GET .../instances`（status/taskCode 过滤） |
| `get_sync_instance_detail` | `GET .../instances/{id}` |
| `query_task_log` | `GET /log/detail`（日志分页，limit≤1000） |
| `download_task_log` | `GET /log/download-log`（完整日志文件一次下载，PTC 分析用） |

| 写工具（需人工确认） | REST 端点 |
| --- | --- |
| `create_sync_task` | `POST .../tasks` |
| `update_sync_task` | `PUT .../tasks/{taskCode}`（带 expectedRevision 乐观锁） |
| `publish_sync_task` | `POST .../tasks/{taskCode}/publish` |
| `unpublish_sync_task` | `POST .../tasks/{taskCode}/unpublish` |
| `delete_sync_task` | `DELETE .../tasks/{taskCode}`（不可恢复） |
| `stop_task_instance` | `POST .../task-instances/{id}/stop` |
| `force_success_task_instance` | `POST .../task-instances/{id}/force-success` |

安全边界（宿主侧固化，模型不可见/不可传）：凭证注入（每请求的 session cookie 或 token）、projectCode 来自会话绑定的工作空间、`Result<T>` 业务码校验、16KB 截断、写操作 HITL 确认。

## 使用体验（完整 agent 流程示例）

```
你> 照着 orders_daily 建一个 orders_daily_v2，目标表改成 orders_v2
    → list_sync_tasks（找 orders_daily）→ get_sync_task（取参数模板）
    → preview_sync_task（校验 YAML）
    → —— 需要确认的操作 ——  create_sync_task({...})  确认执行? [y/N] y
    → 创建成功（OFFLINE）→ publish_sync_task（再次确认）→ 已上线
你> 最近一次失败是什么原因
    → list_sync_instances(status=FAILURE) → query_task_log → 结论 + 实例 ID/日志行
你> 把任务 42 删了
    → 模型先说明影响并建议以下线替代 → delete_sync_task（确认框）→ 用户拒绝则停止
```

## 安装

```bash
cd yak-ops-agent
python -m venv .venv
.venv/Scripts/python -m pip install -e . -i https://pypi.tuna.tsinghua.edu.cn/simple
```

依赖清单见 `pyproject.toml`（含 AgentScope、FastAPI/uvicorn、httpx、Pydantic、cocoon 沙箱等）；开发环境用 `pip install -e ".[dev]"` 一并安装 ruff 与 pre-commit。

要求 Python 3.11+（AgentScope 2.0 硬要求）。

## 配置

复制 `.env.example` 为 `.env`，只需填写模型接入：`LLM_BASE_URL`（openai-compatible 端点）、`LLM_API_KEY`、`LLM_MODEL`。

平台账号与项目不再写入配置：用户身份与工作空间由前端逐请求传入（见下方"鉴权两层"）。CLI 用 `--project-code` 指定工作空间，用 `--session-id` 或 `YAK_OPS_TOKEN` 认证。

## 运行

```bash
# 交互 CLI（写操作逐条确认）
.venv/Scripts/python -m yak_agent.cli --project-code <workspaceId>
# 单问（非交互，写操作自动拒绝）
.venv/Scripts/python -m yak_agent.cli --project-code <workspaceId> "现在有哪些同步实例在运行？"
# HTTP 服务（给前端消费；默认 127.0.0.1:8090）
.venv/Scripts/python -m yak_agent.server
```

## HTTP 接口（前端对接）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/agent/sessions` | 创建会话 → `{sessionId}` |
| POST | `/agent/sessions/{sid}/messages` | 发消息（body `{message}`）→ **SSE 事件流** |
| POST | `/agent/sessions/{sid}/confirm` | 确认/拒绝挂起的写操作（body `{approved: bool 或 [bool...]}`） |
| GET | `/agent/sessions` | 列出**当前用户在当前工作空间**的已持久化会话 |
| DELETE | `/agent/sessions/{sid}` | 删除会话（进行中的会话返回 409，需先结束本轮） |
| GET | `/health` | 存活检查 |

**鉴权两层**：

1. **服务层**（保护 agent 服务本身）：配置 `YAK_AGENT_SERVICE_TOKEN` 后所有端点要求 `Authorization: Bearer <token>`；未配置时仅接受 loopback 调用，非本机来源 401。
2. **用户层**（数据权限边界，与原后端同一套登录认证）：前端每次请求携带三个头——`X-Yak-Session-Id`（登录换得的 sessionId）、`X-Yak-User-Id`（平台用户 id）、`X-Yak-Workspace-Id`（当前工作空间，即 projectCode）。服务端用 sessionId 调 `GET /users/get-user-info` 反查用户并校验前端传入的 user id 一致，再调 `GET /projects` 校验该工作空间已授予该用户（401/403 透传）。会话与工作空间强绑定，之后**所有工具调用与 PTC 脚本都以该用户身份、该工作空间为 projectCode 执行**。归属键优先用「用户 id + createTime」派生；`createTime` 缺失的历史用户退化为登录 session 绑定的进程内非持久身份，会话只在当前进程内可用、不落盘，避免自增 id 复用继承旧会话。确认动作的调用方写入审计。

SSE 事件类型：

| 事件 | 说明 |
| --- | --- |
| `text_delta` | 回复文本增量（`delta`，带 `replyId` / `blockId`） |
| `thinking_start` / `thinking_delta` / `thinking_end` | 模型推理过程流，同一 `blockId` 分组 |
| `tool_start` | 工具调用开始（`tool`、`toolCallId`） |
| `tool_call_delta` | 工具入参 JSON 片段（`delta`） |
| `tool_result_delta` | 工具结果文本片段（`delta`） |
| `tool_result` | 工具调用结束（`state`：success/error/denied/interrupted） |
| `usage` | 单次模型调用 token 用量（含 cache 计数） |
| `reply_end` | 一轮回复结束（`finishedReason`：completed/interrupted/exceed_max_iters/error，含 `error`） |
| `confirm_required` | 写操作待确认，含 `toolCalls[]` |
| `done` / `error` | 本次消息流结束 / 异常 |

**确认期间连接保持打开**——前端收到 `confirm_required` 后调用 `/confirm` 并在同一连接继续接收后续事件。

会话按轮持久化（`AgentState` 序列化，`YAK_AGENT_SESSION_DIR` 可配），服务重启后按会话 ID 懒加载恢复；`createTime` 缺失的历史用户不写盘，仅当前进程内可用。服务端口与绑定地址：`YAK_AGENT_PORT` / `YAK_AGENT_HOST`。

## 已知边界

- 前端 assistant drawer 的对接接口由 HTTP 服务（`python -m yak_agent.server`）提供，CLI 为本地运维入口；
- 工具调用审计以 JSONL 形式落盘（`logs/audit/`），可配置重定向；
- 创建/修改任务需要完整的 pipeline `parameters` JSON——system prompt 引导模型从 `get_sync_task` 取模板修改并用 `preview_sync_task` 验证，避免凭空构造；
- 验收（5 个只读 Golden 问题、冷启动稳定性、写操作全生命周期演练）在开发环境以真实模型与真实后端执行；验收脚本强依赖 LLM 与内网后端、运行会产生真实副作用与调用成本，因此**不随仓库分发**。
