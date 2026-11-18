# FYOIP-16: Agent Programmatic Tool Calling — sandboxed code execution for log root-cause analysis

Status: Draft

Discussion: N/A (draft for review)

Decision: N/A

Implementation: N/A until accepted

Release: N/A

Related FLIPs: N/A

## Motivation

Phase 1 of the agent integration is closed and verified on form 1: the agent calls REST-backed tools one call at a time inside a ReAct loop, results are truncated and enter the context, and the model reasons over them. The verification record is a real-model drill on the data-sync domain — read questions 5/5, cold-start stability 10/10, and a full write lifecycle (create → publish → unpublish → delete) with every write passing the HITL confirmation pause.

That same drill exposed a structural limit of form 1. Asked "why did the most recent sync task fail", the model answered with the outermost frame only — `org.apache.flink.runtime.JobException: Recovery is suppressed by NoRestartBackoffTimeStrategy` — and stopped. That line is Flink's wrapper, not the cause; the actual root cause lives further up the `Caused by` chain and earlier in the log, which the model never saw, because every tool result is cut twice on its way into the context (16 KB per payload at the client, ~1,000 tokens per result by `ContextConfig.tool_result_limit`). A single Flink task log routinely reaches several MB; the model's view of it is the last screenful. The same round took 203 seconds, dominated by long-answer generation over an inflated context and by re-prefilling the whole history on every ReAct iteration.

The distinction that shapes this proposal: reading a status or listing instances does not need code execution — the data is small and the answer is direct. Log root-cause analysis is the opposite shape: large free-text payloads, retrieval-heavy investigation (find the first `Caused by`, correlate startup logs with run logs), and a low-density conclusion (a few lines out of thousands). That is exactly the workload programmatic tool calling (PTC) exists for: the model writes a script, the script calls tools in a loop inside a sandbox, data stays inside the script's scope, and only the conclusion returns to the context.

| Question | Form 1 (today) | With PTC |
| --- | --- | --- |
| "Is instance 14 running?" | Direct tool call; adequate | Unnecessary overhead |
| "Why did instance 14 fail?" | Sees ~1,000 tokens of log tail; answers with the outermost frame | Script downloads the full log file in one call, greps the `Caused by` chain, returns the root cause and its first occurrence |
| "Compare durations across all 15 instances" | Model pulls every page into context and computes by hand | Script aggregates; model reads one table |

AgentScope already ships the sandbox layer this needs (`agentscope.workspace` with Docker, Bubblewrap, Daytona and AppleContainer backends), so the proposal reuses it rather than building a custom script sandbox; the earlier idea of a hand-rolled JS sandbox is withdrawn on that basis.

## Public Interfaces

### New agent tool

One new tool is exposed to the model; the REST surface of the platform is untouched.

| Item | Contract |
| --- | --- |
| Tool | `run_analysis_script(code: str, timeout_seconds: int = 60)` |
| Environment | Python 3.11 inside a `DockerWorkspace` container, one container per execution |
| Callable surface | A generated read-only SDK (`yak_agent_sdk`), injected into the script's import path. Functions mirror the allowlisted read operations (list/get/preview/overview/instances/log + the full-log download endpoint, which returns the entire log file in one call) with the same signatures as the form-1 tools. Credentials are injected by the SDK from the server process; scripts never see tokens or passwords. |
| Return | `{stdout, stderr, result}` where `result` is the value of a script-level `return`/`print` of the final expression, truncated to 16 KB. Data processed inside the script does not enter the context. |
| Bounds | Wall-clock timeout (default 60 s, hard cap 300 s), memory and CPU limits from the container runtime, network egress restricted to the yak-ops API base. |
| Audit | Every execution is appended to the same JSONL audit trail: the script itself (truncated), exit status, duration, and the tool calls the SDK made on behalf of the script. |

### SDK generation

The SDK is generated from `yak_agent/operations.yaml` — the same registry that materialises the form-1 tools — so the two shapes cannot drift:

