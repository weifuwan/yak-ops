package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.dao.entity.enums.DwModelEnum;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 数仓分层服务接口
 *
 * @author bruce
 */
public interface DwLayerService {

    String create(DwLayerDTO DwLayerType);

    DwLayerVO updateById(DwLayerDTO DwLayerType);

    String delete(String id);

    DwLayerVO selectById(String id);

    DwLayerVO selectByDwType(String dwType);

    IPage<DwLayerVO> paging(DwLayerDTO DwLayerTypeDTO);

    List<DwLayerVO> all(DwLayerDTO dto);

    List<OptionVO> option(List<String> dwType);

    List<OptionVO> optionByModalType(DwModelEnum modalType);
}
