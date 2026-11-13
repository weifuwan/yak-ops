package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.BusinessTypePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务分类Mapper接口
 *
 * @author bruce
 */
@Mapper
public interface BusinessTypeMapper extends BaseMapper<BusinessTypePO> {


}
