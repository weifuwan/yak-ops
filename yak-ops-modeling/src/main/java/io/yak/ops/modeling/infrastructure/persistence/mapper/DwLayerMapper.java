package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.dwlayer.DwLayerPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数仓分层Mapper接口
 *
 * @author bruce
 */
@Mapper
public interface DwLayerMapper extends BaseMapper<DwLayerPO> {


}
