package io.yak.ops.common.page;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * HTTP 分页数据封装对象。
 *
 * <p>该类型只负责现有 {@code bizData + pagination} HTTP JSON 契约。
 * Repository / Service 内部分页统一使用 {@link PageData}，并在 HTTP 输出边界转换为本类型。</p>
 *
 * @param <T> 业务数据类型
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class PagingData<T> {

    /** 当前分页的业务数据列表。 */
    private List<T> bizData;

    /** 分页信息。 */
    private PaginationData pagination;

    public PagingData(PageData<T> pageData) {
        if (pageData == null) {
            this.bizData = new ArrayList<>();
            this.pagination = PaginationData.builder()
                    .total(0L)
                    .pages(0L)
                    .pageNo(1L)
                    .pageSize(0L)
                    .build();
            return;
        }
        this.bizData = new ArrayList<>(pageData.records());
        this.pagination = PaginationData.builder()
                .total(pageData.total())
                .pages(pageData.pages())
                .pageNo(pageData.pageNo())
                .pageSize(pageData.pageSize())
                .build();
    }

    public static <T> PagingData<T> from(PageData<T> pageData) {
        return new PagingData<>(pageData);
    }
}
