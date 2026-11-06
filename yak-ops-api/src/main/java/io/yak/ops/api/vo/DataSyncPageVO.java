package io.yak.ops.api.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataSyncPageVO<T> {
    private List<T> bizData;
    private Pagination pagination;

    public static <T> DataSyncPageVO<T> of(List<T> data, int pageNo, int pageSize) {
        int total = data.size();
        int start = Math.min((pageNo - 1) * pageSize, total);
        int end = Math.min(start + pageSize, total);
        return new DataSyncPageVO<>(data.subList(start, end),
                new Pagination(pageNo, pageSize, total, (total + pageSize - 1) / pageSize));
    }

    @Data
    @AllArgsConstructor
    public static class Pagination {
        private int pageNo;
        private int pageSize;
        private int total;
        private int pages;
    }
}
