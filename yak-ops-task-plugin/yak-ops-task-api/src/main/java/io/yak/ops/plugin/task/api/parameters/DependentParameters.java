package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.plugin.task.api.enums.DependentRelation;
import io.yak.ops.plugin.task.api.model.DependentTaskModel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
public class DependentParameters extends AbstractParameters {

    private Dependence dependence;

    @Override
    public boolean checkParameters() {
        if (dependence == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(dependence.getDependTaskList())) {
            return false;
        }
        if (dependence.getCheckInterval() != null && dependence.getCheckInterval() <= 0) {
            return false;
        }
        return true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dependence {

        private List<DependentTaskModel> dependTaskList;
        private DependentRelation relation;
        /**
         * Time unit is second
         */
        private Integer checkInterval;
        private DependentFailurePolicyEnum failurePolicy;
        /**
         * Time unit is minutes
         */
        private Integer failureWaitingTime;
    }

    /**
     * the dependent task failure policy.
     */
    public enum DependentFailurePolicyEnum {
        DEPENDENT_FAILURE_FAILURE,
        DEPENDENT_FAILURE_WAITING
    }

}
