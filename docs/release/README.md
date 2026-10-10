# Yak Ops Version Management

Status: Active

Scope:

- Yak Ops 产品版本
- Git Tag
- Release Artifact
- Docker Image
- Release Gate
- 数据库 Migration 冻结规则
- 发布与回滚

本规则管理的是 Yak Ops **产品发布版本**，不管理 Data Sync Task 的 `definitionVersion`。

当前 Release Candidate 版本：

```text
1.2.0
```

已发布稳定版本：

```text
1.0.0
1.1.0
```

当前候选版本：

```text
1.2.0 — Scope Frozen / Ready for Formal Release Gate
```

下一开发版本 Contract：

```text
1.3.0 — Multi-Table + Incremental + Long-Running
```

v1.3.0 规划材料：

- [v1.3.0 Release Contract](./v1.3.0.md)

PR0 只冻结 v1.3 架构与范围边界；在 v1.2.0 正式发布前不切换仓库 Product Version，也不修改 v1.2 Release Candidate / Migration / Evidence。

v1.2.0 候选材料：

- [v1.2.0 Release Contract](./v1.2.0.md)
- [v1.2.0 Release Notes](./v1.2.0-release-notes.md)
- [v1.2.0 Release Readiness](./v1.2.0-readiness.md)
- [v1.2.0 Release Evidence](./v1.2.0-release-evidence.md)

v1.1.0 发布材料：

- [v1.1.0 Release Contract](./v1.1.0.md)
- [v1.1.0 Release Notes](./v1.1.0-release-notes.md)
- [v1.1.0 Release Readiness](./v1.1.0-readiness.md)
- [v1.1.0 Release Evidence](./v1.1.0-release-evidence.md)

## 1. 两种版本必须分离

Yak Ops 同时存在两类版本，它们语义完全不同：

```text
Product Version
v1.0.0 / v1.0.1 / v1.1.0
→ 描述整个 Yak Ops 产品发布物

Task definitionVersion
v1 / v2 / v3
→ 描述单个 Data Sync Task 的可执行定义
```

必须遵循：

- Product Version 不能驱动 Task `definitionVersion`。
- Task `definitionVersion` 不能作为产品 Release 版本。
- REALTIME CDC state 继续按 Workspace / Task / definitionVersion 隔离。
- 产品升级不能因为 Product Version 变化而自动创建新的 CDC state domain。

## 2. Product Version

Yak Ops 使用 Semantic Versioning：

```text
MAJOR.MINOR.PATCH
```

版本含义：

- `PATCH`：兼容的 Bug Fix，不新增需要用户重新理解的产品能力。
- `MINOR`：向后兼容的新能力。
- `MAJOR`：存在明确的不兼容产品、API、部署或数据契约变化。

示例：

```text
1.0.0 → 第一个稳定版本
1.0.1 → V1 Bug Fix
1.1.0 → V1 新增兼容能力
2.0.0 → Breaking Change
```

预发布版本使用 SemVer prerelease：

```text
1.0.0-rc.1
1.0.0-rc.2
```

预发布版本不得更新 `latest`。

## 3. Version Identity

Release Commit 上的版本必须保持一致：

```text
Git Tag
v1.0.0
   ↓ strip "v"
Maven project.version
1.0.0
   ↓
Distribution
yak-ops-1.0.0.tar.gz
   ↓
Docker Image
weifuwan/yak-ops:1.0.0
weifuwan/yak-ops-api:1.0.0
```

规则：

- 根 `pom.xml` 的 `project.version` 是构建产物版本来源。
- Git Tag 使用 `v{version}`。
- `release.env` 中的产品版本必须与 Maven Product Version 一致。
- Compose 中默认镜像版本不得长期保留与当前 Release 不一致的历史版本。
- OCI Image Label 的 `org.opencontainers.image.version` 必须等于 Product Version。
- OCI Image Label 的 revision 必须可以追溯到 Release Commit。
- Release 时任何版本不一致都属于 Release Blocker。

版本一致性由 `scripts/release/check-release-metadata.sh` 机械校验，并已进入普通 Quality Check。传入 `vX.Y.Z` 时还会校验期望 Tag 版本与仓库元数据一致；正式 Release Workflow 的编排由后续 PR 负责。

## 4. Git Tag

正式发布 Tag：

```text
v1.0.0
v1.0.1
v1.1.0
```

预发布 Tag：

```text
v1.0.0-rc.1
```

规则：

- Tag 只能指向通过 Release Gate 的 Commit。
- 正式发布 Tag 创建后不可移动、不可复用。
- 已发布版本不得重新使用相同 Tag 指向不同代码。
- 修复已发布版本必须创建新版本，例如 `v1.0.1`。
- Release Gate 未通过时禁止创建正式 Tag。

## 5. Distribution Artifact

