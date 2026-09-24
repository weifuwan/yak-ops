package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.RolePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 MyBatis 映射接口。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {
}
