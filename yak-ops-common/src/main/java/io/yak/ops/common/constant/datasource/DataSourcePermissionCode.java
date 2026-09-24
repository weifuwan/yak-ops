package io.yak.ops.common.constant.datasource;

/** 数据源管理稳定权限编码。 */
public final class DataSourcePermissionCode {

    /** 查看数据源页面、列表、详情和 Catalog 元数据。 */
    public static final String READ = "resource:data-source:read";

    /** 新增数据源。 */
    public static final String CREATE = "datasource:create";

    /** 编辑数据源。 */
    public static final String UPDATE = "datasource:update";

    /** 删除数据源。 */
    public static final String DELETE = "datasource:delete";

    /** 测试数据源连接。 */
    public static final String TEST = "datasource:test";

    private DataSourcePermissionCode() {}
}
