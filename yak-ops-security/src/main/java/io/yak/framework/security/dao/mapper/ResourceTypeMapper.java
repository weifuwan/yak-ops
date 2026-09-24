package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.ResourceTypePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源类型 MyBatis 映射接口。
 */
@Mapper
public interface ResourceTypeMapper extends BaseMapper<ResourceTypePO> {
}
