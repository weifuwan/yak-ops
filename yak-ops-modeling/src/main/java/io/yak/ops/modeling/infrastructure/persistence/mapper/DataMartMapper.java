package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.datamart.DataMartPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集市
 *
 * @author bruce
 */
@Mapper
public interface DataMartMapper extends BaseMapper<DataMartPO> {

}
