package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.MenuPO;
import org.apache.ibatis.annotations.Mapper;

/** 菜单 MyBatis 映射接口。 */
@Mapper
public interface MenuMapper extends BaseMapper<MenuPO> {
}
