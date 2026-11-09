package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.plugin.task.api.TaskConstants;
import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PropertySensitiveUtilsTest {

    @Test
    void maskSensitiveValueShouldNotMutateSource() {
        Property source = new Property("password", Direct.IN, DataType.VARCHAR, "secret");
        source.setSensitive(true);

        Property masked = PropertySensitiveUtils.maskSensitiveValue(source);

        Assertions.assertEquals("secret", source.getValue());
        Assertions.assertEquals(TaskConstants.SENSITIVE_DATA_MASK, masked.getValue());
        Assertions.assertTrue(masked.isSensitive());
    }

    @Test
    void mergeSensitivePlaceholderShouldRestoreExistingValue() {
        Property existing = new Property("token", Direct.IN, DataType.VARCHAR, "plain-token");
        existing.setSensitive(true);
        Property submitted = new Property(
                "token", Direct.IN, DataType.VARCHAR, TaskConstants.SENSITIVE_DATA_MASK);
        submitted.setSensitive(true);

        List<Property> merged = PropertySensitiveUtils.mergeSensitiveValuePlaceholders(
                Collections.singletonList(submitted),
                Collections.singletonList(existing));

        Assertions.assertEquals("plain-token", merged.get(0).getValue());
        Assertions.assertEquals(TaskConstants.SENSITIVE_DATA_MASK, submitted.getValue());
    }
}
