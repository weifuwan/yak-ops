# Agent 长期记忆：常驻 Profile 与显式写入

## 目标

让「我是谁、用户是谁、用户定过的硬偏好」这类**必须始终成立**的事实，在任何新会话的第一轮就出现在上下文里，并且优先级高于提示词里的默认角色描述；不再依赖每轮检索是否恰好命中。

本方案只解决身份/称呼/偏好这一层，不改造 ReMe 的情节记忆与流程记忆。

## 背景

### 事故

新会话被问「你是谁 / 你叫啥」时，agent 回答「我是数据同步助手，没有单独的名字」。用户纠正「昨天还给你取名了」之后，agent 主动调用 `memory_search` 才检索到：

- 助手称呼：狗蛋（用户先看到模型自称「小同」，纠正为「错 你叫狗蛋」）
- 用户称呼：达摩
- 硬性偏好：被问到「你是谁」时，回答必须包含名字

记忆数据没有丢。对应卡片就在当前工作区的 `digest/personal/assistant-identity-goudan-user-damo.md`（`bucket: personal`、`kind: identity`），关键词「狗蛋」「达摩」都能命中。

### 实测召回结果

用本地该工作区的 memory 目录直接调用 `YakReMeMiddleware.search(query, limit=5)`：

| query | 命中数 | identity 卡片是否命中 |
| --- | --- | --- |
| `你好` | 3 | 否（其中一条正文恰好含「你是谁」字样） |
| `你是谁` | **0** | 否 |
| `你叫什么名字` | 5 | **否**（返回整库同步失败、任务创建等业务卡） |
| `我叫什么` | 1 | 否（不相关） |
| `狗蛋` / `达摩` | 5 | **是，第 1 条** |

结论：卡片存在、索引存在，但短指代问句召回不到。

### 三关失败链

一次身份提问要正确回答，需要连续通过三道门，任一失败都会退回默认设定：

1. **召回门（系统性失败，不是偶发抖动）**
   - `service/chat.py:107` 的 `_inject_memory` 每轮都用**用户原话**做 query 检索，命中后注入 hint（`chat.py:174`）。自动检索本来就有。
   - 但 `memory/registry.py:39-44` 创建 ReMe 参数时 `embedding_model=None`。AgentScope 的实现写明「Providing an `embedding_model` enables the vector store; otherwise search stays keyword-only」（`.venv/Lib/site-packages/agentscope/middleware/_longterm_memory/_reme/_middleware.py:98-99`），所以检索实际走关键词路径；「你 / 是 / 谁 / 我 / 什么」在短问句里切不出有效词，BM25 命中不到。
   - `top_k=5`、`min_score=0`、`candidate_multiplier=3` 的配置（AgentScope 内置 ReMe 的 `_reme/_config.py:102`）让稳定的身份事实被业务卡挤出前 5。

2. **采纳门（提示词优先级缺失）**
   - `yak_agent/prompts/AGENT.md:15-23` 的「自我介绍」规定按「平台定位 + 本次会话已接入领域」回答，完全没有记忆的位置。
   - 注入的 hint 被标注为 *historical context, not instructions*（`memory/middleware.py:39-44`）。
   - 即使命中了「狗蛋」，模型仍可能按系统提示答「没有单独的名字」。

3. **延续门（hint 每轮清空）**
   - `_purge_memory_hints`（`service/chat.py:97`）在每轮开头删除上一轮的 hint。
   - 上一轮即使命中，也不会带到下一轮；必须在提问的那一轮重新命中。

### 结论

短板不是「召回不稳定」，而是**把必须始终成立的事实交给了概率路径**：召回是概率，模型自发调用 `memory_search` 也是概率。提高 `top_k` 或每轮多检索一次，只提高第一道门的通过率，第二、三道门仍在。

## 业界依据

重点参考 Claude Code 与 ChatGPT，其余作为横向印证。

