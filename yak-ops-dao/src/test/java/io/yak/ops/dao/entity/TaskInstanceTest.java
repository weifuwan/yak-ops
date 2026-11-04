package io.yak.ops.dao.entity;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.model.DependentItem;
import io.yak.ops.plugin.task.api.model.DependentTaskModel;
import io.yak.ops.plugin.task.api.parameters.DependentParameters;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TaskInstanceTest {

    /**
     * test for TaskInstance.getDependence
     */
    @Test
    public void testTaskInstanceGetDependence() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskParams(JSONUtils.toJsonString(getDependentParameters()));
    }

    /**
     *
     * @return
     */
    private DependentParameters getDependentParameters() {
        DependentParameters dependentParameters = new DependentParameters();
        List<DependentTaskModel> dependTaskList = new ArrayList<>();
        List<DependentItem> dependentItems = new ArrayList<>();
        DependentItem dependentItem = new DependentItem();
        dependentItem.setDepTaskCode(111L);
        dependentItem.setDefinitionCode(222L);
        dependentItem.setCycle("today");
        dependentItems.add(dependentItem);
        return dependentParameters;
    }
}
