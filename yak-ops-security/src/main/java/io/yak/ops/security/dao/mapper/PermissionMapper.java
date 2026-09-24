package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.PermissionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 MyBatis 映射接口。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionPO> {
}
