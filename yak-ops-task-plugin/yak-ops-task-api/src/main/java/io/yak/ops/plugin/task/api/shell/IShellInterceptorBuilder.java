package io.yak.ops.plugin.task.api.shell;

import io.yak.ops.common.exception.FileOperateException;

import java.io.IOException;
import java.util.Map;

public interface IShellInterceptorBuilder<T extends IShellInterceptorBuilder<T, Y>, Y extends IShellInterceptor> {

    T newBuilder();

    T newBuilder(T builder);

    T shellDirectory(String directory);

    T shellName(String shellFilename);

    T runUser(String systemUser);

    T cpuQuota(Integer cpuQuota);

    T memoryQuota(Integer memoryQuota);

    T appendSystemEnv(String envFiles);

    T appendCustomEnvScript(String customEnvScript);

    T properties(Map<String, String> propertyMap);

    T sudoMode(boolean sudoEnable);

    T appendScript(String script);

    Y build() throws IOException, InterruptedException, FileOperateException;
}
