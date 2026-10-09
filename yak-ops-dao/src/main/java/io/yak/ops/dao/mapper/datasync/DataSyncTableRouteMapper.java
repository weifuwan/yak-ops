package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据同步表级 Route 的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Mapper
public interface DataSyncTableRouteMapper extends BaseMapper<DataSyncTableRouteEntity> {}
