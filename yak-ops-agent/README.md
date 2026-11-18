# yak-ops-agent（AgentScope Python harness）

数据同步页面的完整功能 agent（查询 + 创建/修改/发布/下线/删除 + 实例停止/强制成功），**替代已移除的 Java agent 模块**。
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

安全边界（宿主侧固化，模型不可见/不可传）：凭证注入（token 或 session cookie）、`projectCode` 来自配置、`Result<T>` 业务码校验、16KB 截断、写操作 HITL 确认。

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
.venv/Scripts/python -m pip install "agentscope==2.0.12" -i https://pypi.tuna.tsinghua.edu.cn/simple
```

要求 Python 3.11+（AgentScope 2.0 硬要求）。

## 配置

复制 `.env.example` 为 `.env` 填写：`LLM_BASE_URL`（openai-compatible 端点）、`LLM_API_KEY`、`LLM_MODEL`、`YAK_OPS_PROJECT_CODE`（项目雪花 ID，必填）。

**认证（二选一，token 优先）**：
- 当前默认：`YAK_OPS_USERNAME` + `YAK_OPS_PASSWORD`——`POST /login` 换 `sessionId` cookie，后续请求自动携带；session 过期（401）自动重登重试一次；
- 平台 API token 功能上线后：`YAK_OPS_TOKEN`（设置后忽略用户名密码）。

## 运行

```bash
# 交互 CLI（写操作逐条确认）
.venv/Scripts/python -m yak_agent.app
# 单问（非交互，写操作自动拒绝）
.venv/Scripts/python -m yak_agent.app "现在有哪些同步实例在运行？"
# HTTP 服务（给前端消费；默认 127.0.0.1:8090）
.venv/Scripts/python -m yak_agent.server
# 只读冒烟（5 个 Golden 问题 + 冷启动稳定性探针）
.venv/Scripts/python smoke/smoke_test.py --repeat 10
```

## HTTP 接口（前端对接）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/agent/sessions` | 创建会话 → `{sessionId}` |
| POST | `/agent/sessions/{sid}/messages` | 发消息（body `{message}`）→ **SSE 事件流** |
| POST | `/agent/sessions/{sid}/confirm` | 确认/拒绝挂起的写操作（body `{approved: bool 或 [bool...]}`） |
| GET | `/agent/sessions` | 列出已持久化会话 |
| DELETE | `/agent/sessions/{sid}` | 删除会话 |
| GET | `/health` | 存活检查 |

SSE 事件类型：`text_delta`（增量文本）、`tool_start` / `tool_result`（工具调用）、`confirm_required`（写操作待确认，含 `toolCalls[]`）、`done`、`error`。**确认期间连接保持打开**——前端收到 `confirm_required` 后调用 `/confirm` 并在同一连接继续接收后续事件。

会话按轮持久化（`AgentState` 序列化，`YAK_AGENT_SESSION_DIR` 可配），服务重启后按会话 ID 懒加载恢复。服务端口与绑定地址：`YAK_AGENT_PORT` / `YAK_AGENT_HOST`。

## 已知边界

- Java `AgentChatController` 已随旧模块移除，前端 assistant drawer 的 SSE 接口暂缺；本模块提供 CLI 入口，HTTP 兼容层在闭环验收后按需补；
- 建表机制（四张审计表）随 Java 模块移除，审计留痕在 Python 侧复跑 Golden 前另行落地；
- 创建/修改任务需要完整的 pipeline `parameters` JSON——system prompt 引导模型从 `get_sync_task` 取模板修改并用 `preview_sync_task` 验证，避免凭空构造；
- 冒烟脚本只覆盖只读场景；写操作的确认流由写操作演练脚本（`smoke/write_drill.py`）端到端验证。
