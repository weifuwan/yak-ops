package io.yak.ops.modeling.api.vo.pagination;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pagination {
    private long total;
    private long current;
    private long pageSize;
}
