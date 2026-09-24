package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.OplogExtra;

import java.util.List;

/**
 * 操作日志扩展数据访问接口。
 */
public interface OplogExtraDao {
    List<OplogExtra> selectListByType(Integer var1);

    void insertBatch(List<OplogExtra> var1);
}
