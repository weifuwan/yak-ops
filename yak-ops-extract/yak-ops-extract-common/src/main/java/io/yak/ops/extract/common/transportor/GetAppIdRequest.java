package io.yak.ops.extract.common.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetAppIdRequest {

    private int taskInstanceId;

    private String logPath;

}
