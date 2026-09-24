package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.PermissionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 MyBatis 映射接口。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionPO> {
}
