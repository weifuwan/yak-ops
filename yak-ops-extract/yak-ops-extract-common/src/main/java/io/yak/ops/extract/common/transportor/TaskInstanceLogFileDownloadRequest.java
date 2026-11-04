package io.yak.ops.extract.common.transportor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceLogFileDownloadRequest {

    private long taskInstanceId;

    private String taskInstanceLogAbsolutePath;

}
