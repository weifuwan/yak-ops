package io.yak.ops.modeling.application;

import io.yak.ops.modeling.api.vo.ModelingBootstrapVO;
import io.yak.ops.modeling.api.vo.ModelingOverviewVO;

/** Application contract for the modeling module bootstrap. */
public interface ModelingModuleService {

    ModelingBootstrapVO getBootstrap(long projectCode);

    ModelingOverviewVO getOverview(long projectCode);
}
