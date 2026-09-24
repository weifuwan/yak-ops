package io.yak.framework.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.ProjectPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 MyBatis 映射接口。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectPO> {
}
