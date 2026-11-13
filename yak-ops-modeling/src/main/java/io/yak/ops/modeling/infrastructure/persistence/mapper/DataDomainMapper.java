package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.datadomain.DataDomainPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据域Mapper接口
 *
 * @author bruce
 */
@Mapper
public interface DataDomainMapper extends BaseMapper<DataDomainPO> {


}
