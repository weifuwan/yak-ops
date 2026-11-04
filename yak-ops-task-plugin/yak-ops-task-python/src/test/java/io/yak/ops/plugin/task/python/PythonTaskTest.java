package io.yak.ops.plugin.task.python;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PythonTaskTest {

    @Test
    public void buildPythonExecuteCommand() throws Exception {
        PythonTask pythonTask = createPythonTask();
        Assertions.assertEquals("${PYTHON_LAUNCHER} test.py", pythonTask.buildPythonExecuteCommand("test.py"));
    }

    private PythonTask createPythonTask() {
        return new PythonTask(null);
    }
}