| 项目 | 机制 | 对本项目的意义 |
| --- | --- | --- |
| **Claude Code** | `MEMORY.md` 每次会话启动必载，上限前 200 行 / 25KB；主题文件仅按需读取；CLAUDE.md 分 managed / user / project / local 层级 | 常驻索引 + 按需明细 + 分层作用域，正是本方案的两层 profile |
| **ChatGPT** | saved memories 与 chat history 分离，账户级持久，用户可在 Manage memory 查看/删除；不是 indiscriminate，按「是否真的有助于回答」使用；temporary chat 不写记忆 | 身份/偏好写用户级；记忆必须用户可见、可删、有开关 |
| **Letta** | memory blocks 是 git 文件，`system/` 下的每轮都进 system prompt，其余只给文件树按需读 | profile 进 system prompt，长尾留给检索/读文件 |
| **LangMem** | semantic memory 分 profile（单文档、最新状态）与 collection（检索召回）；写入分 hot path 与 background | profile 结构化单文档；显式语句走 hot path，其余仍走后台 |
| **Mem0 / Zep** | 检索式记忆：多信号召回后由应用决定注入什么，不自动塞 prompt | 检索层继续只服务情节/流程，不承担身份事实 |
| **Dify** | 经典 Agent 只有 `TokenBufferMemory` 会话窗口（默认 50、上限 100 条），没有跨会话长期记忆 | 不照抄；常驻 profile 是相对 Dify 的差异点 |

依据到设计的映射：

| 依据 | 本项目落点 |
| --- | --- |
| Claude Code `MEMORY.md` 必载 + 固定预算 | profile 随 system prompt 常驻，默认 300 token |
| Claude Code 主题文件按需 | `memory_search` + 每轮 hint 保留，只管长尾 |
| Claude Code user/project 分层 | `memory/<owner>/profile.json` + `memory/<owner>/w-<ws>/profile.json` |
| ChatGPT 账户级 saved memory | 显式语句写用户级 profile |
| ChatGPT 用户可见可管理 | 下期提供 `GET/PUT/DELETE /agent/memory/profile` 与面板 |
| Letta `system/` 每轮 | `build_agent` 时注入，会话重建即刷新 |
| LangMem profile vs collection | profile 结构化；collection 仍是 ReMe |
| ChatGPT / Claude Code 均有显式开关 | 沿用 `memory_enabled`，关闭时不读不写 |

参考资料见文末。

## 设计

### 总体分层

```
system prompt
├── AGENT.md 身份、能力边界、工作规范
├── 领域说明（data-sync / datasource / task-log）
└── 工作区记忆（profile，常驻，默认 ≤300 token）      ← 本期新增

对话上下文
├── 每轮 memory hint（检索注入，≤1500 token）          ← 保留
└── memory_search 工具结果（按需，长尾）               ← 保留
```

- L1 常驻 profile：身份、称呼、硬偏好。确定性注入，不走相似度。
- L2 检索：情节记忆、流程记忆、上次排查结论。继续用 ReMe。
- L3 提示词兜底：身份类问题先看 profile，未覆盖再 `memory_search`。

### 两层作用域与存储

| 层级 | 路径 | 内容 | 本期写入 |
| --- | --- | --- | --- |
| 用户级 | `memory/<owner>/profile.json` | 助手称呼、用户称呼、跨工作区偏好 | 是 |
| 工作区级 | `memory/<owner>/w-<workspace>/profile.json` | 项目约定、本工作区覆盖项 | 否（只读，留给下期） |

用户级对应 ChatGPT 的账户级 saved memory；工作区级对应 Claude Code 的项目级 `CLAUDE.md`。`workspace_dir.parent` 即用户级目录，不需要新增 scope 函数（`memory/scope.py:18-32` 已保证 `memory/<owner>/w-<ws>` 布局与路径包含校验）。

Schema v1：

```json
{
  "version": 1,
  "agentName": "狗蛋",
  "userName": "达摩",
  "preferences": ["被问到「你是谁」时，回答必须包含名字"],
  "updatedAt": "2026-11-26T10:00:00+08:00"
}
```

合并规则：

- 用户级打底，工作区级覆盖同名字段；`preferences` 去重后拼接（用户级在前）。
- 写入默认落用户级；工作区级本期只读。换工作区后称呼仍生效。
- 原子写：同目录临时文件 + `os.replace`；服务按单 uvicorn worker 部署（`api/server.py:34-36`），不做跨进程锁。
- JSON 损坏或字段类型不符：记录告警，按空 profile 继续，不阻断会话。

