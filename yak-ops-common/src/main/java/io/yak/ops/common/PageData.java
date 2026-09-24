package io.yak.ops.common;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 与具体持久化框架无关的统一分页数据。
 *
 * <p>该类型用于 Repository / Service 等内部业务边界，避免 MyBatis {@code IPage} 等基础设施类型向上泄漏。
 * HTTP 输出由 {@link PagingData} 负责，以保持现有接口 JSON 结构稳定。</p>
 *
 * <p>普通业务模块应直接使用本类型，不应通过继承再次创建 {@code XxxPage<T>} 兼容包装。</p>
 *
 * @param <T> 业务数据类型
 * @author weifuwan
 */
public final class PageData<T> {

    private final List<T> records;
    private final long total;
    private final long pages;
    private final long pageNo;
    private final long pageSize;

    public PageData(List<T> records, long total, long pages, long pageNo, long pageSize) {
        this.records = records == null ? List.of() : List.copyOf(records);
        this.total = total;
        this.pages = pages;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public List<T> records() {
        return records;
    }

    public long total() {
        return total;
    }

    public long pages() {
        return pages;
    }

    public long pageNo() {
        return pageNo;
    }

    public long pageSize() {
        return pageSize;
    }

    /**
     * 根据记录、总条数、当前页和每页大小创建分页数据，并计算总页数。
     *
     * <p>适用于使用 limit/offset 或其他非 MyBatis 分页查询的 Repository Adapter。</p>
     *
     * @param records 当前页记录
     * @param total 数据总条数
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param <T> 业务数据类型
     * @return 分页数据
     */
    public static <T> PageData<T> of(List<T> records, long total, long pageNo, long pageSize) {
        long pages = pageSize <= 0L ? 0L : (total + pageSize - 1L) / pageSize;
        return new PageData<>(records, total, pages, pageNo, pageSize);
    }

    /**
     * 映射当前页记录，并保持分页元数据不变。
     *
     * <p>显式指定 {@code Stream.map} 的目标类型为外层 {@code R}，避免
     * {@code Function<? super T, ? extends R>} 被推断为独立 capture type。</p>
     *
     * @param mapper 记录转换函数
     * @param <R> 目标记录类型
     * @return 映射后的分页数据
     */
    public <R> PageData<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new PageData<>(records.stream().<R>map(mapper).toList(), total, pages, pageNo, pageSize);
    }

    /**
     * 创建空分页数据。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param <T> 业务数据类型
     * @return 空分页数据
     */
    public static <T> PageData<T> empty(long pageNo, long pageSize) {
        return of(List.of(), 0L, pageNo, pageSize);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PageData<?> that)) return false;
        return total == that.total
                && pages == that.pages
                && pageNo == that.pageNo
                && pageSize == that.pageSize
                && records.equals(that.records);
    }

    @Override
    public int hashCode() {
        return Objects.hash(records, total, pages, pageNo, pageSize);
    }

    @Override
    public String toString() {
        return "PageData{" + "records="
                + records + ", total="
                + total + ", pages="
                + pages + ", pageNo="
                + pageNo + ", pageSize="
                + pageSize + '}';
    }
}
