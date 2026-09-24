# Backend Test Rules

Scope:
- Yak Ops backend automated tests
- Java behavior that needs regression evidence

Load With:
- `JAVA_RULES.md`
- nearest owner RULES

## When Tests Are Required

优先为这些变化新增或更新测试：

- Datasource create / update / delete 业务规则。
- connection normalize / merge secret / connection test。
- Catalog query、identifier policy、cache invalidation。
- SQL Execution 状态、事务、取消、结果转换和 audit read model。
- Controller 请求、权限、HTTP Contract、错误映射。
- Repository / DAO 自定义查询、分页、更新、Mapper XML。
- Datasource Plugin 稳定协议、metadata、config、catalog、execution contract。
- 值得长期防回归的 Bug。

默认不机械测试：

- DTO / VO / PO 样板。
- Lombok 生成代码。
- 简单枚举和常量。
- MyBatis-Plus 已保证的普通 CRUD。
- private 实现细节。
- 没有业务语义的纯透传。
- 只为了覆盖率数字存在的断言。

## Test Boundary

### Datasource Business

- 默认 JUnit + Mockito。
- Mock owner 边界外依赖。
- 测试业务结果、异常、状态变化和有意义协作。
- 不为了 Unit Test 启动完整 Spring Boot。

### Controller

- 优先 focused MVC test / `@WebMvcTest + MockMvc`。
- Mock Controller 之后的业务 owner。
- 保护 validation、权限、HTTP status、Result contract 和错误映射。
- 不重复测试内部业务实现。

### Persistence

- 普通 CRUD 不重复测试框架。
- 自定义 SQL、Mapper XML、事务或数据库语义使用 Integration Test。
- Integration Test 优先 Testcontainers + MySQL。
- 不 Mock Mapper 和数据库后再声称验证了 SQL。

### Datasource Plugin

保护稳定且确定性的协议行为：

- plugin discovery / descriptor
- config normalization
- URL / property generation
- catalog request / response mapping
- execution adapter behavior
- provider error mapping

默认不访问真实外部数据库。只有明确的 Integration Test 才允许依赖容器化数据库。

## Mock Rules

Must:
- 只 Mock 被测试 owner 外部的依赖。
- 协作本身属于 Contract 时才 verify 调用。

Must Not:
- Mock 被测试行为本身。
- 同时 Mock Repository、DAO、Mapper 后声称验证持久化。
- 对所有内部调用做 verify 来锁死实现。

## Stability

测试必须独立、可重复。

禁止：
- `Thread.sleep()` 等待异步结果。
- 依赖执行顺序。
- 依赖生产数据。
- 使用真实账号或密钥。
- 随机决定测试是否通过。

## Location

```text
Unit Test
<module>/src/test/java/.../XxxTest.java

Integration Test
<module>/src/test/java/.../XxxIT.java
```

## Execution

```bash
./mvnw test
./mvnw verify
```

## Current CI Gap

当前 GitHub Actions 事实：

- `Backend Compile` 执行 `-DskipTests -pl yak-ops-boot -am compile`。
- 主 `CI` 后端打包执行 `clean package -DskipTests`。
- 主 `CI` 前端执行 `yarn build`。

因此当前 CI 绿色只证明编译 / 打包 / 前端构建成功，**不证明后端测试或前端测试已执行**。

在 CI 补齐测试前，需要测试证据的改动必须显式运行对应测试，并在 PR 中说明。
