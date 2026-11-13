package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableFieldVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionVersionHistoryVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;

import java.util.List;

/** Application contract for project-scoped dimension modeling. */
public interface DimensionModelingService {

    DimensionTableVO create(DimensionTableDTO dto);

    DimensionTableVO updateById(DimensionTableDTO dto);

    DimensionTableVO submit(String id);

    String delete(String id);

    Boolean deleteBatch(List<String> ids);

    DimensionTableVO selectById(String id);

    IPage<DimensionTableVO> page(DimensionTableDTO dto);

    List<DimensionTreeNodeVO> tree(DimensionTableDTO dto);

    List<DimensionTableVO> all(DimensionTableDTO dto);

    List<DimensionTableVO> getNoneList(String keyword);

    List<OptionVO> getSelectOption();

    List<OptionVO> getDimOption();

    List<DimensionVersionHistoryVO> getVersionHistory(String id);

    DimensionTableVO rollbackToVersion(String id, Integer targetVersion);

    List<DimensionTableFieldVO> listFields(String tableId);

    DimensionTableFieldVO createField(String tableId, DimensionTableFieldDTO dto);

    DimensionTableFieldVO updateField(String tableId, String fieldId, DimensionTableFieldDTO dto);

    List<DimensionTableFieldVO> replaceFields(String tableId, List<DimensionTableFieldDTO> fields);

    String deleteField(String tableId, String fieldId);
}
