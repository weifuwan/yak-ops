package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.ProjectPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 MyBatis 映射接口。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectPO> {
}
