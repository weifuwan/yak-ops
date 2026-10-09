package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据同步 Execution 产品事件表的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Mapper
public interface DataSyncExecutionEventMapper extends BaseMapper<DataSyncExecutionEventEntity> {}