- one Python function per `readonly` operation (`list_sync_instances(status=None, task_code=None, page_no=1, page_size=20)`), returning parsed `data` dicts instead of strings;
- `write` and `destructive` operations are **not** exported to the SDK in this release (see Proposed Changes 5);
- pagination is explicit: the SDK returns pages and exposes a `page_all()` helper so scripts decide how much they pull, with a hard cap of 10,000 records per execution.

### Session and audit extensions

- `run_analysis_script` executions are recorded per call in the existing JSONL audit trail (script body, exit status, duration, SDK call count);
- the SSE stream gains one event type, `script_result`, so a frontend can show what the sandbox produced without dumping the script into the chat.

## Proposed Changes

### 1. The sandbox is a pluggable backend: Docker in production, a restricted subprocess locally

The execution environment is a backend behind one interface, with two implementations at different isolation levels:

- **DockerWorkspace** (AgentScope built-in, production shape): a persistent `python:3.11-slim` container as the execution environment for model-written scripts — private filesystem, Docker-enforced CPU/memory/timeout bounds, egress restricted to the yak-ops API base. The container persists across turns so intermediate files survive follow-up rounds.
- **LocalRestrictedExecutor** (development / Docker-less hosts): the script runs as a subprocess of the server in a dedicated working directory with a wall-clock timeout, truncated stdout, the generated SDK as its only I/O surface, and the same audit record. It provides **no OS-level network or filesystem isolation**; its trust model is "the script was generated by our own model, audited on every run" — it guards against mistakes, not malice. This level is for verification and intranet dev hosts; production deployments run the Docker backend.

Both receive the same injected surface: the generated read-only SDK (`yak_agent_sdk`, every request pinned to the configured API base) and httpx, via `extra_pip` (Docker) or the server's own environment (local). If neither backend is available, `run_analysis_script` is not registered and the agent keeps form 1 — it is an enhancement, not a dependency (see Compatibility).

### 2. The SDK is generated from the operation registry

`operations.yaml` already carries method, path, parameter routing and risk for every operation. A generator emits `yak_agent_sdk/functions.py` at build time from the `readonly` subset. Hand-written SDK code is limited to the credential injection shim and the pagination helper. Regeneration is part of the same workflow that already regenerates the contract, so an allowlist change propagates to both tool shapes with one command.

### 3. Data stays in the script; only conclusions enter the context

The script's stdout is the only channel back. This proposal does not stream intermediate tool results into the conversation: the model writes the script, reads the conclusion, and answers. That is the entire point — the 203-second round shrinks because a multi-thousand-token log never becomes a multi-thousand-token context segment, and the follow-up answer gets shorter because the model reasons over a conclusion instead of a screenful of raw text.

### 4. Read-only in this release; writes stay in form 1

The SDK exports only `readonly` operations. Write and destructive operations remain form-1 tools behind the HITL confirmation pause. Mixing writes into sandboxed scripts would need a code-level confirmation story (static analysis of the script against the operation registry, surfacing the write calls it will make before execution) — valuable, but a separate decision, and this proposal deliberately does not carry it. The form-1 write path is already verified end to end and stays the only way to mutate anything.

### 5. Network and resource bounds are enforced by the runtime, not by the prompt

The container's egress is restricted to the yak-ops API base; the SDK holds the only credentials and they are never written into the script's filesystem; timeout, memory and CPU limits come from the Docker runtime. The model cannot promise safety, so nothing relies on it doing so.

### 6. Form 1 tools remain, unchanged

`run_analysis_script` is additive. The 14 form-1 tools keep their schemas, permissions and audit records; sessions that never call the new tool behave exactly as today. The agent decides per question which shape fits; the system prompt states the heuristic (small lookups → direct tools; log analysis, cross-instance aggregation → script).

### 7. What an implementation must not add

- no general filesystem access from scripts (the SDK is the only I/O surface);
- no write or destructive operations in the SDK;
- no unrestricted egress;
- no second audit format;
- no change to the platform REST surface or to `yak-ops-openapi`.

