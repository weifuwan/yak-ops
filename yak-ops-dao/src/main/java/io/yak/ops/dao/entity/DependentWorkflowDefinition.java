package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.CycleEnum;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.model.DependentItem;
import io.yak.ops.plugin.task.api.model.DependentTaskModel;
import io.yak.ops.plugin.task.api.parameters.DependentParameters;

import java.util.List;

import lombok.Data;

@Data
public class DependentWorkflowDefinition {

    private long workflowDefinitionCode;

    private int workflowDefinitionVersion;

    private long taskDefinitionCode;

    private String taskParams;

    private String workerGroup;

    public CycleEnum getDependentCycle(long upstreamProcessDefinitionCode) {
        DependentParameters dependentParameters = this.getDependentParameters();
        List<DependentTaskModel> dependentTaskModelList = dependentParameters.getDependence().getDependTaskList();

        for (DependentTaskModel dependentTaskModel : dependentTaskModelList) {
            List<DependentItem> dependentItemList = dependentTaskModel.getDependItemList();
            for (DependentItem dependentItem : dependentItemList) {
                if (upstreamProcessDefinitionCode == dependentItem.getDefinitionCode()) {
                    return cycle2CycleEnum(dependentItem.getCycle());
                }
            }
        }

        return CycleEnum.DAY;
    }

    public CycleEnum cycle2CycleEnum(String cycle) {
        CycleEnum cycleEnum = null;

        switch (cycle) {
            case "day":
                cycleEnum = CycleEnum.DAY;
                break;
            case "hour":
                cycleEnum = CycleEnum.HOUR;
                break;
            case "week":
                cycleEnum = CycleEnum.WEEK;
                break;
            case "month":
                cycleEnum = CycleEnum.MONTH;
                break;
            default:
                break;
        }
        return cycleEnum;
    }

    public DependentParameters getDependentParameters() {
        return JSONUtils.parseObject(taskParams, DependentParameters.class);
    }

}
