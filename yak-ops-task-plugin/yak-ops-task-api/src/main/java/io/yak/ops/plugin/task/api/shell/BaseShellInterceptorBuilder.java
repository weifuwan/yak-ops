package io.yak.ops.plugin.task.api.shell;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseShellInterceptorBuilder<T extends BaseShellInterceptorBuilder<T, Y>, Y extends BaseShellInterceptor>
        implements
            IShellInterceptorBuilder<T, Y> {

    protected String shellDirectory;

    protected String shellName;

    protected String runUser;

    protected Integer cpuQuota;

    protected Integer memoryQuota;

    protected List<String> systemEnvs = new ArrayList<>();

    protected List<String> customEnvScripts = new ArrayList<>();

    protected Map<String, String> propertyMap = new HashMap<>();

    protected boolean sudoEnable;

    protected List<String> scripts = new ArrayList<>();

    protected BaseShellInterceptorBuilder() {
    }

    @Override
    public T newBuilder(T builder) {
        T newBuilder = newBuilder();
        newBuilder.shellDirectory = builder.shellDirectory;
        newBuilder.shellName = builder.shellName;
        newBuilder.runUser = builder.runUser;
        newBuilder.cpuQuota = builder.cpuQuota;
        newBuilder.memoryQuota = builder.memoryQuota;
        newBuilder.systemEnvs = builder.systemEnvs;
        newBuilder.customEnvScripts = builder.customEnvScripts;
        newBuilder.propertyMap = builder.propertyMap;
        newBuilder.sudoEnable = builder.sudoEnable;
        newBuilder.scripts = builder.scripts;
        return newBuilder;
    }

    @Override
    public T shellDirectory(String shellDirectory) {
        this.shellDirectory = shellDirectory;
        return (T) this;
    }

    @Override
    public T shellName(String shellFilename) {
        this.shellName = shellFilename;
        return (T) this;
    }

    @Override
    public T runUser(String systemUser) {
        this.runUser = systemUser;
        return (T) this;
    }

    @Override
    public T cpuQuota(Integer cpuQuota) {
        this.cpuQuota = cpuQuota;
        return (T) this;
    }

    @Override
    public T memoryQuota(Integer memoryQuota) {
        this.memoryQuota = memoryQuota;
        return (T) this;
    }

    @Override
    public T appendSystemEnv(String envFiles) {
        systemEnvs.add(envFiles);
        return (T) this;
    }

    @Override
    public T appendCustomEnvScript(String customEnvScript) {
        customEnvScripts.add(customEnvScript);
        return (T) this;
    }

    @Override
    public T properties(Map<String, String> propertyMap) {
        if (MapUtils.isNotEmpty(propertyMap)) {
            this.propertyMap.putAll(propertyMap);
        }
        return (T) this;
    }

    @Override
    public T sudoMode(boolean sudoEnable) {
        this.sudoEnable = sudoEnable;
        return (T) this;
    }

    @Override
    public T appendScript(String script) {
        scripts.add(script);
        return (T) this;
    }

}
