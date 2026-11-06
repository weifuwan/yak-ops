# Yak Ops API 开发规范

1. 开发前必须阅读现有同类功能的实现，优先遵循项目已有的代码风格和架构。
2. 优先复用现有类、接口、工具和方法，禁止重复实现已有功能。
3. 新增代码遵循现有包结构，禁止随意创建新的分层和包。
4. Controller 必须继承 `BaseController`，只负责参数接收、基础校验和调用 Service。
5. Controller 输入参数优先使用 `@RequestParam`、`@PathVariable` 等注解直接接收，只有复杂请求或包含嵌套结构时才使用 `@RequestBody` DTO，禁止为简单参数单独创建 Request 对象。
6. Controller 禁止直接调用 DAO、Mapper 或编写业务逻辑。
7. Controller 禁止使用 `@Service`、`@Repository` 等不属于控制层的注解。
8. Controller 统一使用 `Result<T>` 返回响应，禁止自定义统一响应包装类。
9. 简单的组合返回值优先使用 `Map`，已有对象能够满足需求时禁止新增 VO。
10. 只有明确的复杂响应结构才允许新增 VO，禁止为单个字段或简单结果创建 VO。
11. 请求参数优先复用现有 DTO，复杂请求使用 DTO，禁止无意义地增加对象。
12. 分页参数统一使用 `pageNo` 和 `pageSize`，页码从 1 开始。
13. 分页返回值统一使用 `PageInfo<T>`，禁止新增分页 VO 或自定义分页结构。
14. 分页查询统一使用 MyBatis-Plus 的 `Page` 和 `IPage`，禁止先查询全部数据再在内存中分页。
15. 分页参数统一使用 `BaseController.checkPageParams()` 校验。
16. Service 必须定义接口及对应的 Impl，业务 ServiceImpl 统一继承 `BaseServiceImpl`。
17. 业务逻辑、业务校验和事务管理统一放在 Service 层。
18. 涉及多次关联写入的业务操作必须在 Service 层合理使用 `@Transactional`。
19. 业务异常优先使用现有的 `ServiceException` 和 `Status`，禁止随意定义新的异常体系。
20. DAO 必须定义接口及对应的 Impl，DAO 实现优先继承现有 `BaseDao`。
21. Mapper 统一继承 MyBatis-Plus 的 `BaseMapper`，复杂 SQL 优先放在对应 Mapper XML 中。
22. 数据库查询必须在 DAO 层实现，禁止在 Service 中直接编写 SQL。
23. 数据库实体统一放在 `yak-ops-dao` 的 `entity` 包中，优先复用现有实体。
24. 数据库表名统一使用 `t_yak_ops_` 前缀，字段命名遵循现有数据库规范。
25. 新增或修改表结构时，必须同步维护 MySQL、PostgreSQL 和 H2 的初始化 SQL。
26. 建表时必须合理定义主键、非空约束、唯一约束和必要索引。
27. 涉及 Project 的资源必须校验 Project 权限及资源归属，禁止跨 Project 访问。
28. 涉及密码、Token 和连接凭证的接口，禁止返回明文敏感信息。
29. 新增接口必须遵循现有 REST 路径、HTTP 方法和异常处理规范。
30. 涉及资源创建、修改和删除的操作，按现有审计机制记录操作日志。
31. 新增功能必须补充必要的测试用例，并执行对应模块的现有 CI 检查。
32. 禁止引入 MVP、临时兼容逻辑、无意义的包装类和过度设计。
33. 禁止添加无意义的防御性校验、解释性注释及冗余代码。
34. 代码格式统一遵循项目现有的 Spotless 配置。
