package io.yak.ops.plugin.task.api.utils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

class TaskTypeUtilsTest {

    @Test
    void isSwitchTask() {
        assertThat(TaskTypeUtils.isSwitchTask("SWITCH")).isTrue();
    }

}
