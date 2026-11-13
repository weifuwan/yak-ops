package io.yak.ops.modeling.application.service;


import com.baomidou.mybatisplus.extension.service.IService;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.infrastructure.persistence.entity.businessprocess.BusinessProcessPO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;

import java.util.List;

/**
 * 数仓分层服务接口
 *
 * @author bruce
 */
public interface BusinessProcessService extends IService<BusinessProcessPO> {

    String create(BusinessProcessDTO dto);

    BusinessProcessVO updateById(BusinessProcessDTO dto);

    String delete(String id);

    BusinessProcessVO selectById(String id);

    PaginationResult<BusinessProcessVO> paging(BusinessProcessDTO dto);

    List<BusinessProcessVO> all(BusinessProcessDTO dto);

    void deleteByDomainId(String dataDomainId);

    List<CascaderTreeVO> treeList(String busTypeId);
}
