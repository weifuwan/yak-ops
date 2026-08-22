# Realtime Sync Domain Guardrails

> Scope: `yak-ops-business-sync-realtime` and every change that creates, edits, publishes, executes, observes, serializes, or persists realtime-sync tasks.
>
> This file is the **module-level mandatory domain contract**. Detailed design/history lives under `docs/realtime-sync/domain/`.

---

## 0. Mandatory Domain Impact Analysis

**Do not start coding before performing a Domain Impact Analysis.**

Every realtime-sync change MUST answer:

```text
Domain Impact Analysis
- Bounded context:
- Aggregate(s): RealtimeSyncTask / DefinitionVersion / SyncExecution / adjacent context
- SyncDefinition area: Endpoint / Route / Selector / Target / ReplayKey / SyncPolicy / ExecutionPolicy / none
- Invariant/lifecycle impact:
- Layer: Domain / Application / Infrastructure / Interface
- Current mapping/gap:
- Safety properties to preserve:
- Domain Gap: yes/no
```

If the requirement cannot be mapped to the accepted aggregates, `SyncDefinition` sub-model, lifecycle, or an explicit adjacent context:

```text
Domain Gap: yes
```

**STOP. Review/extend the domain model before implementation.**

Do not bypass a Domain Gap with a temporary field, boolean, enum, `syncType`, `sceneType`, `*Spec`, `*Task`, or `*Service`.

---

# 1. Core coordinate system

Realtime Sync has three aggregate roots:

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

Canonical configuration:

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
│   ├── SourceSelector
│   ├── SinkTarget
│   └── ReplayKey
├── SyncPolicy
└── ExecutionPolicy
```

Lifecycle:

```text
RealtimeSyncTask.currentDraft
        │ publish
        ▼
DefinitionVersion (immutable)
        │ start
        ▼
SyncExecution
```

Meanings MUST remain separate:

```text
RealtimeSyncTask  = long-lived identity + current Draft + PublishedDefinitionRef
SyncDefinition    = what/how to synchronize
DefinitionVersion = immutable published fact
SyncExecution     = one actual run of one immutable published version
```

**Task ≠ Definition ≠ Version ≠ Execution.**

---

# 2. One definition truth only

`SyncDefinition` is the single domain source of truth.

These are adapters/projections only:

```text
Wizard
Yak Realtime YAML
HTTP DTO / VO
DB JSON compatibility representation
Flink CDC Pipeline YAML
```

MUST NOT create a second editable truth such as:

```text
WizardSpec
YamlSpec
FlinkSpec
MysqlSyncSpec
PostgresSyncSpec
KafkaSyncDefinition
```

Flink Pipeline YAML remains a transient compiled artifact.

---

# 3. Version rules

A Published DefinitionVersion is immutable.

```text
Publish -> create/reuse immutable DefinitionVersion
Execution -> reference explicit DefinitionVersionId
```

MUST NOT:

```text
publish by only toggling a mutable Draft marker
modify Published Version in place
make an Execution read current Draft
replace historical Published content with current Draft
```

Authoritative identity:

```text
Task.publishedDefinitionVersionId
SyncExecution.definitionVersionId
```

Legacy compatibility only:

```text
definition_version = DraftRevision
published_version  = published DraftRevision marker
```

Do not use those legacy integers as immutable version identity.

---

# 4. Draft, Published and active Execution may coexist

Valid state:

```text
Execution E100(V3) RUNNING
+
Draft r4
+
Published V4
```

Active/uncertain Execution MUST NOT block Save Draft or Publish.

Save/Publish MUST NOT mutate the active Execution's:

```text
DefinitionVersionRef
RuntimeEnvironmentSnapshot
DesiredState
ObservedState
EngineExecutionRef
```

Publish is **not** hot reload.

---

# 5. SyncExecution owns runtime lifecycle

Every actual run is a new Execution:

```text
Start                 -> new Execution
RestartExecution      -> new Execution, same DefinitionVersion
ApplyPublishedVersion -> new Execution, captured Published DefinitionVersion
```

For one Execution:

```text
STOPPED = terminal
FAILED  = terminal
```

Terminal Execution MUST NOT be resurrected.

v1 allows at most one Active / Uncertain Execution per Task:

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

---

# 6. DesiredState and ObservedState are different facts

```text
DesiredState  = control-plane/user intent
ObservedState = latest known runtime fact
```

Rules:

- Stop persists `desired = STOPPED` before engine convergence.
- Reconcile updates observed/runtime facts, not user intent merely to match the engine.
- `UNKNOWN` means insufficient knowledge, not failure.
- `CONFLICT` means ambiguous external identity; do not guess.
- uncertain submission must not create a second Execution.

Runtime lifecycle truth is **SyncExecution only**.

Physical Task columns:

```text
desired_state
observed_state
last_error
```

are inert compatibility storage.

MUST NOT reintroduce:

```text
Execution lifecycle dual-write to Task columns
runtime fallback to Task columns
desiredJobs
hasOtherDesiredRunning
markStarting
```

Read model:

```text
latest Execution exists -> state/error from Execution
no Execution            -> STOPPED / STOPPED / null
```

Task row locking may remain a cross-instance command mutex; lock location does not imply lifecycle ownership.

---

# 7. RestartExecution and ApplyPublishedVersion stay distinct

## RestartExecution

```text
E100(V3) -> stop -> E101(V3)
```

Target is `currentExecution.definitionVersionId`, never current PublishedRef.

## ApplyPublishedVersion

```text
E100(V3), Published=V4 -> explicit apply -> E101(V4)
```

Target is captured at command start and remains pinned. A later Publish V5 does not change an already-running Apply V4 command.

## Preflight before Stop

Both commands MUST validate/resolve/compile the exact target version before stopping a healthy Execution.

After preflight, DB-lock replacement reservation must re-check the same stable RUNNING Execution before STOPPING.

HTTP v1 `/restart` may remain only as an external alias for `restartExecution()`.

---

# 8. Route / Selector / Policy composition comes before scenario enums

Prefer:

```text
1 Exact Route           -> UI single-table
N Exact Routes          -> UI multi-table
Pattern Selector        -> pattern matching
future DatabaseSelector -> whole-database capability
```

MUST NOT add without explicit domain review:

```text
syncType
sceneType
SINGLE_TABLE
MULTI_TABLE
WHOLE_DATABASE
SHARDING
MysqlRealtimeTask
KafkaRealtimeTask
```

New scenarios first extend Selector / Route / Target / Policy. If they cannot, mark `Domain Gap`.

---

# 9. Replay safety is an invariant

Every v1 route has a non-empty `ReplayKey` with unique fields.

Contextual preflight validates current source uniqueness semantics and ambiguous routing.

Core Domain MUST NOT model `strictReplaySafety=false`.

---

# 10. Runtime Environment / DataSource are adjacent contexts

Definition stores runtime reference; Execution stores runtime snapshot.

Core Domain MUST NOT contain connection/runtime internals such as:

```text
flinkHome
flinkCdcHome
flinkRestUrl
sshHost
sshUser
identityFile
javaHome
jdbcUrl
host
port
username
password
connectionJson
```

Credentials exist only at submission boundary and retain short lifetime / zeroization.

---

# 11. Flink is Infrastructure

Core Domain does not model Flink Job as Task, Flink YAML as Definition, Flink commands, SSH mode, or connector JAR paths.

Boundary:

```text
SyncDefinition / DefinitionVersion
        ↓ Application/compiler
