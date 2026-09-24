# Java Rules

Scope:
- All Yak Ops Java production code

Load With:
- `ARCHITECTURE.md`
- nearest module RULES

Principles:
- 简单
- 紧凑
- 统一
- 先复用，后抽象
- 复杂度必须证明自己有必要存在

## Must

- 能一行清晰表达就保持一行；明显过长再换行。
- 先读当前类和直接依赖，再决定新增结构。
- 当前类用 `private` 方法能收敛时，优先 private method。
- JDK、Spring、MyBatis-Plus 或现有工具已经提供的能力直接复用。
- 一个类只承担一个明确 owner 的职责。
- 不可变数据适合 `record` 且更清晰时优先使用 `record`。
- Lombok 能明显减少样板代码时可以使用，但不能隐藏关键行为。
- 公共接口、核心类和非直观行为只注释职责、边界和原因。
- 删除过期注释和已经不存在的架构描述。
- 修改完成后执行与改动匹配的显式编译、构建、静态检查或手工验证。

## Must Not

- 没有真实 ownership 时新增 Manager / Coordinator / Handler / Assembler / Adapter / Helper / Base。
- 为了文件长度拆类。
- 为了“统一架构”增加无意义 interface + impl。
- 增加纯转发、重复判空、重复转换、重复赋值。
- 重复封装框架已有能力。
- 为未来需求提前创建空层、空接口或扩展点。
- 重建已删除的测试或 CI 体系作为任务副作用。

## Abstraction Test

新增一个类或层之前至少能回答一个问题：

- 它是否拥有独立生命周期？
- 它是否拥有稳定 Contract？
- 它是否隔离外部协议或基础设施？
- 它是否拥有明确业务规则？
- 它是否被多个真实调用方以同一语义复用？

如果都不是，优先不抽。
