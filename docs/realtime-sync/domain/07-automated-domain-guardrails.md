# Stage 7 — Automated Realtime Sync Domain Guardrails

> Status: implemented.
>
> Goal: turn the accepted Realtime Sync domain model and Stage 6 migration result into executable checks that resist architectural drift.

---

## 1. Why Stage 7 exists

Stages 1～6 established and implemented:

```text
RealtimeSyncTask
      │ publish
      ▼
DefinitionVersion (immutable)
      │ start / restart / apply
      ▼
SyncExecution
```

Stage 7 does **not** add a new aggregate or change runtime semantics.

It converts accepted rules into:

```text
Domain rules
   ↓
Static checks + framework-free smoke + regression hook + PR contract
   ↓
CI feedback before merge
```

---

## 2. Four enforcement layers

```text
Layer A  Static Domain Contract            mandatory
Layer B  Framework-free Core Domain Smoke mandatory
Layer C  Maven/JUnit Deep Regression Hook conditional on private dependency token
Layer D  Pull Request Domain Contract      mandatory for realtime PRs
```

The important design choice is that **Layer A/B do not depend on Maven, Node, DB, Flink, SSH, or private repositories**.

Therefore domain governance remains executable even when the broader build environment is unavailable.

---

# 3. Layer A — zero-dependency Static Domain Contract

Entry point:

```text
tools/realtime_domain_guardrails.py
```

Local command:

```bash
python3 tools/realtime_domain_guardrails.py
```

Pull-request workflow command:

```bash
python3 tools/realtime_domain_guardrails.py --event "$GITHUB_EVENT_PATH"
```

The script uses Python standard library only.

## 3.1 Core Domain purity

Current executable Core kernel:

```text
RealtimeJobState.java
SyncDefinition.java
RuntimeEnvironmentRef.java
DefinitionDigest.java
SyncDefinitionDigestCalculator.java
DefinitionVersion.java
SyncExecution.java
SyncExecutionStateMachine.java
```

These files may import only:

```text
java.*
io.yak.ops.business.sync.realtime.domain.*
```

The guard rejects Core dependencies on:

```text
Spring
Jackson
MyBatis / MyBatis-Plus
Controller
Service
Repository
DAO
Engine / Flink adapter
persistence frameworks
```

It also rejects infrastructure identifiers in Core such as:

```text
pipelineYaml
flinkHome
flinkCdcHome
flinkRestUrl
sshHost
sshUser
identityFile
jdbcUrl
password
```

## 3.2 Scenario/type explosion prevention

Inside realtime Domain, the guard blocks suspicious second-truth names such as:

```text
Wizard*Spec
Yaml*Spec
Flink*Spec
Mysql*Definition
Kafka*Definition
*SceneType*
*SyncType*
```

and identifiers:

```text
sceneType
syncType
```

A legitimate new scenario that cannot fit Selector / Route / Target / Policy must first be treated as a Domain Gap.

## 3.3 SyncExecution remains runtime truth

The guard rejects reintroduction of Task runtime ownership.

It checks that DAO/Application code does not again dual-write:

```text
Task.desiredState
Task.observedState
Task.lastError
```

and that query SQL does not again fallback to:

```text
d.desired_state
d.observed_state
d.last_error
```

It also blocks the old side paths:

```text
desiredJobs
hasOtherDesiredRunning
markStarting
```

## 3.4 Immutable version identity

Published-update detection must compare:

```text
SyncExecution.definitionVersionId
vs
RealtimeSyncTask.publishedDefinitionVersionId
```

The guard rejects using legacy:

```text
definition_version
published_version
```

as immutable DefinitionVersion identity.

## 3.5 Restart and Apply stay separate

Application must expose:

```text
restartExecution
applyPublishedVersion
```

and must not reintroduce a generic Application `restart()`.

Frontend must use:

```text
restart-execution
apply-published-version
```

as separate actions.

The legacy HTTP `/restart` alias may exist only when it delegates to `restartExecution()`.

## 3.6 Start may not read current Draft

The static guard requires the Start path to resolve a Published DefinitionVersion and rejects the old mutable-Draft preparation path.

Protected behavior:

```text
Published V3 + Draft r4
Start -> V3
```

## 3.7 Digest/revision naming

The migrated semantic aliases must remain visible:

```text
sourceConfigDigest
draftRevision
artifactDigest
```

This prevents future code from collapsing Draft CAS digest, DefinitionDigest and ExecutionArtifactDigest into one ambiguous concept.

