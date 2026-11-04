package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.plugin.task.api.model.SwitchResultVo;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchParameters extends AbstractParameters {

    // due to history reasons, the field name is switchResult
    private SwitchResult switchResult;

    // The next branch which should be executed after the switch logic task executed.
    private Long nextBranch;

    @Override
    public boolean checkParameters() {
        if (switchResult == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(switchResult.getDependTaskList()) && switchResult.getNextNode() == null) {
            return false;
        }
        for (SwitchResultVo switchResultVo : switchResult.getDependTaskList()) {
            if (switchResultVo == null || switchResultVo.getNextNode() == null) {
                return false;
            }
        }
        return true;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SwitchResult {

        // switch condition
        private List<SwitchResultVo> dependTaskList;

        // default branch node code in switch task
        private Long nextNode;
    }

}
