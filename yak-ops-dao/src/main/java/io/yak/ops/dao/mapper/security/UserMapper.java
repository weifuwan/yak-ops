package io.yak.ops.dao.mapper.security;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.security.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/** Yak Security user mapper. */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {}
