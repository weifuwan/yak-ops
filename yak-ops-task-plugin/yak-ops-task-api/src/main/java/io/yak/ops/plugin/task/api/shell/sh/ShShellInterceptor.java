package io.yak.ops.plugin.task.api.shell.sh;

import io.yak.ops.plugin.task.api.shell.BaseShellInterceptor;

import java.util.List;

public class ShShellInterceptor extends BaseShellInterceptor {

    protected ShShellInterceptor(List<String> executeCommands, String shellDirectory) {
        super(executeCommands, shellDirectory);
    }
}
