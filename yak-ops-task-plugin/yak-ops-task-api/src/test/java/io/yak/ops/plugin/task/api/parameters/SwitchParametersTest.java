package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.plugin.task.api.model.SwitchResultVo;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SwitchParametersTest {

    @Test
    public void testCheckParameters_allowsDefaultOnlyWithEmptyCaseList() {
        SwitchParameters.SwitchResult result = new SwitchParameters.SwitchResult();
        result.setDependTaskList(Collections.emptyList());
        result.setNextNode(203L);

        SwitchParameters parameters = new SwitchParameters();
        parameters.setSwitchResult(result);

        Assertions.assertTrue(parameters.checkParameters());
    }

    @Test
    public void testCheckParameters_allowsDefaultOnlyWithNullCaseList() {
        SwitchParameters.SwitchResult result = new SwitchParameters.SwitchResult();
        result.setDependTaskList(null);
        result.setNextNode(203L);

        SwitchParameters parameters = new SwitchParameters();
        parameters.setSwitchResult(result);

        Assertions.assertTrue(parameters.checkParameters());
    }

    @Test
    public void testCheckParameters_rejectsEmptyCasesWithoutDefault() {
        SwitchParameters.SwitchResult result = new SwitchParameters.SwitchResult();
        result.setDependTaskList(Collections.emptyList());
        result.setNextNode(null);

        SwitchParameters parameters = new SwitchParameters();
        parameters.setSwitchResult(result);

        Assertions.assertFalse(parameters.checkParameters());
    }

    @Test
    public void testCheckParameters_requiresTargetForEveryCase() {
        SwitchParameters.SwitchResult result = new SwitchParameters.SwitchResult();
        result.setDependTaskList(Arrays.asList(
                new SwitchResultVo("${amount} > 0", 201L),
                new SwitchResultVo("${amount} <= 0", null)));
        result.setNextNode(203L);

        SwitchParameters parameters = new SwitchParameters();
        parameters.setSwitchResult(result);

        Assertions.assertFalse(parameters.checkParameters());
    }

    @Test
    public void testCheckParameters_keepsDolphinSchedulerCaseWithoutDefaultSemantics() {
        SwitchParameters.SwitchResult result = new SwitchParameters.SwitchResult();
        result.setDependTaskList(Collections.singletonList(
                new SwitchResultVo("true == true", 201L)));
        result.setNextNode(null);

        SwitchParameters parameters = new SwitchParameters();
        parameters.setSwitchResult(result);

        Assertions.assertTrue(parameters.checkParameters());
    }
}
