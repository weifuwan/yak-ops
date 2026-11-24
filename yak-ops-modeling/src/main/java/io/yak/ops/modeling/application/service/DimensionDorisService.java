package io.yak.ops.modeling.application.service;

import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisConfigDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionDorisPublishDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisConfigVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisFieldTypeOptionVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionDorisPublishVO;
import java.util.List;

/** Doris physical model configuration, DDL preview and publish contract. */
public interface DimensionDorisService {

    DimensionDorisConfigVO getConfig(String tableId);

    DimensionDorisConfigVO saveConfig(String tableId, DimensionDorisConfigDTO dto, String operator);

    List<DimensionDorisFieldTypeOptionVO> getFieldTypeOptions();

    DimensionDorisPublishVO preview(String tableId, DimensionDorisPublishDTO dto, DataSource datasource);

    DimensionDorisPublishVO publish(
            String tableId, DimensionDorisPublishDTO dto, DataSource datasource, String operator);

    List<DimensionDorisPublishVO> history(String tableId);
}
