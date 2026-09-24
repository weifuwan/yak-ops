package io.yak.ops.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 分页数据封装对象。
 *
 * <p>该类型只负责现有 {@code bizData + pagination} HTTP JSON 契约。
 * Repository / Service 内部分页统一使用 {@link PageData}，并在 HTTP 输出边界转换为本类型。</p>
 *
 * @param <T> 业务数据类型
 * @author weifuwan
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class PagingData<T> {

    /**
     * 当前分页的业务数据列表。
     */
    private List<T> bizData;

    /**
     * 分页信息。
     */
    private Pagination pagination;

    /**
     * 根据框架无关分页数据创建 HTTP 分页数据。
     *
     * @param pageData 业务分页数据
     */
    public PagingData(PageData<T> pageData) {
        if (pageData == null) {
            this.bizData = new ArrayList<>();
            this.pagination = Pagination.builder()
                    .total(0L)
                    .pages(0L)
                    .pageNo(1L)
                    .pageSize(0L)
                    .build();
            return;
        }
        this.bizData = new ArrayList<>(pageData.records());
        this.pagination = Pagination.builder()
                .total(pageData.total())
                .pages(pageData.pages())
                .pageNo(pageData.pageNo())
                .pageSize(pageData.pageSize())
                .build();
    }

    /**
     * 根据框架无关分页数据创建 HTTP 分页数据。
     *
     * @param pageData 业务分页数据
     * @param <T> 业务数据类型
     * @return HTTP 分页数据
     */
    public static <T> PagingData<T> from(PageData<T> pageData) {
        return new PagingData<>(pageData);
    }

    /**
     * 分页信息。
     *
     * <p>记录总数据量、总页数、当前页码和每页数据量。</p>
     *
     * @author weifuwan
     */
    @Getter
    @Setter
    @Builder
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    public static class Pagination {

        /**
         * 数据总条数。
         */
        private long total;

        /**
         * 总页数。
         */
        private long pages;

        /**
         * 当前页码。
         */
        private long pageNo;

        /**
         * 每页数据条数。
         */
        private long pageSize;
    }
}