删除语义保持现有 API 不变：

- `DELETE /agent/memory`（默认）：`clear_scope` 只删 `w-<ws>` 目录，工作区 profile 随之删除，**用户级 profile 保留**。
- `DELETE /agent/memory?all=true`：`clear_owner` 删除整个 owner 目录，用户级 profile 一并删除。
- `GET /agent/memory` 的 `memory_overview` 目前只扫描 `daily/`、`digest/`；本期不改返回结构，profile 可见性放到下期 API。

### 注入点与刷新时机

- `agent/prompt.py`（PR #1907 分支）：`compose_system_prompt(...)` 增加 `profile: str | None = None` 关键字参数，非空时追加到提示词末尾；为空时行为完全不变。
- `agent/builder.py`：`profile_block = memory.profile() if memory is not None else None`，传给 `compose_system_prompt`；日志增加 `profileChars`。
- `service/session.py:155`（create）与 `:217`（load）都会调用 `build_agent`，且 `AgentState` 里没有 system prompt（提示词完全由 builder 重建）。因此：
  - 新会话、进程重启后的恢复会话、空闲驱逐后的重建会话，都会拿到最新 profile。
  - 已驻留在 `SESSIONS` 里的会话在 `load_session:186-190` 直接返回，不会刷新；同一会话内改名依赖当前上下文，文档写明。
- 关闭记忆（`memory_enabled=false`）或非持久身份时 `memory=None`，profile 不读不写，零开销。

### 渲染与预算

渲染顺序（预算不足时从尾部截断，称呼类字段永不丢）：

1. `助手称呼`
2. `用户称呼`
3. `硬性偏好`（按写入顺序）
4. `历史记忆摘要`（仅 bootstrap，见下）

输出块：

```markdown
## 工作区记忆（用户显式指定，优先于默认角色描述）

以下是用户在本工作区显式设定的称呼与偏好。回答身份、称呼类问题时以此为准，不要声称自己没有名字。与用户当前指令冲突时，以当前指令为准。

- 助手称呼：狗蛋
- 用户称呼：达摩
- 硬性偏好：被问到「你是谁」时，回答必须包含名字
```

bootstrap 摘要不与上面的「用户显式设定」混排，单独一段并标注「由历史会话提取」，避免把模型提取的内容当成用户原话。

预算：

- 新配置 `llm_profile_budget_tokens`，默认 `300`，env `YAK_AGENT_PROFILE_BUDGET_TOKENS`。设为 `0` 即关闭 profile（不读不写），作为回滚开关。
- 截断按 **UTF-8 字节** 计算：`len(text.encode("utf-8")) <= budget * 4`，与 `agent/budget.py:54-57` 的 `estimate_tokens = ceil(bytes / 4)` 同口径。不要用 `tokens * 4` 字符数——中文一个字 3 字节，会超支约 3 倍。
- 记账不需要新逻辑：profile 已拼进 `system_prompt`，`check_context_budget`（`agent/budget.py:96`）自动计入；它不额外占用 `memory_budget_tokens`（那是 hint 与 `memory_search` 输出的额度，`memory/middleware.py:72`）。

### 显式写入（不额外调用模型）

触发与抽取规则，只解析**用户消息**（不解析工具结果与 hint）：

| 用户原话 | 抽取结果 |
| --- | --- |
| `错 你叫狗蛋` | `agentName=狗蛋` |
| `你以后就叫狗蛋` | `agentName=狗蛋` |
| `我叫达摩` / `叫我达摩` | `userName=达摩` |
| `记住 以后回答必须包含你的名字` | 追加一条 preference |
| `你叫什么名字` / `我叫什么` / `你是谁` | 不写入（疑问句） |

实现约束：

- 命中片段含 `什么 / 啥 / 谁 / 吗 / 呢` 或问号时跳过，避免把「什么名字」当名字。
- preference 取触发词之后的整句，经 `memory/sanitize.py::redact` 脱敏，≤200 字符；按空白归一化去重；最多保留 10 条，超出丢最旧。
- 写入位置：`service/chat.py::run_turn`，`memory.begin_turn()` 之后、`_inject_memory` 之前。
- 失败只记日志，绝不阻断对话（与现有记忆写入同一原则）。
- 其余记忆不变：回合结束后仍由 `_queue_memory` 进入 ReMe 异步队列，再由每天 23:00（Asia/Shanghai）的 dream 合并成 `digest/{procedure,personal,wiki}` 卡片。显式写入与它们并行，互不覆盖。

