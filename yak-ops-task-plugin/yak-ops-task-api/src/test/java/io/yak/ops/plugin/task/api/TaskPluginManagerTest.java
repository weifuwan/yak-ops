package io.yak.ops.plugin.task.api;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.plugin.task.api.task.ConditionsLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.DependentLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.SubWorkflowLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.SwitchLogicTaskChannelFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaskPluginManagerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            ConditionsLogicTaskChannelFactory.NAME,
            DependentLogicTaskChannelFactory.NAME,
            SubWorkflowLogicTaskChannelFactory.NAME,
            SwitchLogicTaskChannelFactory.NAME})
    void testGetTaskChannel_logicTaskChannel(String type) {
        assertThat(TaskPluginManager.getTaskChannel(type)).isNotNull();
    }

    @Test
    void shouldNotLoadLogicFakeTaskChannel() {
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> TaskPluginManager.getTaskChannel("LogicFakeTask"));

        assertThat(exception).hasMessageThat().contains("Cannot find TaskChannel");
    }

}
