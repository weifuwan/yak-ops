package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.ConfigPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置 MyBatis 映射接口。
 */
@Mapper
public interface ConfigMapper extends BaseMapper<ConfigPO> {
}
