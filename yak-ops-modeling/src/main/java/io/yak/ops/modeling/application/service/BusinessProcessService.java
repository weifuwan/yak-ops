package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;

import java.util.List;

/** Service contract for business processes. */
public interface BusinessProcessService {

    String create(BusinessProcessDTO dto);

    String create(BusinessProcessDTO dto, String operator);

    BusinessProcessVO updateById(BusinessProcessDTO dto);

    BusinessProcessVO updateById(BusinessProcessDTO dto, String operator);

    String delete(String id);

    BusinessProcessVO selectById(String id);

    IPage<BusinessProcessVO> paging(BusinessProcessDTO dto);

    List<BusinessProcessVO> all(BusinessProcessDTO dto);

    void deleteByDomainId(String dataDomainId);

    List<CascaderTreeVO> treeList(String busTypeId);
}
