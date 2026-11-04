package io.yak.ops.alert.api;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The alarm information includes the parameters of the alert channel and the alarm data
 */
@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class AlertInfo {

    private Map<String, String> alertParams;

    private AlertData alertData;

    private int alertPluginInstanceId;

}