Compiled engine artifact
        ↓
Flink CDC adapter
```

`EngineExecutionRef` is engine-neutral; Flink JobId is adapter-specific external identity.

---

# 12. Adapter-private tuning stays outside Core Domain

If `ExecutionPolicy` accepts a setting, the engine must apply it or explicitly reject it during preflight.

MUST NOT silently persist a no-op policy.

Known gap: checkpoint/restart settings are not yet fully applied by the current runtime path.

---

# 13. Validation has three layers

```text
Intrinsic Domain Validation
Contextual Preflight
Adapter / Artifact Validation
```

External connectivity failure does not mutate a historical SyncDefinition into an intrinsically invalid object.

---

# 14. Digest semantics remain distinct

```text
DefinitionDigest
= canonical SyncDefinition + RuntimeEnvironmentRef

sourceConfigDigest
= exact mutable-Draft / publish-CAS compatibility digest

artifactDigest / ExecutionArtifactDigest
= compiled runtime artifact digest for one Execution
```

Physical `config_digest` columns may remain, but new Domain/Application code uses semantic names.

---

# 15. Historical evidence is immutable by default

DefinitionVersion and SyncExecution are historical facts.

Do not casually hard-delete published versions, execution history, or audit events.

Current hard delete is a known gap; Archive/Tombstone requires its own domain decision.

---

# 16. Safety protection list

Preserve unless an explicit replacement is proven equivalent or safer:

```text
Idempotency-Key
unique start persistence
DB command serialization
start reservation before external submit
same-key race recovery
prepared version re-check
stop-during-start
uncertain submission -> UNKNOWN
runtime identity persistence/recovery
ambiguous match -> CONFLICT
RuntimeEnvironmentSnapshot
replacement-stop reservation
submission-scoped credentials + zeroization
secret-free persistence
log redaction
multi-instance reconcile lease
```

---

# 17. Stage 6 migration is complete

```text
Wave 0  Core VOs + compatibility mapper                      ✅
Wave 1  Immutable DefinitionVersion                          ✅
Wave 2  Start by Published DefinitionVersion                 ✅
Wave 3  SyncExecution lifecycle ownership                    ✅
Wave 4  Active Execution allows Draft Save / Publish         ✅
Wave 5  RestartExecution / ApplyPublishedVersion split       ✅
Wave 6  Legacy runtime projection / contract cleanup         ✅
```

Physical/v1 compatibility names may remain temporarily (`job_definition`, `job_deployment`, `definition_version`, `published_version`, `config_digest`, `status`, `latestDeployment`, HTTP `/restart`). They are not domain truth.

Physical contract removal remains incremental:

```text
expand -> switch readers/writers -> verify -> contract
```

---

# 18. Known gaps

Keep separate until explicitly designed:

```text
Audit-safe Archive / Tombstone
ExecutionPolicy checkpoint/restart runtime application
Flink FINISHED normal completion / snapshot-only
legacy failure-rate mapping
Read-model package hygiene
Compute Environment physical context cleanup
API v2 / physical schema naming cleanup
```

---

# 19. Stage 7 automated enforcement

The domain contract is machine checked.

## Mandatory: Static Domain Contract

```bash
python3 tools/realtime_domain_guardrails.py
```

It checks Core purity, anti-pattern naming, Execution-only runtime truth, immutable version identity, Restart/Apply separation, Start-by-Published, digest semantics, required docs, and realtime PR body contract.

## Mandatory: framework-free Core Smoke

GitHub Actions compiles Core Domain with JDK 21 and **no Spring/Maven classpath**, then runs:

```text
tools/realtime-domain-smoke/RealtimeDomainSmoke.java
```

`SyncExecutionStateMachine` is pure Java; Spring construction belongs in `RealtimeSyncConfiguration`.

## Conditional: Maven/JUnit deep regression

The existing deeper JUnit suite depends on private repository:

```text
weifuwan/yak-framework
```

A repository-scoped `GITHUB_TOKEN` for yak-ops cannot read that private sibling repo.

Workflow Job `Backend regression hook` therefore runs full Maven/JUnit only when repository secret:

```text
YAK_FRAMEWORK_TOKEN
```

is configured with read access to yak-framework.

Without that secret the Job emits an explicit notice and skips Maven/JUnit steps.

**A green Backend regression hook without `YAK_FRAMEWORK_TOKEN` does not mean JUnit passed.**

The mandatory required checks remain Static Domain Contract + Framework-free Core Smoke + PR contract.

## Mandatory: PR contract

Realtime PR bodies contain:

```text
Domain Impact Analysis
Domain Gap
Domain Compliance Report
```

Workflow listens to PR `edited` so description fixes are re-evaluated.

Files:

```text
.github/workflows/realtime-sync-domain-guardrails.yml
.github/PULL_REQUEST_TEMPLATE/realtime-sync-domain.md
docs/realtime-sync/domain/07-automated-domain-guardrails.md
```

## Guard modification rule

Do not disable a guard merely because a feature fails it.

If an accepted domain rule changes, update:

```text
DOMAIN.md
relevant design docs
replacement automated guard
```

together in the same reviewed change.

---

# 20. AI implementation/completion contract

Before code:

```text
Domain Impact Analysis
- Bounded context
- Aggregate(s)
- SyncDefinition area
- Invariant/lifecycle impact
- Layer
- Current mapping/gap
- Safety properties
- Domain Gap: yes/no
```

After code:

```text
Domain Compliance Report
- Domain rule implemented
- Aggregate(s) affected
- Invariant/lifecycle changes
- Legacy compatibility retained
- Safety properties preserved
- DB/API migration mode
- Tests/guardrails added or updated
- Known gaps remaining
```

A realtime PR should not be considered complete until mandatory Stage 7 guardrails pass.

---

# 21. Review rejection triggers

Return for domain review if a PR introduces without explicit approval:

```text
second editable Spec/Definition truth
syncType / sceneType scenario modeling
Flink/SSH/JDBC/Spring/persistence concerns in Core Domain
Execution reading current Draft
Published Version mutation
Restart that may silently upgrade
Apply target drift after command start
stop-before-target-preflight
second active Execution under UNKNOWN/CONFLICT
UNKNOWN -> FAILED only to permit retry
Task runtime truth reintroduction
Deployment.status as lifecycle truth
legacy revision as immutable Version identity
ambiguous new generic configDigest
hard-delete history presented as harmless cleanup
adapter-private tuning in SyncDefinition
silent ExecutionPolicy no-op
Big-Bang schema/API rename/drop
disabling a Stage 7 guard instead of resolving the violated rule
```

---

# 22. Source documents / authority order

```text
docs/realtime-sync/domain/01-domain-boundary-and-language.md
docs/realtime-sync/domain/02-core-domain-model.md
docs/realtime-sync/domain/03-invariants-and-lifecycle.md
docs/realtime-sync/domain/04-current-code-mapping.md       # historical snapshot
docs/realtime-sync/domain/05-ai-domain-rules.md
docs/realtime-sync/domain/06-stage6-migration-completion.md # current implementation facts
docs/realtime-sync/domain/07-automated-domain-guardrails.md  # executable enforcement
```

Authority order:

```text
1. Newest approved user/domain decision
2. DOMAIN.md current hard rules
3. Stage 6 current implementation facts
4. accepted detailed domain design docs
5. Stage 7 automated checks + invariant tests
6. legacy names / physical schema details
```
