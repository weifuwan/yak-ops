package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.infrastructure.persistence.entity.businessprocess.BusinessProcessPO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务过程Mapper接口
 *
 * @author bruce
 */
@Mapper
public interface BusinessProcessMapper extends BaseMapper<BusinessProcessPO> {


    IPage<BusinessProcessVO> paging(Page<BusinessProcessVO> page, BusinessProcessDTO dto);
}
