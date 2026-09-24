package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.OplogExtraPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志扩展 MyBatis 映射接口。
 */
@Mapper
public interface OplogExtraMapper extends BaseMapper<OplogExtraPO> {
}
