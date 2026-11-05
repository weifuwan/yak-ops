package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.UserPreference;
import io.yak.ops.dao.mapper.UserPreferenceMapper;
import io.yak.ops.dao.repository.UserPreferenceDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

@Repository
public class UserPreferenceDaoImpl implements UserPreferenceDao {

    private final UserPreferenceMapper mapper;

    public UserPreferenceDaoImpl(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<UserPreference> listByUserAndScene(int userId, String scene) {
        return mapper.selectList(Wrappers.<UserPreference>lambdaQuery()
                .eq(UserPreference::getUserId, userId)
                .eq(UserPreference::getScene, scene)
                .orderByDesc(UserPreference::getFavorite)
                .orderByAsc(UserPreference::getSortOrder)
                .orderByDesc(UserPreference::getUseCount)
                .orderByDesc(UserPreference::getLastUsedTime));
    }

    @Override
    public Optional<UserPreference> find(int userId, String scene, String itemKey) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserPreference>lambdaQuery()
                .eq(UserPreference::getUserId, userId)
                .eq(UserPreference::getScene, scene)
                .eq(UserPreference::getItemKey, itemKey)));
    }

    @Override
    public int upsertFavorite(int userId, String scene, String itemKey, boolean favorite, int sortOrder) {
        return mapper.upsertFavorite(userId, scene, itemKey, favorite, sortOrder);
    }

    @Override
    public int recordUsage(int userId, String scene, String itemKey) {
        return mapper.recordUsage(userId, scene, itemKey);
    }
}
