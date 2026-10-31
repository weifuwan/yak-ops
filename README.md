# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是面向数据集成与同步场景的数据平台，通过 Task Definition 管理同步配置、发布和执行，由 YakFlow 执行 JDBC 批量读取与 MySQL CDC 同步。已发布版本的能力请查看对应 Release。

## Build and Run

运行 Distribution 需要 Java 21，并配置元数据库连接和 Datasource Master Key。

```sh
# Docker Compose，内置元数据库
cp .env.example .env
# 按下方说明编辑 .env，然后启动
docker compose up -d
```

首次使用空数据库时，在 `.env` 设置 `YAK_SECURITY_BOOTSTRAP_ENABLED=true` 和独立的强密码 `YAK_SECURITY_BOOTSTRAP_PASSWORD`，启动成功并创建管理员后将开关改回 `false` 并重新部署。已有用户数据的环境保持关闭。没有配置管理员密码时，不允许启用初始化。

在非本地环境必须修改示例数据库密码并设置新的 `YAK_OPS_DATASOURCE_MASTER_KEY`。使用外部数据库可选择 `compose.without-mysql.yaml`，两种部署方式都使用 `YAK_DATABASE_*` 连接元数据库。

正式 Distribution 为 `yak-ops-{version}.tar.gz`，解压后通过 `./bin/run-yak-ops.sh` 启动。

## Data Sync

新建同步任务时，选择来源和目标类型，再选择链路支持的产品：

- **单表离线**：选择一张表，配置目标字段、映射表达式和行过滤，手动执行或按计划执行有界批次。
- **整库实时**：选择一张或多张表，配置表映射，并选择全量后持续增量、仅增量或全量后结束。

“整库实时”的全量后结束策略仍归入该产品，但执行是可结束的批次；它不持续监听后续变更。保存、发布、执行和调度是不同操作。数据源凭据由 Workspace 中的数据源管理，任务参数不保存凭据。

配置示例、增量约束、字段 Transform 和产品筛选接口见 [Data Sync 使用与 Pipeline 定义](docs/data-sync-pipeline.md)。

## Development

- [开发与验证](DEVELOPMENT.md)
- [贡献与 YOIP](.github/CONTRIBUTING.md)
- [YOIP 模板](.github/YOIP_TEMPLATE.md)
- [前端开发规范](yak-ops-ui/FRONTEND_RULES.md)

## Releases

- [发布历史与 Release Notes](docs/releases/README.md)
- [历史 GitHub Releases](https://github.com/weifuwan/yak-ops/releases)
- [发布操作](scripts/release/README.md)

发布检查包括 CI、JDBC Source/Sink、CDC、独立 `yak-ops-e2e` 模块的产品/恢复集成测试、Playwright UI、Distribution 与 Docker/Compose 验证。`yak-ops-boot` 模块内旧的 E2E 未作为当前测试入口；各测试是否通过以对应 Release Gate 的实际报告为准。发布证据来自 GitHub Actions、Git Tags 和 Release Assets。
