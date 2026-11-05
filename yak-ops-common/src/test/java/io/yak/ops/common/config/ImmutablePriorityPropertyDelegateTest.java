package io.yak.ops.common.config;

import static io.yak.ops.common.constants.Constants.COMMON_PROPERTIES_PATH;
import static io.yak.ops.common.constants.Constants.REMOTE_LOGGING_YAML_PATH;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImmutablePriorityPropertyDelegateTest {

    private final ImmutablePriorityPropertyDelegate immutablePriorityPropertyDelegate =
            new ImmutablePriorityPropertyDelegate(
                    new ImmutablePropertyDelegate(COMMON_PROPERTIES_PATH),
                    new ImmutableYamlDelegate(REMOTE_LOGGING_YAML_PATH),
                    Collections.singletonMap("string.property", "valueFromEnv"));

    @Test
    void getOverrideFromEnv() {
        String value = immutablePriorityPropertyDelegate.get("string.property");
        Assertions.assertEquals("valueFromEnv", value);
    }

}
