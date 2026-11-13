package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;

import java.util.List;

/** Service contract for data domains. */
public interface DataDomainService {

    String create(DataDomainDTO dto);

    DataDomainVO updateById(DataDomainDTO dto);

    String delete(String id);

    DataDomainVO selectById(String id);

    IPage<DataDomainVO> paging(DataDomainDTO dto);

    List<DataDomainVO> all();
}
