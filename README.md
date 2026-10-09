# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是一个面向数据集成与数据同步场景的数据平台项目。当前 V1 聚焦在可部署、可验收的核心链路，而不是一次覆盖完整数据平台能力。

## 当前分支

数据源管理、用户管理、工作空间管理仍保留。离线同步与实时同步旧实现（包括 JDBC / CDC Connector、业务执行器、同步 Controller 和前端任务页面）已清理，**当前分支不提供数据同步功能**。

Data Sync 只保留稳定业务接口、DTO / VO、已发布数据库迁移；Yak Ops Core 与新的通用 Runtime 框架供后续重新实现。历史 V1 发布范围与验收结果见下方发布材料，不等于当前开发分支能力。

## 文档

- [文档入口](docs/README.md)
- [架构](ARCHITECTURE.md)
- [版本管理](docs/release/README.md)
- [v1.0.0 Release Contract](docs/release/v1.0.0.md)
- [v1.1.0 Release Contract](docs/release/v1.1.0.md)（Released）
- [v1.1.0 Release Readiness](docs/release/v1.1.0-readiness.md)
- [v1.1.0 Release Notes](docs/release/v1.1.0-release-notes.md)
- [v1.2.0 Release Contract](docs/release/v1.2.0.md)（Release Candidate / Ready for Gate）
- [v1.2.0 Release Readiness](docs/release/v1.2.0-readiness.md)（Ready）
- [v1.2.0 Release Notes](docs/release/v1.2.0-release-notes.md)
- [v1.2.0 Release Evidence](docs/release/v1.2.0-release-evidence.md)
- [Data Sync 手工 E2E](docs/e2e/data-sync/README.md)

## Distribution

正式 Distribution：

```text
yak-ops-{version}.tar.gz
```

运行 Tar 包需要 Java 21，并需要配置 Yak Ops 元数据库连接与 Datasource Master Key。

启动入口：

```bash
./bin/run-yak-ops.sh
```

Docker 部署可以使用：

- `compose.yaml`：包含 MySQL。
- `compose.without-mysql.yaml`：使用外部 MySQL。

复制 `.env.example` 为本地 `.env` 后，请在任何非本地环境替换示例密码并设置新的 `YAK_OPS_DATASOURCE_MASTER_KEY`。

## Release

Release 不以“代码能编译”作为上线标准。

正式版本需要通过：

```text
Quality Check
→ Full Backend Acceptance
→ Manual Product E2E
→ Distribution Verification
→ Docker / Compose Smoke Test
→ Version Consistency
→ Release Gate PASS
```

详细规则见 [Version Management](docs/release/README.md)。
