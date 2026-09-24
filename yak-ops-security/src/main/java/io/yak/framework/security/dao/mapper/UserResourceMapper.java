package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.UserResourcePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户资源 MyBatis 映射接口。
 */
@Mapper
public interface UserResourceMapper extends BaseMapper<UserResourcePO> {
}
