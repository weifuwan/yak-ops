package io.yak.ops.dao.utils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EnvironmentUtilsTest {

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void testIsEnvironmentCodeEmpty_emptyEnvironmentCode(Long environmentCode) {
        assertThat(EnvironmentUtils.isEnvironmentCodeEmpty(environmentCode)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {123})
    void testIsEnvironmentCodeEmpty_nonEmptyEnvironmentCode(Long environmentCode) {
        assertThat(EnvironmentUtils.isEnvironmentCodeEmpty(environmentCode)).isFalse();
    }

    @Test
    void testGetDefaultEnvironmentCode() {
        assertThat(EnvironmentUtils.getDefaultEnvironmentCode()).isEqualTo(-1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void testGetEnvironmentCodeOrDefault_emptyEnvironmentCode(Long environmentCode) {
        assertThat(EnvironmentUtils.getEnvironmentCodeOrDefault(environmentCode)).isEqualTo(-1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {123})
    void testGetEnvironmentCodeOrDefault_nonEmptyEnvironmentCode(Long environmentCode) {
        assertThat(EnvironmentUtils.getEnvironmentCodeOrDefault(environmentCode)).isEqualTo(environmentCode);
    }

    @ParameterizedTest
    @CsvSource(value = {",123", "-1,123"})
    void testGetEnvironmentCodeOrDefault_withDefaultValue_emptyEnvironmentCode(Long environmentCode,
                                                                               Long defaultValue) {
        assertThat(EnvironmentUtils.getEnvironmentCodeOrDefault(environmentCode, defaultValue)).isEqualTo(defaultValue);
    }

    @ParameterizedTest
    @CsvSource(value = {"1,123"})
    void testGetEnvironmentCodeOrDefault_withDefaultValue_nonEmptyEnvironmentCode(Long environmentCode,
                                                                                  Long defaultValue) {
        assertThat(EnvironmentUtils.getEnvironmentCodeOrDefault(environmentCode, defaultValue))
                .isEqualTo(environmentCode);
    }
}
