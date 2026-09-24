package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.RoleMenuPO;
import org.apache.ibatis.annotations.Mapper;

/** 角色菜单关系 MyBatis 映射接口。 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuPO> {
}
