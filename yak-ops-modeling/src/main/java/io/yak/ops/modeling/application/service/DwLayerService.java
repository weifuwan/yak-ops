package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;

import java.util.List;

/** Service contract for warehouse layers. */
public interface DwLayerService {

    String create(DwLayerDTO dto);

    DwLayerVO updateById(DwLayerDTO dto);

    String delete(String id);

    DwLayerVO selectById(String id);

    DwLayerVO selectByDwType(String dwType);

    IPage<DwLayerVO> paging(DwLayerDTO dto);

    List<DwLayerVO> all(DwLayerDTO dto);

    /** Returns warehouse-layer options filtered by model type codes. */
    List<OptionVO> option(List<String> modelTypes);

    /** Returns warehouse-layer options for one model type. */
    List<OptionVO> optionByModalType(DwModelEnum modalType);
}
