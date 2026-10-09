# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是一个面向数据集成与数据同步场景的数据平台项目。当前 V1 聚焦在可部署、可验收的核心链路，而不是一次覆盖完整数据平台能力。

## 当前分支

数据源、用户、工作空间管理保持不变。数据同步已经恢复离线 / 实时任务页面、Controller、Service、DAO、任务 / 多表路由 / 调度配置、Schema 预览和历史执行记录查询；既有 Flyway 表结构不变。

**旧同步引擎仍然移除。** JDBC / CDC Source、Reader、Sink、Debezium、业务执行器和旧 LocalExecution 不恢复。当前可以维护任务、查看历史记录，但无法运行同步或启用调度（明确返回 `ENGINE_UNAVAILABLE`，不创建空转实例）。新的 Core-based Runtime 框架仍保留供后续接入；历史版本验收不代表当前分支同步可执行。

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
