package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

/**
 * 业务分类服务接口
 *
 * @author bruce
 */
public interface BusinessTypeService {

    String create(BusinessTypeDTO businessType);

    BusinessTypeVO updateById(BusinessTypeDTO businessType);

    String delete(String id);

    BusinessTypeVO selectById(String id);

    IPage<BusinessTypeVO> paging(BusinessTypeDTO businessTypeDTO);

    List<TreeVO> tree(String keyword, String id);

    List<BusinessTypeVO> all();

    List<DataDomainVO> selectTypeData(String id);

    Map<String, List<String>> allRelatedDomainIds();

    Boolean updateTypeData(String id, List<String> dataDomainIds);

    Boolean deleteTypeData(String id, String dataDomainId);

    List<OptionVO> optionRelatedDomain(String id, boolean checked);

    Boolean onLoadAllDomains(String id);
}
