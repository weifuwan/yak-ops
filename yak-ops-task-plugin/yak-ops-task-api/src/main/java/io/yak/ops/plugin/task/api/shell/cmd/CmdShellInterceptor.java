package io.yak.ops.plugin.task.api.shell.cmd;

import io.yak.ops.plugin.task.api.shell.BaseShellInterceptor;

import java.util.List;

public class CmdShellInterceptor extends BaseShellInterceptor {

    protected CmdShellInterceptor(List<String> executeCommands, String workingDirectory) {
        super(executeCommands, workingDirectory);
    }
}