---

# 4. Layer B — framework-free Core Domain compile

Stage 7 removes Spring `@Component` from:

```text
SyncExecutionStateMachine
```

Spring configuration now owns construction:

```text
RealtimeSyncConfiguration
   ↓ @Bean
SyncExecutionStateMachine
```

GitHub Actions compiles the Core kernel directly with JDK 21:

```text
javac --release 21
```

No Maven/Spring classpath is supplied.

This means a future accidental Core import from Spring/Jackson/adapter code causes a real compile failure, not merely a style warning.

---

# 5. Layer B behavior smoke

Executable:

```text
tools/realtime-domain-smoke/RealtimeDomainSmoke.java
```

It verifies framework-free domain behavior:

### SyncDefinition invariants

```text
ReplayKey duplicate fields -> reject
SourceRef == SinkRef       -> reject
valid routes               -> accept
```

### DefinitionDigest

```text
route ordering only changes      -> same digest
ReplayKey ordering only changes  -> same digest
RuntimeEnvironmentRef changes    -> different digest
```

### SyncExecution lifecycle

```text
RUNNING -> blocks second Execution
UNKNOWN -> blocks second Execution
STOPPED -> permits next Execution
STOPPED -> STARTING same Execution -> reject
FAILED  -> RUNNING same Execution  -> reject
```

---

# 6. Layer C — Maven/JUnit deep regression hook

The repository already contains deeper tests for:

```text
architecture boundaries
DefinitionVersion persistence
SyncExecution DAO isolation
Published-vs-Draft execution path
stop-during-start
UNKNOWN / CONFLICT
RestartExecution / ApplyPublishedVersion
cross-instance replacement races
```

The GitHub workflow contains a `Backend regression hook` for these tests.

However `yak-ops` depends on:

```text
io.yak.framework:*:1.0.0-SNAPSHOT
```

and `weifuwan/yak-framework` is a **private GitHub repository**.

A normal repository-scoped `GITHUB_TOKEN` for `yak-ops` cannot checkout that private sibling repository.

Therefore the deep regression layer is enabled only when repository secret:

```text
YAK_FRAMEWORK_TOKEN
```

is configured with read access to `weifuwan/yak-framework`.

## When the secret exists

The workflow performs:

```text
checkout yak-framework
install 1.0.0-SNAPSHOT
build yak-ops realtime reactor dependencies
run targeted realtime JUnit suite
```

including:

```text
RealtimeArchitectureTest
SyncExecutionStateMachineTest
SyncDefinitionDigestCalculatorTest
CdcPipelineSpecCompatibilityMapperTest
DefinitionVersionRepositoryAdapterTest
VersioningRealtimeJobStoreTest
RealtimeJobDaoImplExecutionIsolationTest
RealtimeJobStoreAdapterExecutionProjectionTest
RealtimeExecutionPathTest
RealtimeJobServiceConcurrencyTest
RealtimeJobLifecycleCoordinatorTest
RealtimeWave4DefinitionMutationTest
RealtimeWave5VersionCommandTest
RealtimeWave5VersionCommandRaceTest
```

## When the secret does not exist

The Job emits an explicit GitHub Actions notice:

```text
Backend regression not executed
```

and skips Maven/JUnit steps.

**A green Backend regression hook without the secret must not be interpreted as “JUnit passed”.**

The mandatory enforcement layers remain:

```text
Static domain contract
Framework-free core domain smoke
PR contract
```

If the team wants full backend regression to become a mandatory required check, configure `YAK_FRAMEWORK_TOKEN` first and then make the JUnit step required in branch protection.

---

# 7. Layer D — Pull Request Domain Contract

Realtime-related pull requests must contain:

```text
Domain Impact Analysis
Domain Gap
Domain Compliance Report
```

The workflow receives `$GITHUB_EVENT_PATH`, so the static guard validates the actual PR body.

The workflow also listens to PR `edited` events. A developer can fix the PR description and have the domain contract re-evaluated without pushing a dummy code commit.

Dedicated template:

```text
.github/PULL_REQUEST_TEMPLATE/realtime-sync-domain.md
```

Minimal pre-implementation contract:

```text
Domain Impact Analysis
- Bounded context:
- Aggregate(s):
- SyncDefinition area:
- Invariant/lifecycle impact:
- Layer:
- Current mapping/gap:
- Safety properties to preserve:
- Domain Gap: yes/no
```

