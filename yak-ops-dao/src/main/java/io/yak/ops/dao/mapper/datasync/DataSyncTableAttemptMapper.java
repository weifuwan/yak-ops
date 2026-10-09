package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保存单表级执行尝试的 MyBatis Mapper。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Mapper
public interface DataSyncTableAttemptMapper extends BaseMapper<DataSyncTableAttemptEntity> {}
