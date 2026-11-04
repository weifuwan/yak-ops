package io.yak.ops.plugin.task.api.shell.bash;

import io.yak.ops.plugin.task.api.shell.BaseShellInterceptor;

import java.util.List;

public class BashShellInterceptor extends BaseShellInterceptor {

    public BashShellInterceptor(List<String> executeCommands, String workingDirectory) {
        super(executeCommands, workingDirectory);
    }

}
