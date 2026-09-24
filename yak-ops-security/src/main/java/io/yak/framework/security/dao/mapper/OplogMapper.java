package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.OplogPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 MyBatis 映射接口。
 */
@Mapper
public interface OplogMapper extends BaseMapper<OplogPO> {
}
