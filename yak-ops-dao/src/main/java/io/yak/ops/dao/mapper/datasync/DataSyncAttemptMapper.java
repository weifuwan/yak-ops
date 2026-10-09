package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据同步 Attempt 历史表的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Mapper
public interface DataSyncAttemptMapper extends BaseMapper<DataSyncAttemptEntity> {}
