package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供 Data Sync Table Execution 的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Mapper
public interface DataSyncTableExecutionMapper extends BaseMapper<DataSyncTableExecutionEntity> {}
