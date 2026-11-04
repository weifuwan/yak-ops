package io.yak.ops.dao.utils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkerGroupUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "default"})
    void testIsWorkerGroupEmpty_emptyWorkerGroup(String workerGroup) {
        assertThat(WorkerGroupUtils.isWorkerGroupEmpty(workerGroup)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "default1"})
    void testIsWorkerGroupEmpty_nonEmptyWorkerGroup(String workerGroup) {
        assertThat(WorkerGroupUtils.isWorkerGroupEmpty(workerGroup)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "default"})
    void testGetWorkerGroupOrDefault_emptyWorkerGroup(String workerGroup) {
        assertThat(WorkerGroupUtils.getWorkerGroupOrDefault(workerGroup))
                .isEqualTo(WorkerGroupUtils.getDefaultWorkerGroup());
    }

    @ParameterizedTest
    @ValueSource(strings = {"test"})
    void testGetWorkerGroupOrDefault_nonEmptyWorkerGroup(String workerGroup) {
        assertThat(WorkerGroupUtils.getWorkerGroupOrDefault(workerGroup)).isEqualTo(workerGroup);
    }

    @ParameterizedTest
    @CsvSource(value = {",test", "default,test"})
    void testGetWorkerGroupOrDefault_withDefaultValue_emptyWorkerGroup(String workerGroup, String defaultValue) {
        assertThat(WorkerGroupUtils.getWorkerGroupOrDefault(workerGroup, defaultValue)).isEqualTo(defaultValue);
    }

    @ParameterizedTest
    @CsvSource(value = {"test1,test"})
    void testGetWorkerGroupOrDefault_withDefaultValue_nonEmptyWorkerGroup(String workerGroup, String defaultValue) {
        assertThat(WorkerGroupUtils.getWorkerGroupOrDefault(workerGroup)).isEqualTo(workerGroup);
    }

    @Test
    void getDefaultWorkerGroup() {
        assertThat(WorkerGroupUtils.getDefaultWorkerGroup()).isEqualTo("default");
    }
}
