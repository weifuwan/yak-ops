package io.yak.ops.modeling.application;

import io.yak.ops.modeling.api.vo.ModelingBootstrapVO;
import io.yak.ops.modeling.api.vo.ModelingSectionVO;

import java.util.Arrays;

import org.springframework.stereotype.Service;

/**
 * Application entry point for the modeling module.
 *
 * <p>Workspace-scoped business capabilities are registered here as each migration phase is completed.
 */
@Service
public class ModelingModuleService {

    public ModelingBootstrapVO getBootstrap(long projectCode) {
        return new ModelingBootstrapVO(
                projectCode,
                "DATA_MODELING",
                "数据建模",
                Arrays.asList(
                        new ModelingSectionVO("PLANNING", "数仓规划"),
                        new ModelingSectionVO("MODELING", "模型设计"),
                        new ModelingSectionVO("STANDARDS", "数据标准"),
                        new ModelingSectionVO("INDICATORS", "数据指标")));
    }
}
