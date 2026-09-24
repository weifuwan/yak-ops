package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.RolePermissionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限关系 MyBatis 映射接口。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionPO> {
}
