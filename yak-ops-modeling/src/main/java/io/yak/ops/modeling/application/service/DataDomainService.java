package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 数据域服务接口
 *
 * @author bruce
 */
public interface DataDomainService {

    String create(DataDomainDTO DataDomainType);

    DataDomainVO updateById(DataDomainDTO DataDomainType);

    String delete(String id);

    DataDomainVO selectById(String id);

    IPage<DataDomainVO> paging(DataDomainDTO DataDomainTypeDTO);

    List<DataDomainVO> all();
}
