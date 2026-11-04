package io.yak.ops.plugin.task.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class ShellParametersTest {

    @Test
    void dealOutParamTest() {
        ShellParameters shellParameters = new ShellParameters();
        List<Property> localParams = Lists.newArrayList(new Property("a", Direct.OUT, DataType.VARCHAR, "a"));
        shellParameters.setLocalParams(localParams);

        Map<String, String> taskOutputParams = new HashMap<>();
        taskOutputParams.put("b", "b");
        shellParameters.dealOutParam(taskOutputParams);
        List<Property> varPool = shellParameters.getVarPool();
        assertEquals(1, varPool.size());
        assertEquals("a", varPool.get(0).getValue());
    }

    @Test
    void dealOutParamTest_notTaskOutput() {
        ShellParameters shellParameters = new ShellParameters();
        List<Property> localParams = Lists.newArrayList(new Property("a", Direct.OUT, DataType.VARCHAR, "a"));
        shellParameters.setLocalParams(localParams);

        Map<String, String> taskOutputParams = new HashMap<>();
        shellParameters.dealOutParam(taskOutputParams);
        List<Property> varPool = shellParameters.getVarPool();
        assertEquals(1, varPool.size());
        assertEquals("a", varPool.get(0).getValue());
    }

    @Test
    void dealOutParamTest_taskOutputOverrideOut() {
        ShellParameters shellParameters = new ShellParameters();
        List<Property> localParams = Lists.newArrayList(new Property("a", Direct.OUT, DataType.VARCHAR, "a"));
        shellParameters.setLocalParams(localParams);

        Map<String, String> taskOutputParams = new HashMap<>();
        taskOutputParams.put("a", "b");
        shellParameters.dealOutParam(taskOutputParams);
        List<Property> varPool = shellParameters.getVarPool();
        assertEquals(1, varPool.size());
        assertEquals("b", varPool.get(0).getValue());
    }

}
