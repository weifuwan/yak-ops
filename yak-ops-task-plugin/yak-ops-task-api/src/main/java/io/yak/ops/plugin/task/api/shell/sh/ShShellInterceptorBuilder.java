package io.yak.ops.plugin.task.api.shell.sh;

import io.yak.ops.common.exception.FileOperateException;
import io.yak.ops.plugin.task.api.shell.BaseLinuxShellInterceptorBuilder;

import java.io.IOException;
import java.util.List;

public class ShShellInterceptorBuilder
        extends
            BaseLinuxShellInterceptorBuilder<ShShellInterceptorBuilder, ShShellInterceptor> {

    @Override
    public ShShellInterceptorBuilder newBuilder() {
        return new ShShellInterceptorBuilder();
    }

    @Override
    public ShShellInterceptor build() throws IOException, FileOperateException {
        generateShellScript();
        List<String> bootstrapCommand = generateBootstrapCommand();
        return new ShShellInterceptor(bootstrapCommand, shellDirectory);
    }

    @Override
    protected String shellHeader() {
        return "#!/bin/sh";
    }

    @Override
    protected String shellInterpreter() {
        return "sh";
    }

    @Override
    protected String shellExtension() {
        return ".sh";
    }
}
