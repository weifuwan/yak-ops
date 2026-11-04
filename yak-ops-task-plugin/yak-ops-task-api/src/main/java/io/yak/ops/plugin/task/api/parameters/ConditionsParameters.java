package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.plugin.task.api.enums.DependentRelation;
import io.yak.ops.plugin.task.api.model.ConditionDependentTaskModel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionsParameters extends AbstractParameters {

    private ConditionDependency dependence;

    private ConditionResult conditionResult;

    @Override
    public boolean checkParameters() {
        if (dependence == null || CollectionUtils.isEmpty(dependence.getDependTaskList())) {
            return false;
        }
        if (conditionResult == null || CollectionUtils.isEmpty(conditionResult.getSuccessNode())
                || CollectionUtils.isEmpty(conditionResult.getFailedNode())) {
            return false;
        }
        return true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionDependency {

        private List<ConditionDependentTaskModel> dependTaskList;

        private DependentRelation relation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionResult {

        private boolean conditionSuccess;

        private List<Long> successNode;

        private List<Long> failedNode;
    }

}
