package io.yak.ops.service.preference;

import io.yak.ops.common.enums.UserPreferenceScene;
import io.yak.ops.common.model.UserPreferenceRecord;
import io.yak.ops.dao.entity.UserPreference;
import io.yak.ops.dao.repository.UserPreferenceDao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private static final int ITEM_KEY_MAX_LENGTH = 128;

    private final UserPreferenceDao dao;

    public UserPreferenceServiceImpl(UserPreferenceDao dao) {
        this.dao = dao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserPreferenceRecord> list(UserPreferenceScene scene, int userId) {
        checkScene(scene);
        checkUser(userId);
        return dao.listByUserAndScene(userId, scene.name()).stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceRecord updateFavorite(UserPreferenceScene scene, String itemKey, boolean favorite, int userId) {
        checkUser(userId);
        checkScene(scene);
        if (scene != UserPreferenceScene.PRODUCT_MENU) {
            throw new IllegalArgumentException("Only PRODUCT_MENU supports favorites");
        }
        String key = checkItemKey(itemKey);
        Optional<UserPreference> existing = dao.find(userId, scene.name(), key);
        int sortOrder = 0;
        if (favorite) {
            if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getFavorite())
                    && existing.get().getSortOrder() != null && existing.get().getSortOrder() > 0) {
                sortOrder = existing.get().getSortOrder();
            } else {
                sortOrder = dao.listByUserAndScene(userId, scene.name()).stream()
                        .filter(row -> Boolean.TRUE.equals(row.getFavorite()))
                        .map(UserPreference::getSortOrder)
                        .filter(order -> order != null && order > 0)
                        .max(Integer::compareTo)
                        .orElse(0) + 1;
            }
        }
        if (dao.upsertFavorite(userId, scene.name(), key, favorite, sortOrder) < 0) {
            throw new IllegalStateException("Failed to save user preference");
        }
        return requireSaved(userId, scene, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceRecord recordUsage(UserPreferenceScene scene, String itemKey, int userId) {
        checkUser(userId);
        checkScene(scene);
        if (scene != UserPreferenceScene.DATASOURCE_CREATE_TYPE) {
            throw new IllegalArgumentException("Only DATASOURCE_CREATE_TYPE supports usage recording");
        }
        String key = checkItemKey(itemKey);
        if (dao.recordUsage(userId, scene.name(), key) <= 0) {
            throw new IllegalStateException("Failed to record user preference usage");
        }
        return requireSaved(userId, scene, key);
    }

    private UserPreferenceRecord requireSaved(int userId, UserPreferenceScene scene, String key) {
        return dao.find(userId, scene.name(), key)
                .map(this::toRecord)
                .orElseThrow(() -> new IllegalStateException("Saved preference not found"));
    }

    private void checkScene(UserPreferenceScene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
    }

    private void checkUser(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
    }

    private String checkItemKey(String itemKey) {
        if (itemKey == null || itemKey.trim().isEmpty() || itemKey.length() > ITEM_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid preference item key");
        }
        return itemKey.trim();
    }

    private UserPreferenceRecord toRecord(UserPreference entity) {
        UserPreferenceRecord record = new UserPreferenceRecord();
        record.setScene(UserPreferenceScene.valueOf(entity.getScene()));
        record.setItemKey(entity.getItemKey());
        record.setFavorite(entity.getFavorite());
        record.setSortOrder(entity.getSortOrder());
        record.setUseCount(entity.getUseCount());
        record.setLastUsedTime(entity.getLastUsedTime());
        return record;
    }
}
