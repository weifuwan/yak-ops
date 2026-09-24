package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 MyBatis 映射接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