`yak-ops-dist` 是 Yak Ops Release Packaging 的唯一所有者。

正式产物命名：

```text
yak-ops-{version}.tar.gz
```

例如：

```text
yak-ops-1.0.0.tar.gz
```

Distribution 至少包含：

```text
bin/
conf/
libs/
web/
jdbc-drivers-builtin/
LICENSE
NOTICE
README.md
```

Release 必须通过 `scripts/release/verify-distribution.sh` 检查 Tar 根目录、关键文件、启动脚本执行权限和内置 MySQL Driver；验证通过后生成 `yak-ops-dist/target/SHA256SUMS`。

建议 Release Asset：

```text
yak-ops-1.0.0.tar.gz
SHA256SUMS
```

## 6. Docker Image

正式镜像：

```text
weifuwan/yak-ops:{version}
weifuwan/yak-ops-api:{version}
```

其中：

- `yak-ops`：Frontend Runtime。
- `yak-ops-api`：Backend Runtime。

Docker Image 必须以已经通过 Release Gate 的 Distribution 为输入，不能独立从另一套源码构建出不同内容。

推荐发布链路：

```text
Source
  ↓
Frontend Build + Maven Build
  ↓
yak-ops-{version}.tar.gz
  ├──→ Backend Image
  └──→ Frontend Image
```

`latest` 是可变的便利标签，不是版本身份。

必须：

- 先发布不可变版本标签。
- 完成镜像 Smoke Test。
- 最后才允许更新 `latest`。
- 回滚时使用明确版本标签，不依赖 `latest`。

## 7. Release Gate

Yak Ops 使用通用 `Release Gate` / `Release Decision`，不再把正式发布逻辑硬编码为某个历史版本的场景列表。

每个版本的具体 Manual E2E 要求写在自己的 Readiness 文档中。正式 Decision 只在对应 Readiness 已明确 `Status: Ready` 且人工确认全部为 true 时允许通过。

Yak Ops 不以“功能开发完成”作为可发布标准。

正式 Release 必须同时通过四层 Gate。

### Gate 1 — Code Quality

必须通过仓库 Quality Check：

```text
Backend logging rules
Backend format
Backend compile
Backend verify

Frontend format
Frontend lint
Frontend type check
Frontend architecture
Frontend build
```

### Gate 2 — Backend Acceptance

Release 时必须执行完整 Backend Acceptance。

不能因为当前 Commit 没有命中特定 path filter 而跳过：

```text
JDBC Acceptance
MySQL CDC Cross-Database Acceptance
Data Sync Automation Acceptance
```

日常 PR 只自动执行 Quality Check 的 4 个基础检查。JDBC Source 与 JDBC Sink 的真实数据库验收不再因 Connector 路径变化而自动触发；需要独立验收时，在 GitHub Actions 选择 `JDBC Source Acceptance` 或 `JDBC Sink Cross-Database Acceptance`，使用 `Run workflow` 选择要验证的分支。两条工作流保留全部 MySQL / PostgreSQL / Oracle 集成测试。

正式 Release Gate 会复用并要求两条 JDBC 工作流全部通过，同时保留 Full Backend Acceptance 的完整产品验收门禁。当前 Full Backend Acceptance 仍在未恢复产品运行链路时 fail-closed；独立 JDBC 验收通过不等于产品 E2E 可发布。

### Gate 3 — Build & Deployment Acceptance

必须真实构建：

```text
Frontend
Backend
yak-ops-dist
Docker backend image
Docker frontend image
```

Distribution 至少验证：

- tar.gz 可以正常解压。
- `libs/yak-ops-api.jar` 存在。
- `web/index.html` 存在。
- `conf/application.yml` 存在。
- `bin/run-yak-ops.sh` 存在且具备执行权限。
- 内置 JDBC Driver 满足当前发布契约。

Docker 至少验证：

- Backend Healthcheck 成功。
- Frontend Runtime 正常启动。
- Compose 可以完成最小部署。
- 用户能够打开页面并完成登录 Smoke Test。

### Gate 4 — Product E2E

Manual E2E 验证用户可见的完整产品链路。

原则：

- 自动 Acceptance 负责 Runtime / Connector / Cross-Database 行为。
- Manual E2E 负责真实 UI 产品路径。
- Manual E2E 不扩展成所有数据库、参数和写入模式的笛卡尔积矩阵。
- 与文档预期不一致的行为，在原因明确前按失败处理。

Data Sync Manual E2E 入口：

- [Data Sync Manual E2E](../e2e/data-sync/README.md)

## 8. Release Blocker

以下任意一项成立时禁止正式发布：

