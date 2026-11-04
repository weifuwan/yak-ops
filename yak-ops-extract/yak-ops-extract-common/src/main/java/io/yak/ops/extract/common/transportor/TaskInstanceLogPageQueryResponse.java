package io.yak.ops.extract.common.transportor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceLogPageQueryResponse {

    private String logContent;

    private LogResponseStatus code = LogResponseStatus.SUCCESS;

    private String message;

}
