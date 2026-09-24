package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.po.ConfigPO;

import java.util.List;

/**
 * 配置数据访问接口。
 */
public interface ConfigDao {
    int insert(ConfigPO var1);

    int updateById(ConfigPO var1);

    int update(ConfigPO var1);

    int deleteById(Long var1);

    IPage<ConfigPO> selectPage(ConfigQueryDTO var1);

    List<ConfigPO> listByCondition(ConfigPO var1);

    List<ConfigPO> listConfigByGroup(String var1);

    List<String> listDistinctGroup();

    ConfigPO getbyId(Long var1);

    ConfigPO getByGroupAndName(String var1, String var2);
}
