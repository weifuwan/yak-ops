package io.yak.ops.api.utils;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.plugin.task.api.TaskConstants;
import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.utils.GlobalParameterUtils;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SensitivePropertyUtilsTest {

    @Test
    void restoreStartParamsShouldKeepSensitiveGlobalPlaintext() {
        Property global = new Property("token", Direct.IN, DataType.VARCHAR, "plain-token");
        global.setSensitive(true);
        Property start = new Property("token", Direct.IN, DataType.VARCHAR, TaskConstants.SENSITIVE_DATA_MASK);

        List<Property> restored = SensitivePropertyUtils.restoreStartParams(
                Collections.singletonList(start),
                Collections.singletonList(global));

        Assertions.assertEquals(1, restored.size());
        Assertions.assertEquals("plain-token", restored.get(0).getValue());
        Assertions.assertTrue(restored.get(0).isSensitive());
    }

    @Test
    void createShouldRejectSensitivePlaceholderWithoutExistingValue() {
        Property submitted = new Property(
                "password", Direct.IN, DataType.VARCHAR, TaskConstants.SENSITIVE_DATA_MASK);
        submitted.setSensitive(true);

        Assertions.assertThrows(ServiceException.class,
                () -> SensitivePropertyUtils.requireNoPlaceholder(Collections.singletonList(submitted)));
    }

    @Test
    void maskWorkflowDefinitionShouldReturnCopyWithMaskedGlobalValue() {
        Property global = new Property("password", Direct.IN, DataType.VARCHAR, "secret");
        global.setSensitive(true);
        WorkflowDefinition source = new WorkflowDefinition();
        source.setExecutionType(WorkflowExecutionTypeEnum.PARALLEL);
        source.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(Collections.singletonList(global)));

        WorkflowDefinition masked = SensitivePropertyUtils.mask(source);
        List<Property> maskedParams = GlobalParameterUtils.deserializeGlobalParameter(masked.getGlobalParams());

        Assertions.assertNotSame(source, masked);
        Assertions.assertEquals("secret",
                GlobalParameterUtils.deserializeGlobalParameter(source.getGlobalParams()).get(0).getValue());
        Assertions.assertEquals(TaskConstants.SENSITIVE_DATA_MASK, maskedParams.get(0).getValue());
    }
}
