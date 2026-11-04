# Yak Ops Scheduler

[English](README.md)

`yak-ops-scheduler` 是从 **Apache DolphinScheduler 3.4.3-release** 迁移而来的源码模块，目前仍然保留原有工作流执行体系。它不是重新实现的轻量调度器，也尚未直接接入 `yak-ops-cdc` 和 `yak-ops-ui`。

## 迁移来源与范围

- 上游基线：[apache/dolphinscheduler 26228f9c](https://github.com/apache/dolphinscheduler/commit/26228f9c5bff24b3ded7095484caf7a5ecffa050)，分支 `3.4.3-release`。
- 迁移后 Maven 坐标：`io.yak.ops:yak-ops-*:1.4.0`；上游 POM 版本为 `3.4.4-SNAPSHOT`。
- 当前包含 22 个顶层模块、99 个 Maven POM。
- 原生依赖基线为 Spring Boot 2.x 及较早的 Java 工具链。暂时使用 **JDK 11** 编译和验证 Scheduler，避免将其依赖直接合并到根项目或 CDC 的版本体系。
- Java 包名暂保留 `org.apache.dolphinscheduler`，便于与上游源码对照。

`MIGRATION_MANIFEST.json` 是**最初迁移时的历史清单**，用于记录源码 SHA256 和被排除的内容；后续删除文档不会同步改写这份基线。迁移说明和原始根 POM 分别保留在 `UPSTREAM.md`、`UPSTREAM_ROOT_POM.xml`。Apache `LICENSE`、`NOTICE`、源码版权头和相关第三方许可证需要保留。

## 模块与职责

| 模块 | 主要职责 |
| --- | --- |
| `yak-ops-master` | Workflow 命令、执行图、状态机、任务派发、故障恢复 |
| `yak-ops-worker`、`yak-ops-task-executor` | Task 执行与生命周期管理 |
| `yak-ops-scheduler-plugin` | `SchedulerApi` 抽象与 Quartz 定时触发 |
| `yak-ops-api`、`yak-ops-service` | Workflow、Schedule 等管理接口与服务 |
| `yak-ops-dao`、`yak-ops-dao-plugin` | 数据持久化、初始化 SQL 与数据库方言 |
| `yak-ops-registry`、`yak-ops-extract`、`yak-ops-eventbus` | 服务注册协调、RPC 契约和内部事件 |
| `yak-ops-spi`、`yak-ops-task-plugin`、`yak-ops-datasource-plugin`、`yak-ops-storage-plugin`、`yak-ops-alert` | 插件接口及具体扩展 |
| `yak-ops-standalone-server` | 单 JVM 集成启动入口，主要用于开发和冒烟验证 |

**Quartz 触发器不会直接运行 CDC Pipeline。** 当前实现将 Schedule 绑定到 DolphinScheduler 的 Workflow Definition；触发后生成 Workflow Command，由 Master 推进 Workflow，再派发 Task 给 Worker。

Standalone 是开发和冒烟验证入口，不等于已经完成 Yak Ops 生产集成。默认内存 H2 的状态不会在重启后保留。

## 当前验证与集成状态

已有 JDK 11 CI 记录显示：所选的 99 个 Maven 模块完成编译，部分 Master/EventBus 测试、H2 Workflow 集成测试通过，Standalone 能启动且健康检查覆盖 API、Master、Worker、Alert 和 DB。

**这些记录不代表所有插件、JDK 21 兼容性或 Yak Ops UI/CDC 集成已经验证通过。** 上游的独立 UI、distribution 和部分大数据插件并未迁移；公共 API 内仍可能存在 K8s、YARN、Hadoop 残留代码。

## 编译与启动

在仓库根目录执行：

```sh
# 检查 Scheduler Maven 模块模型
bash mvnw -B -ntp -pl :yak-ops-scheduler validate

# 使用 JDK 11 编译所选 Scheduler 模块
bash mvnw -B -ntp -f yak-ops-scheduler/pom.xml \
  -Dmaven.test.skip=true -Dspotless.skip=true -Djacoco.skip=true \
  -Danalyze.skip=true compile

# 使用 JDK 11 启动本地 H2 Standalone
bash scripts/run-scheduler-standalone.sh
```

Standalone 成功启动后，健康检查地址：

`http://127.0.0.1:12345/dolphinscheduler/actuator/health`

更详细的测试命令、架构约束和模块职责参见根目录 [DEVELOPMENT.md](../DEVELOPMENT.md)，仓库修改约束参见 [AGENTS.md](../AGENTS.md)。

## 维护原则

第一阶段只删除过期文档和旧插件清单，不改动 Java 源码、测试、POM 和运行资源。删除的模块级 `CLAUDE.md` 中有价值的共性说明已集中到 `DEVELOPMENT.md`。保留历史迁移清单作为溯源基线，不把它误当作当前目录快照。