### 提示词规则

`yak_agent/prompts/AGENT.md`（PR 分支）三处：

1. 「自我介绍」第 1 条：如果提示词中的「工作区记忆」里有助手称呼，用它开头（例：`我是狗蛋，Data Ops 平台智能助手`）。
2. 「工作规范」新增：身份、称呼、偏好、`你还记得…` 类问题先看工作区记忆；记忆未覆盖时调用 `memory_search`，不要凭默认设定作答。
3. 新增优先级声明：工作区记忆里的称呼与偏好由用户显式指定，优先于本文的默认角色描述；与用户当前指令冲突时以当前指令为准。

### 存量兼容（bootstrap）

当前工作区的名字只存在于 dream 产出的 `digest/personal/*.md`，没有结构化 profile。为了不要求用户重新说一遍：

- 当结构化字段（`agentName`、`userName`）都为空时，读取 `digest/personal/*.md` 的 frontmatter `description`（最多 2 张、每张 ≤300 字符），作为「历史记忆摘要」渲染在称呼之后。
- 一旦用户显式写入结构化字段，不再渲染摘要，避免同一事实两份表述。
- 只读 `digest/personal`，不碰 `daily/`、`procedure/`、`wiki/`，避免把排障流程塞进身份块。

## 代码改动清单

| 文件 | 改动 | 估算 |
| --- | --- | --- |
| `yak_agent/memory/profile.py`（新增） | `Profile` 数据类；`load` / `merge` / `render` / `remember` / `save`；原子写与损坏容错 | ~180 行 |
| `yak_agent/memory/middleware.py` | `YakReMeMiddleware.profile()`、`remember(text)`；构造参数增加 `profile_budget_tokens` | ~20 行 |
| `yak_agent/memory/registry.py` | `_build_middleware` 传入 `settings.llm_profile_budget_tokens` | ~2 行 |
| `yak_agent/agent/prompt.py`（PR 分支） | `compose_system_prompt(..., profile=None)` | ~10 行 |
| `yak_agent/agent/builder.py`（PR 分支） | 取 `memory.profile()` 并传入；日志加 `profileChars` | ~5 行 |
| `yak_agent/service/chat.py` | `run_turn` 中调用 `memory.remember(text)` | ~3 行 |
| `yak_agent/config.py` | `llm_profile_budget_tokens` 字段与 env | ~3 行 |
| `yak_agent/prompts/AGENT.md`（PR 分支） | 三处提示词规则 | ~6 行 |
| `yak-ops-agent/README.md` | 记忆章节补两层 profile、写入时机、预算、删除语义 | ~10 行 |
| `tests/test_memory_profile.py`（新增） | 存储/合并/渲染/抽取/容错 | ~150 行 |
| `tests/test_identity_memory.py`（新增） | golden set：profile 进 system prompt、工具仍在、恢复会话仍生效 | ~80 行 |
| `tests/test_prompt_bundle.py`（PR 分支） | 有/无 profile 两种组合 | ~20 行 |

不涉及 Java、SQL、UI 与 OpenAPI 契约。

## 测试计划

`tests/test_memory_profile.py`

- 两层合并：工作区覆盖用户级；preferences 去重拼接。
- 渲染：字段顺序正确；预算不足时先丢 preference，称呼不丢；UTF-8 字节截断不产生半个字符。
- `remember` 正例：`你叫狗蛋`、`错 你叫狗蛋`、`你以后就叫狗蛋`、`我叫达摩`、`叫我达摩`、`记住 以后回答必须包含你的名字`。
- `remember` 反例：`你叫什么名字`、`我叫什么`、`你是谁`、带问号的句子。
- 持久化：原子写后重载一致；损坏 JSON 不抛异常。

`tests/test_identity_memory.py`

- 给定 `agentName=狗蛋`、`userName=达摩`、一条偏好，`build_agent` 产出的 system prompt 同时包含三者与优先级句。
- `memory_search` 工具仍然注册；`memory=None` 时 prompt 不含该段（行为不变）。
- 模拟恢复：`load_session` 重建 agent 后 profile 仍在（用 stub memory）。