- Release Gate 任一必选项失败。
- Product Version 不一致。
- Distribution 无法从 Release Commit 重建。
- Docker Image 无法追溯到 Release Commit。
- 存在未关闭的 P0 / P1 Release Blocker。
- 当前承诺能力的 Manual E2E 主链路失败。
- 数据库 Migration 不满足发布冻结规则。
- 发布文档把未来能力描述成当前能力。
- 发现会导致凭证泄露、跨 Workspace 越权或数据破坏的已知问题。

P0 / P1 的判断以是否阻断当前版本安全部署和核心主链路为准，不要求引入复杂缺陷分级系统。

## 9. Database Migration Freeze

Yak Ops 把开发期 Schema 演进与正式用户升级历史分开管理。

`v1.0.0` 是第一个正式 Migration 冻结点：

```text
v1.0.0
└── V1__baseline.sql
```

从 v1.1.0 开始，每个 Product Version **最多新增一个**正式 Release Migration：

```text
v1.0.0 → V1__baseline.sql
v1.1.0 → V2__v1_1_0.sql
v1.1.1 → no migration, if Schema is unchanged
v1.2.0 → V3__v1_2_0.sql
```

开发期间允许为当前未发布版本按能力创建多个 Draft Migration；在 Release Freeze 阶段，如果这些 Migration 尚未发布且没有不可重建共享环境依赖，则必须先 squash 为 0 或 1 个 Release Migration，再进入正式 Release Gate。

必须：

- 已发布 Migration 永久冻结，禁止修改、rename、删除、重排或参与后续 squash。
- 已进入不可重建共享环境并需要保留升级历史的 Migration 同样视为冻结。
- Draft Migration 只进入可重建的开发 / E2E 环境；Draft checksum 变化后重建数据库，不用 Flyway repair 掩盖差异。
- Release Migration 使用 `V{flywayVersion}__v{major}_{minor}_{patch}.sql`。
- 一个 Release Migration 可以包含该版本多个 Schema 主题，但应按能力注释分段并保持经过验证的 SQL 顺序。
- 没有 Schema 变化的 Product Version 不创建空 Migration。
- 正式 Release Gate 必须通过 `scripts/release/check-release-migration.sh <version>`，确认没有遗留 Draft、没有同版本多个 Release Migration，也没有超过目标 Product Version 的未来 Migration。

Product Version 与 Flyway Version 彼此独立；Release Migration 文件名负责建立两者的可追溯关系。

## 10. Release Evidence

每个正式版本必须能回答：

> 这个 Release 是哪一个 Commit、通过了什么验证、产出了什么不可变 Artifact？

至少记录：

- Product Version。
- Git Tag。
- Release Commit SHA。
- Quality Check 结果。
- Full Backend Acceptance 结果。
- Manual E2E 结果。
- Distribution 文件名与 SHA-256。
- Backend / Frontend Image Tag。
- Backend / Frontend Image Digest。
- Release Notes。
- 已知限制。

Release Evidence 可以由 GitHub Release、CI Artifact 和版本文档共同承载，但版本与 Commit 身份必须唯一。

## 11. Rollback

应用回滚使用明确版本：

```text
1.0.1 → 1.0.0
```

禁止使用 `latest` 作为回滚目标。

回滚时必须特别保护：

- 生产数据库。
- ${yak.ops.home}/data。
- REALTIME CDC offset / schema history。
- 用户上传或本地资源目录。
- 外置 JDBC Driver。

Realtime 数据目录被删除会失去从之前 Debezium offset 继续的能力，因此应用镜像回滚不能隐式清理持久化数据。

数据库 Migration 默认采用 forward-fix 思路。已经执行到共享环境的 Migration 不通过修改旧 SQL 回滚；需要数据库数据级回退时，应使用经过确认的备份恢复或独立修复 Migration。

## 12. Release Flow

正式版本推荐流程：

```text
Feature Complete
      ↓
Scope Freeze
      ↓
Release Candidate
      ↓
Quality Check
      ↓
Full Backend Acceptance
      ↓
Manual Product E2E
      ↓
Build Distribution
      ↓
Distribution Verification
      ↓
Build Docker Images
      ↓
Compose Smoke Test
      ↓
Version Consistency Check
      ↓
Release Gate PASS on exact Ready commit
      ↓
Release Publish verifies exact Gate run + commit
      ↓
Tag vX.Y.Z
      ↓
Publish Release Assets
      ↓
Push Immutable Image Tags
      ↓
Update latest
```

任何步骤失败都停止正式发布。

## 13. Version Contract Ownership

长期版本规则由本文件维护。

每一个具体版本使用独立版本 Contract，例如：

```text
docs/release/v1.0.0.md
docs/release/v1.1.0.md
docs/release/v1.2.0.md
docs/release/v1.3.0.md
```

具体版本 Contract 负责定义：

- Scope。
- Non-Goals。
- 版本特有验收标准。
- Release Checklist。

长期规则和具体版本 Contract 冲突时，必须在发布前显式修正文档，不能通过口头约定绕过 Release Gate。
