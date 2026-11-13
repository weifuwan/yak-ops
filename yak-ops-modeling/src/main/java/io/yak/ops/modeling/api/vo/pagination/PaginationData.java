package io.yak.ops.modeling.api.vo.pagination;

import java.util.List;

import lombok.Data;

@Data
public class PaginationData<T> {
    private List<T> bizData;
    private Pagination pagination;
}
