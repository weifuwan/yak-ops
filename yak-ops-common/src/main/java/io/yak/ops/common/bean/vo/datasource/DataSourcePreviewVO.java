package io.yak.ops.common.bean.vo.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/** A bounded source preview whose textual values preserve database numeric precision. */
@Getter
public final class DataSourcePreviewVO {
    /** Ordered physical column metadata for the preview. */
    private final List<DataSourceCatalogColumnVO> columns;

    /** Ordered cells encoded as text, boolean or null, without numeric JSON coercion. */
    private final List<List<Object>> rows;

    /** Whether the row, cell or total preview size limit truncated the response. */
    private final boolean truncated;

    public DataSourcePreviewVO(List<DataSourceCatalogColumnVO> columns, List<List<Object>> rows, boolean truncated) {
        this.columns = List.copyOf(columns);
        this.rows = rows.stream()
                .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                .toList();
        this.truncated = truncated;
    }
}
