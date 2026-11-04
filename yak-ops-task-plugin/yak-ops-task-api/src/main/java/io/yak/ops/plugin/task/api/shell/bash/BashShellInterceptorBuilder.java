package io.yak.ops.plugin.task.api.shell.bash;

import io.yak.ops.common.exception.FileOperateException;
import io.yak.ops.plugin.task.api.shell.BaseLinuxShellInterceptorBuilder;

import java.io.IOException;
import java.util.List;

public class BashShellInterceptorBuilder
        extends
            BaseLinuxShellInterceptorBuilder<BashShellInterceptorBuilder, BashShellInterceptor> {

    @Override
    public BashShellInterceptorBuilder newBuilder() {
        return new BashShellInterceptorBuilder();
    }

    @Override
    public BashShellInterceptor build() throws FileOperateException, IOException {
        generateShellScript();
        List<String> bootstrapCommand = generateBootstrapCommand();
        return new BashShellInterceptor(bootstrapCommand, shellDirectory);
    }

    @Override
    protected String shellInterpreter() {
        return "bash";
    }

    @Override
    protected String shellExtension() {
        return ".sh";
    }

    @Override
    protected String shellHeader() {
        return "#!/bin/bash";
    }

}
