package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.UserPreference;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** Atomic database updates are defined per database vendor in the XML mapper. */
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {

    int upsertFavorite(@Param("userId") int userId,
                       @Param("scene") String scene,
                       @Param("itemKey") String itemKey,
                       @Param("favorite") boolean favorite,
                       @Param("sortOrder") int sortOrder);

    int recordUsage(@Param("userId") int userId,
                    @Param("scene") String scene,
                    @Param("itemKey") String itemKey);
}
