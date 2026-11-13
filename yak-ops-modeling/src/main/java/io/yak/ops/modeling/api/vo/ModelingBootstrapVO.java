package io.yak.ops.modeling.api.vo;

import java.util.List;

/**
 * Workspace-scoped bootstrap information for the data modeling product.
 */
public class ModelingBootstrapVO {

    private final long projectCode;
    private final String moduleCode;
    private final String moduleName;
    private final List<ModelingSectionVO> sections;

    public ModelingBootstrapVO(long projectCode, String moduleCode, String moduleName,
                               List<ModelingSectionVO> sections) {
        this.projectCode = projectCode;
        this.moduleCode = moduleCode;
        this.moduleName = moduleName;
        this.sections = sections;
    }

    public long getProjectCode() {
        return projectCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public String getModuleName() {
        return moduleName;
    }

    public List<ModelingSectionVO> getSections() {
        return sections;
    }
}
