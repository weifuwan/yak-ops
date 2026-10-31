package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps immutable successful task publication receipts. */
@Mapper
public interface DefinitionPublicationMapper extends BaseMapper<DefinitionPublicationEntity> {}
