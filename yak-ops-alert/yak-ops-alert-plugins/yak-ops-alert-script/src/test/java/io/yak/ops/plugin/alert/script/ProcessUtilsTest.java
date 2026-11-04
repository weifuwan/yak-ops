package io.yak.ops.plugin.alert.script;

import org.junit.jupiter.api.Test;

public class ProcessUtilsTest {

    private static final String rootPath = System.getProperty("user.dir");

    private static final String shellFilPath =
            rootPath + "/dolphinscheduler-alert-plugins/dolphinscheduler-alert-script/src/test/script/shell/test.sh";

    private String[] cmd = {"/bin/sh", "-c", shellFilPath + " -t 1"};

    @Test
    public void testExecuteScript() {
        int code = ProcessUtils.executeScript(cmd);
        assert code != -1;
    }
}
