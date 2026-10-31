package io.yak.ops.common.bean.vo.datasource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;

/** One stable page of matching tables, with an explicit continuation offset. */
@Getter
public final class DataSourceTablePageVO {
    /** Matching table metadata returned for this page. */
    private final List<DataSourceCatalogTableVO> items;

    /** Offset of the next matching entry, or null when the query is exhausted. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final Integer nextOffset;

    public DataSourceTablePageVO(List<DataSourceCatalogTableVO> items, Integer nextOffset) {
        this.items = List.copyOf(items);
        this.nextOffset = nextOffset;
    }
}
