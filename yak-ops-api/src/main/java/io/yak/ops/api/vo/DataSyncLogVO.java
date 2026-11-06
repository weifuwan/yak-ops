package io.yak.ops.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataSyncLogVO {
    private String content;
    private int nextOffset;
    private boolean hasMore;
}