## Compatibility, Deprecation, and Migration Plan

- Purely additive: no existing endpoint, tool schema, audit field or session state changes.
- `operations.yaml` gains nothing itself; the SDK generator reads it as-is. A new `ptc: readonly-only` marker is documentation, not a schema change.
- Backend selection is by availability: DockerWorkspace when Docker is present, LocalRestrictedExecutor otherwise (explicitly documented as the lower-isolation tier). If neither is available, `run_analysis_script` is not registered and the agent is behaviourally identical to today.
- Rollback is deleting the tool registration.

## Test Plan

The acceptance criterion is the real incident that motivated this proposal:

1. **Root cause depth (gold standard)**: for data-sync instance 14, a PTC round must surface the `Caused by` chain's root cause — not stop at `Recovery is suppressed by NoRestartBackoffTimeStrategy` — and report where in the log it first appears. **Met (2026-11-18)**: root cause identified as MySQL error 1170 (BLOB/TEXT primary key without length) at log line 957; round took 150.2 s vs the 203.2 s form-1 baseline.
2. **Full-pagination correctness**: a script that downloads the whole log must see the same line count as the file on disk (verified: 969 lines / 70 KB for instance 14), and the 10,000-record page cap on paginated SDK calls must be enforced.
3. **Bounds**: a script with `while True` hits the wall-clock timeout and returns a timeout result; a script attempting egress to a non-yak-ops host fails at the network layer.
4. **Audit**: every execution produces exactly one audit record including the SDK call list; `parameters_json`-style redaction still applies.
5. **No-regression**: the existing read smoke (5 questions, 10× stability) passes unchanged with the new tool registered.
6. **Performance**: the same "why did the most recent task fail" question completes in a shorter wall-clock round than the 203-second form-1 baseline, with a smaller context growth.

## Open Questions

1. **Log source coverage**: `/log/detail` pages a single task-instance log. Flink's full failure chain may live in JobManager/TaskManager logs. Whether those are reachable through existing endpoints decides the ceiling of log analysis; to be verified before implementation (they are visible on the node filesystem as `logs/<date>/<jobId>/...`, but no API surfaces that path yet).
2. **Docker availability** in target intranet deployments (Windows dev machine vs Linux servers) — determines whether PTC ships enabled or behind a flag.
3. ~~Code quality of the target model~~ **answered (2026-11-18)**: deepseek-flash wrote a 6-round self-correcting analysis script (fixed its own response-shape and pagination bugs, isolated a 250-line DDL dump that masked the tail) and located the true root cause at log line 957 — a MySQL error 1170 (BLOB/TEXT column as primary key without length) in the DDL. The heuristic in Proposed Changes 6 is trusted for this domain.

## Rejected Alternatives

1. **Add a log-analysis endpoint to the platform controllers** — puts agent-specific text processing into upstream-born Java code; the merge-surface cost is the same one that moved contract text into `yak-ops-openapi`. Rejected.
2. **A fixed server-side "log digest" API** — one prebuilt analysis (grep ERROR, take the tail) hard-codes a strategy the model cannot adapt, and every new analysis need becomes another endpoint. Rejected in favour of a general script surface.
3. **Client-side smart log filtering in the form-1 tool** (keep ERROR/`Caused by` lines, drop the rest) — kept as a **transitional improvement** independent of this proposal, but rejected as the end state: it is still heuristic truncation, and the model cannot choose its own retrieval strategy.
4. **Custom script sandbox (JS/Nashorn or a hand-rolled Python jail)** — `agentscope.workspace` already provides hardened container backends; building a fourth one buys nothing. Rejected.
5. **Do nothing** — form 1 stays within its structural limit: the model's view of a multi-MB log is its last screenful, and root-cause questions keep answering with the outermost frame. Rejected, because the drill in phase 1 already produced a wrong-depth answer in production-like use.
