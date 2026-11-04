package io.yak.ops.extract.alert.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertTestSendRequest {

    private int pluginDefineId;

    private String pluginInstanceParams;
}
