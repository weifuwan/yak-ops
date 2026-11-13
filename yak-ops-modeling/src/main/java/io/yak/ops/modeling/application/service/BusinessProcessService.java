package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 数仓分层服务接口
 *
 * @author bruce
 */
public interface BusinessProcessService {

    String create(BusinessProcessDTO dto);

    BusinessProcessVO updateById(BusinessProcessDTO dto);

    String delete(String id);

    BusinessProcessVO selectById(String id);

    IPage<BusinessProcessVO> paging(BusinessProcessDTO dto);

    List<BusinessProcessVO> all(BusinessProcessDTO dto);

    void deleteByDomainId(String dataDomainId);

    List<CascaderTreeVO> treeList(String busTypeId);
}
