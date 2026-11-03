# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是面向数据集成与同步场景的数据平台，通过 Task Definition 管理同步配置、发布和执行，目前保留通用任务管理和数据源能力，并持续迁移 Flink CDC 代码；旧数据同步 Task 插件已不再注册，具体可执行能力以实际版本和测试结果为准。已发布版本的能力请查看对应 Release。

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

## 数据同步状态

旧数据同步产品后端、监控接口及专用任务索引已移除。前端本轮不做修改，因此旧同步页面及监控面板暂时没有可用的后端服务。

通用数据源、任务定义、任务实例与调度 API 保留；新的 Flink CDC 引擎仍在单独迁移和接入。参见 [历史 Data Sync 契约说明](docs/data-sync-pipeline.md)，该文档仅作退役记录，不再作为当前功能使用说明。

## Development

- [开发与验证](DEVELOPMENT.md)
- [贡献与 YOIP](.github/CONTRIBUTING.md)
- [YOIP 模板](.github/YOIP_TEMPLATE.md)
- [前端开发规范](yak-ops-ui/FRONTEND_RULES.md)

## Releases

- [发布历史与 Release Notes](docs/releases/README.md)
- [历史 GitHub Releases](https://github.com/weifuwan/yak-ops/releases)
- [发布操作](scripts/release/README.md)

发布检查包括 CI、当前 Business/CDC 集成测试、Distribution 与 Docker/Compose 验证。各测试是否通过以对应 Release Gate 的实际报告为准。发布证据来自 GitHub Actions、Git Tags 和 Release Assets。