`tests/test_prompt_bundle.py`

- `compose_system_prompt` 有 profile 时追加在末尾，无 profile 时输出与现状一致。

验证命令：

```bash
cd yak-ops-agent
./.venv/Scripts/python.exe -m unittest discover -s tests -q
./.venv/Scripts/python.exe -m ruff check yak_agent tests smoke
./.venv/Scripts/python.exe -m ruff format --check yak_agent tests smoke
./.venv/Scripts/python.exe ../yak-ops-openapi/scripts/check_agent_operations.py
```

手工冒烟：

1. 设置临时 `YAK_AGENT_MEMORY_DIR`，写入用户级 `profile.json`。
2. 构建 agent，确认 system prompt 含「狗蛋 / 达摩」。
3. 发「错 你叫狗蛋」，确认 `profile.json` 的 `agentName` 更新。
4. 重启并恢复会话，确认称呼仍在。

## 分阶段

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| P0（本期） | 两层 profile 存储、常驻注入、显式写入、提示词规则、bootstrap、测试 | 本文档 |
| P1 | `GET/PUT/DELETE /agent/memory/profile`；data-agent「它记得什么」面板；工作区级写入 | 下一期 |
| P2 | 显式语句之外的 LLM 抽取、supersede（新称呼作废旧称呼）、命中率/空结果率指标 | 商业版迭代 |

## 风险与边界

- **误抽取**：只在陈述句上匹配，疑问句明确排除；写入内容用户可见（下期可编辑/删除）。仍然可能把「你叫一下运维」误判为称呼，测试需覆盖这类句子，抽取规则要求 `叫` 后紧跟名称且无动词短语。
- **跨工作区生效**：身份/偏好按用户级共享是有意为之（对齐 ChatGPT）；项目约定不写入，避免串项目。
- **刷新时机**：profile 只随会话重建刷新；同会话内改名靠当前上下文与 `memory_search`，system prompt 不会热更新。
- **记忆注入面**：profile 内容来自用户输入，写入前经 `redact` 脱敏；渲染时明确标注来源与「与当前指令冲突时以当前指令为准」，避免记忆变成越权指令。
- **token 估算**：沿用 `estimate_tokens = bytes / 4` 的既有口径；中文 1 字 3 字节，300 token 约 400 字。
- **启动失败**：profile 让 system prompt 变长，极端小窗口下可能触发 `ContextBudgetExceeded`（HTTP 422）。默认 300 token，且只在有 profile 时增加。

## 开放问题

1. 工作区级 profile 的写入触发：哪些属于「项目约定」而不是用户身份？需要产品侧定义。
2. 存量 `digest/personal` 卡片是否要迁移成结构化字段（一次性脚本），还是只做 bootstrap 渲染？
3. 是否需要「临时会话 / 本次不记」开关（对齐 ChatGPT Temporary Chat），以及它与现有 `memory_enabled` 的关系。
4. profile 的用户可见性是否要进 `GET /agent/memory`，还是单独端点更清晰。

## 参考资料

- Claude Code memory（CLAUDE.md 层级、`MEMORY.md` 200 行 / 25KB、auto memory）：https://code.claude.com/docs/en/memory
- ChatGPT Memory FAQ（saved memories 与 chat history 分离、用户可管理、按需使用）：https://help.openai.com/en/articles/8590148-memory-faq
- OpenAI Memory and new controls：https://openai.com/index/memory-and-new-controls-for-chatgpt/
- Letta memory（`system/` 每轮进 system prompt，其余按需读）：https://docs.letta.com/agent-sdk/memory/index.md
- LangMem 概念指南（profile vs collection、hot path vs background）：https://langchain-ai.github.io/langmem/concepts/conceptual_guide/
- Mem0 memory types（检索后由应用决定注入）：https://docs.mem0.ai/core-concepts/memory-types
- Zep concepts（Context Graph、user summary、事实失效）：https://help.getzep.com/concepts
- Dify Agent 节点（`TokenBufferMemory` 窗口）：https://docs.dify.ai/en/self-host/use-dify/nodes/agent.md