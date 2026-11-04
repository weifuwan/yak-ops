package io.yak.ops.extract.common.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskInstanceLogPageQueryRequest {

    private Integer taskInstanceId;

    private String taskInstanceLogAbsolutePath;

    private int skipLineNum;
    private int limit;

}
