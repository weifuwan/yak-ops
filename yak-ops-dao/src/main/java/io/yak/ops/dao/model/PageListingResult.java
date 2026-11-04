package io.yak.ops.dao.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PageListingResult<T> {

    private List<T> records;

    private long totalCount;

    private int currentPage;

    private int pageSize;
}
