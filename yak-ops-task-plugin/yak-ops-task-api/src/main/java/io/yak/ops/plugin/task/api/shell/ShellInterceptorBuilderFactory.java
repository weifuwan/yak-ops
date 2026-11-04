package io.yak.ops.plugin.task.api.shell;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.task.api.shell.bash.BashShellInterceptorBuilder;
import io.yak.ops.plugin.task.api.shell.cmd.CmdShellInterceptorBuilder;
import io.yak.ops.plugin.task.api.shell.sh.ShShellInterceptorBuilder;

public class ShellInterceptorBuilderFactory {

    private final static String INTERCEPTOR_TYPE = PropertyUtils.getString("shell.interceptor.type", "bash");

    @SuppressWarnings("unchecked")
    public static IShellInterceptorBuilder newBuilder() {
        if (INTERCEPTOR_TYPE.equalsIgnoreCase("bash")) {
            return new BashShellInterceptorBuilder();
        }
        if (INTERCEPTOR_TYPE.equalsIgnoreCase("sh")) {
            return new ShShellInterceptorBuilder();
        }
        if (INTERCEPTOR_TYPE.equalsIgnoreCase("cmd")) {
            return new CmdShellInterceptorBuilder();
        }
        throw new IllegalArgumentException("not support shell type: " + INTERCEPTOR_TYPE);
    }

}
