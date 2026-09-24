package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.UserRolePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关系 MyBatis 映射接口。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRolePO> {
}
