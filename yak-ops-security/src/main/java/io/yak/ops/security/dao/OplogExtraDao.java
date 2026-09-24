package io.yak.ops.security.dao;

import io.yak.ops.security.common.entity.OplogExtra;

import java.util.List;

/**
 * 操作日志扩展数据访问接口。
 */
public interface OplogExtraDao {
    List<OplogExtra> selectListByType(Integer var1);

    void insertBatch(List<OplogExtra> var1);
}
