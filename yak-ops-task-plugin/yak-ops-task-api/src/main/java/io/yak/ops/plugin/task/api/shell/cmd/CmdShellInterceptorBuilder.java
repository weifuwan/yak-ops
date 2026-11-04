package io.yak.ops.plugin.task.api.shell.cmd;

import io.yak.ops.plugin.task.api.shell.BaseWindowsShellInterceptorBuilder;

import java.io.IOException;
import java.util.List;

public class CmdShellInterceptorBuilder
        extends
            BaseWindowsShellInterceptorBuilder<CmdShellInterceptorBuilder, CmdShellInterceptor> {

    @Override
    protected String shellHeader() {
        return "@echo off";
    }

    @Override
    protected String shellInterpreter() {
        return "cmd.exe";
    }

    @Override
    protected String shellExtension() {
        return ".bat";
    }

    @Override
    public CmdShellInterceptorBuilder newBuilder() {
        return new CmdShellInterceptorBuilder();
    }

    @Override
    public CmdShellInterceptor build() throws IOException {
        generateShellScript();
        List<String> bootstrapCommand = generateBootstrapCommand();
        return new CmdShellInterceptor(bootstrapCommand, shellDirectory);
    }
}
