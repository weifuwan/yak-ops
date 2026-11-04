package io.yak.ops.server.master.integration.fake;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@VisibleForTesting
public class LogicFakeTaskParameters extends AbstractParameters {

    private String shellScript;

    @Override
    public boolean checkParameters() {
        if (StringUtils.isEmpty(shellScript)) {
            throw new IllegalArgumentException("shellScript is null or empty");
        }
        return true;
    }

}