Completion contract:

```text
Domain Compliance Report
- Domain rule implemented:
- Aggregate(s) affected:
- Invariant/lifecycle changes:
- Legacy compatibility retained:
- Safety properties preserved:
- DB/API migration mode:
- Tests/guardrails:
- Known gaps remaining:
```

---

# 8. GitHub Actions workflow

File:

```text
.github/workflows/realtime-sync-domain-guardrails.yml
```

Path scope:

```text
realtime backend module
realtime frontend pages
docs/realtime-sync/domain
DOMAIN.md / guardrail tools
realtime PR template
workflow itself
```

Mandatory jobs:

```text
Static domain contract
Framework-free core domain smoke
```

Conditional deep job:

```text
Backend regression hook
  -> full Maven/JUnit only when YAK_FRAMEWORK_TOKEN exists
```

This keeps architectural enforcement reliable without pretending the private cross-repository dependency does not exist.

---

# 9. JUnit architecture guard remains valuable

`RealtimeArchitectureTest` is extended to inspect Core Domain root/nested types through reflection.

It checks framework/adapter leakage through:

```text
annotations
fields
record components
method return types
method parameter types
constructor parameter types
```

When the Maven deep layer is enabled, the same architectural rule is therefore checked in two independent ways:

```text
source-level Python guard
+
runtime-reflection JUnit guard
```

---

# 10. Guardrail modification rule

The guardrail itself is part of the domain contract.

Forbidden shortcut:

```text
feature fails guard
  ↓
disable/remove guard
  ↓
merge feature
```

Correct process:

```text
guard fails
  ↓
implementation bug or Domain Gap?
  ↓
fix implementation
or approve domain change
  ↓
update DOMAIN.md + design docs + replacement guard together
```

A PR weakening a check must explain:

```text
which accepted domain rule changed
why the previous check is no longer correct
which replacement check enforces the new rule
```

---

# 11. What Stage 7 does not solve

Guardrails protect the accepted model. They do not invent answers for unresolved gaps:

```text
Audit-safe Archive / Tombstone
ExecutionPolicy runtime application
Flink FINISHED normal completion / snapshot-only
legacy failure-rate mapping
Compute Environment physical context cleanup
API v2 / physical schema naming cleanup
```

These still require independent Domain Impact Analysis.

---

# 12. Local workflow

Fast deterministic check:

```bash
python3 tools/realtime_domain_guardrails.py
```

Framework-free Core compile/smoke:

```bash
CORE=yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime/src/main/java/io/yak/ops/business/sync/realtime/domain
OUT=.tmp/realtime-domain-core
rm -rf "$OUT" && mkdir -p "$OUT"

javac --release 21 -d "$OUT" \
  "$CORE/RealtimeJobState.java" \
  "$CORE/SyncDefinition.java" \
  "$CORE/RuntimeEnvironmentRef.java" \
  "$CORE/DefinitionDigest.java" \
  "$CORE/SyncDefinitionDigestCalculator.java" \
  "$CORE/DefinitionVersion.java" \
  "$CORE/SyncExecution.java" \
  "$CORE/SyncExecutionStateMachine.java" \
  tools/realtime-domain-smoke/RealtimeDomainSmoke.java

java -cp "$OUT" RealtimeDomainSmoke
```

Full targeted Maven/JUnit requires local access to the private `yak-framework` SNAPSHOT dependency or the configured CI token.

---

# 13. Stage 7 acceptance

Stage 7 is considered implemented when:

```text
[x] Core StateMachine is framework-free
[x] Spring config owns StateMachine bean creation
[x] zero-dependency static domain guard exists
[x] framework-free javac + smoke exists
[x] JUnit architecture guard checks Core purity
[x] realtime PR body contract is machine checked
[x] path-scoped GitHub Actions workflow exists
[x] PR edited event rechecks the contract
[x] private-framework Maven regression has an explicit opt-in hook
[x] DOMAIN.md documents automated enforcement
[x] domain README points to Stage 7
```

End-state development loop:

```text
AI / developer change
      ↓
DOMAIN.md
      ↓
Domain Impact Analysis
      ↓
implementation
      ↓
Static Guard + Core Smoke
      ↓
optional deep Maven/JUnit when private dependency access exists
      ↓
Domain Compliance Report
      ↓
review
```

The Realtime Sync domain is now documented, implemented, and protected by executable checks rather than reviewer memory alone.
