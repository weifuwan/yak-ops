package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据同步任务实例表的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Mapper
public interface DataSyncInstanceMapper extends BaseMapper<DataSyncInstanceEntity> {}
