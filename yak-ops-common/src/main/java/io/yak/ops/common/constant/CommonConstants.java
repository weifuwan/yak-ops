package io.yak.ops.common.constant;

/**
 * Yak Ops 跨领域公共常量，只承载稳定且全局共享的代码契约。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class CommonConstants {

    /** Yak Ops API 根路径。 */
    public static final String API_PREFIX = "/api/v1";

    /** 默认页码，从 1 开始。 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页记录数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 单次分页允许的最大记录数。 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 单次分页允许的最大排序字段数。 */
    public static final int MAX_SORT_FIELDS = 3;

    /** 无明确操作人时使用的系统用户标识。 */
    public static final String SYSTEM_USER = "system";

    private CommonConstants() {}
}
