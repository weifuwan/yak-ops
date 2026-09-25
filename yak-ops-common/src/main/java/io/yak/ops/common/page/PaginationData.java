package io.yak.ops.common.page;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * HTTP 分页元数据，记录总量、总页数、当前页码和每页数量。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class PaginationData {

    /** 数据总条数。 */
    private long total;

    /** 总页数。 */
    private long pages;

    /** 当前页码。 */
    private long pageNo;

    /** 每页数据条数。 */
    private long pageSize;
}
