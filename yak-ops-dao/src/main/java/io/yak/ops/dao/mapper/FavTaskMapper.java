package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.FavTask;

import org.apache.ibatis.annotations.Param;

import java.util.Set;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * fav mapper interface
 */
public interface FavTaskMapper extends BaseMapper<FavTask> {

    Set<String> getUserFavTaskTypes(@Param("userId") int userId);

    boolean deleteUserFavTask(@Param("userId") int userId, @Param("taskType") String taskType);
}
