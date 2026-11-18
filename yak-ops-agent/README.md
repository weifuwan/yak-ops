# yak-ops-agent（AgentScope Python harness）

数据同步只读运维助手的 AgentScope 2.0 实现，**替代已移除的 Java agent 模块**。
平台侧契约与安全边界见 `docs/openapi/`（契约基线）与 `D:/tianxy/code/yak-ops-worktrees/agent-openapi-plan-20261118.md`（接入方案 v1.5）。

## 定位

- 消费方形态：外部 agent 进程（Python），通过 yak-ops REST API 只读接入；
- 编排：AgentScope `Agent`（ReAct 循环）开箱即用；本仓库零自定义框架代码；
- PTC 对齐：代码节点收集数据 + 模型只做判断（形态 1）；`ContextConfig.tool_result_limit` 与客户端 16KB 截断保证中间结果不撑爆上下文。

## 工具（首批，数据同步只读）

| 工具 | REST 端点 | 说明 |
| --- | --- | --- |
| `list_sync_instances` | `GET /projects/{code}/data-sync/instances` | 实例列表（status/taskCode 过滤，pageSize≤50） |
| `get_sync_instance_detail` | `GET /projects/{code}/data-sync/instances/{id}` | 实例详情 |
| `query_task_log` | `GET /log/detail` | 任务日志尾部（limit≤1000 行，头部丢弃保留尾部） |

安全边界（宿主侧固化，模型不可见/不可传）：`token` 请求头注入、`projectCode` 来自配置、`Result<T>` 业务码校验、16KB 截断。

## 安装

```bash
cd yak-ops-agent
python -m venv .venv
.venv/Scripts/python -m pip install "agentscope==2.0.12" -i https://pypi.tuna.tsinghua.edu.cn/simple
```

要求 Python 3.11+（AgentScope 2.0 硬要求）。

## 配置

复制 `.env.example` 为 `.env` 填写：`LLM_BASE_URL`（内网 openai-compatible 端点）、`LLM_API_KEY`、`LLM_MODEL`、`YAK_OPS_TOKEN`（agent 专用账号 token，经 `/access-tokens` 创建）、`YAK_OPS_PROJECT_CODE`。

## 运行

```bash
# 单问
.venv/Scripts/python -m yak_agent.app "昨天有哪些数据同步任务失败了？"
# 交互 CLI
.venv/Scripts/python -m yak_agent.app
# 冒烟（5 个 Golden 问题 + 重复稳定性探针）
.venv/Scripts/python smoke/smoke_test.py --repeat 10
```

## 已知边界

- Java `AgentChatController` 已随旧模块移除，前端 assistant drawer 的 SSE 接口暂缺；本模块当前提供 CLI/单问入口，HTTP 兼容层（`/agent/*`）在闭环验收后按需补；
- 建表机制（四张审计表）随 Java 模块一并移除，审计留痕在 Python 侧复跑 Golden 前另行落地；
- 白名单扩展（workflow/monitor 等）与契约治理推进同步：工具集以 `yak-ops-agent-3.1.json` 白名单子集契约为事实源（待 L1/L2 落地后由脚本生成，当前手工对齐 PoC 覆盖面）。
