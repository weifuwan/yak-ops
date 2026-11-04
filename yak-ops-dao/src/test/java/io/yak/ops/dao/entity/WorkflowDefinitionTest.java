package io.yak.ops.dao.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorkflowDefinitionTest {

    /**
     * task instance sub process
     */
    @Test
    public void getGlobalParamMapTest() {
        WorkflowDefinition taskInstance = new WorkflowDefinition();

        // sub process
        taskInstance.setGlobalParams(
                "[{\"prop\":\"selenium_global_parameters_1\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\"selenium_global_parameters_value_1\"}]");

        taskInstance.getGlobalParamMap();
        Assertions.assertEquals("{selenium_global_parameters_1=selenium_global_parameters_value_1}",
                taskInstance.getGlobalParamMap().toString());

    }
}
