package io.yak.ops.plugin.task.java;

import io.yak.ops.plugin.task.api.model.ResourceInfo;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import lombok.Data;

@Data
public class JavaParameters extends AbstractParameters {

    /**
     * run in jar file
     */
    private ResourceInfo mainJar;

    /**
     * Marks the current task running mode
     */
    private String runType;

    /**
     * main method args
     **/
    private String mainArgs;

    /**
     * java virtual machine args
     **/
    private String jvmArgs;

    /**
     * module path or class path flag
     **/
    private boolean isModulePath;

    /**
     * full main class name
     **/
    private String mainClass;

    private List<ResourceInfo> resourceList;

    /**
     * Check that the parameters are valid
     *
     * @return boolean
     */
    @Override
    public boolean checkParameters() {
        return StringUtils.isNotEmpty(runType) && mainJar != null;
    }

    /**
     * Gets a list of known resource files
     *
     * @return List<ResourceInfo>
     **/
    @Override
    public List<ResourceInfo> getResourceFilesList() {
        return this.resourceList;
    }
}
