package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.UserPreference;

import java.util.List;
import java.util.Optional;

/** The only persistence boundary used by the user-preference service. */
public interface UserPreferenceDao {

    List<UserPreference> listByUserAndScene(int userId, String scene);

    Optional<UserPreference> find(int userId, String scene, String itemKey);

    int upsertFavorite(int userId, String scene, String itemKey, boolean favorite, int sortOrder);

    int recordUsage(int userId, String scene, String itemKey);
}
