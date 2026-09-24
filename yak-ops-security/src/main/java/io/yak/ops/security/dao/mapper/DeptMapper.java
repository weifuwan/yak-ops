package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.DeptPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 MyBatis 映射接口。
 */
@Mapper
public interface DeptMapper extends BaseMapper<DeptPO> {
}
