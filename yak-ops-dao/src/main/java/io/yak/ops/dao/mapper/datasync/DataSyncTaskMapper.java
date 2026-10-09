package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据同步任务定义表的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Mapper
public interface DataSyncTaskMapper extends BaseMapper<DataSyncTaskEntity> {}
